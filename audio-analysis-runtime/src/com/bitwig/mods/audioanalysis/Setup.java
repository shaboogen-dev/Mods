package com.bitwig.mods.audioanalysis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Setting the runtime up and removing it, from Bitwig. Each starts only from the user's own action,
 * and each download happens only after Bitwig's dialog has said what will be downloaded, from where
 * and into which folder, and the user has pressed OK. The steps themselves are in {@link Installer}.
 */
public final class Setup {
    private Setup() { }

    // ---------- core: Audio to MIDI, Detect Key ----------

    /** Set Up Audio Analysis…: asks, then downloads Python and the analysis packages. */
    public static void core(Object window) {
        if (AudioAnalysis.coreReady()) {
            Ui.toast("Audio Analysis is already set up");
            return;
        }
        RuntimePaths paths = AudioAnalysis.paths();
        if (paths.os() == Platform.Os.WINDOWS) {
            List<String> missing = Platform.missingWindowsRuntime(Platform.system32(System.getenv()));
            if (!missing.isEmpty()) {
                Ui.warning("Audio Analysis needs the Visual C++ Redistributable", Installer.windowsRuntimeProblem(missing));
                return;
            }
        }
        PythonBuild build = PythonBuild.of(paths.os(), Platform.currentArch());
        boolean needPython = !Files.isRegularFile(paths.basePython());
        if (needPython && build == null) {
            Ui.warning("Audio Analysis has no Python build for this computer", "This computer is " + System.getProperty("os.arch")
                + "; Audio Analysis runs on Windows x64, macOS (Apple silicon and Intel) and Linux x64 and ARM64.");
            return;
        }
        ConfirmDialog.show(window, "Set Up Audio Analysis", coreText(paths, Platform.currentArch(), needPython ? build : null),
            Setup::runCore);
    }

    /** The dialog's lines. {@code build} is the Python to download, or null when none is. */
    static List<String> coreText(RuntimePaths paths, Platform.Arch arch, PythonBuild build) {
        List<String> lines = new ArrayList<>(List.of(
            "Audio to MIDI and Detect Key run in a Python environment of their own.",
            "",
            "OK downloads:"));
        if (build != null) {
            lines.add("- from GitHub (github.com): Python " + PythonBuild.VERSION + " (python-build-standalone), " + build.sizeText() + ";");
        }
        lines.add("- from the Python Package Index (pypi.org): Basic Pitch 0.4.0 with its ONNX runtime, numpy,");
        lines.add("  librosa, scikit-learn and ffmpeg (imageio-ffmpeg), about " + packageMegabytes(paths.os(), arch, false) + " MB.");
        lines.add("");
        lines.add("Everything goes into " + paths.home() + " (about 750 MB on disk).");
        lines.add("");
        lines.add("Nothing is installed anywhere else. It takes a few minutes; Bitwig stays usable.");
        lines.add("To remove it: Audio Analysis > Remove Audio Analysis Setup…");
        return List.copyOf(lines);
    }

    private static void runCore() {
        Jobs.start("Set Up Audio Analysis", control -> {
            RuntimePaths paths = AudioAnalysis.paths();
            Installer.core(paths, control, AudioAnalysis.engineEnvironment());
            var state = RuntimeState.read(paths.state());
            state.setProperty(RuntimeState.CORE, "ok");
            state.setProperty(RuntimeState.PYTHON, "python-build-standalone " + PythonBuild.VERSION + "+" + PythonBuild.RELEASE);
            state.setProperty(RuntimeState.PYTHON_VERSION, Platform.PYTHON_VERSION);
            state.setProperty("core.date", LocalDateTime.now().toString());
            RuntimeState.write(paths.state(), state);
            Ui.info("Audio Analysis is set up", "Right-click an audio clip for Audio to MIDI and Detect Key.");
        });
    }

    // ---------- stems: Separate Stems ----------

    /** Set Up Stem Separation…: asks, then installs Demucs and fetches its model. */
    public static void stems(Object window) {
        if (!AudioAnalysis.requireCore("Stem separation")) return;
        if (AudioAnalysis.stemsReady()) {
            Ui.toast("Stem separation is already set up");
            return;
        }
        RuntimePaths paths = AudioAnalysis.paths();
        ConfirmDialog.show(window, "Set Up Stem Separation", stemsText(paths, Platform.currentArch()), Setup::runStems);
    }

    static List<String> stemsText(RuntimePaths paths, Platform.Arch arch) {
        int mb = packageMegabytes(paths.os(), arch, true);
        List<String> lines = new ArrayList<>(List.of(
            "Separate Stems runs Demucs (MIT licence) on PyTorch, in the Audio Analysis environment.",
            "",
            "OK downloads:"));
        String torch = paths.os() == Platform.Os.MACOS && arch == Platform.Arch.X86_64 ? "2.2.2" : "2.11.0";
        if (paths.os() == Platform.Os.LINUX) {
            lines.add("- from PyTorch (download.pytorch.org): PyTorch " + torch + ", CPU only;");
            lines.add("- from the Python Package Index (pypi.org): Demucs 4.1.0 and what it needs;");
            lines.add("  about " + mb + " MB together;");
        } else {
            lines.add("- from the Python Package Index (pypi.org): PyTorch " + torch + ", Demucs 4.1.0 and what they need,");
            lines.add("  about " + mb + " MB;");
        }
        lines.add("- from Hugging Face (huggingface.co): the htdemucs separation model, about " + MODEL_MB + " MB.");
        lines.add("");
        lines.add("Everything goes into " + paths.home() + " (about 700 MB more on disk).");
        lines.add("");
        lines.add("Nothing is installed anywhere else, and nothing is downloaded when you separate stems.");
        lines.add("To remove it: Audio Analysis > Remove Audio Analysis Setup…");
        return List.copyOf(lines);
    }

    /** The htdemucs model's download, measured. */
    static final int MODEL_MB = Installer.MODEL_MB;

    /**
     * The packages' download in MB, measured from the locked wheels (8 Nov): the core part, or
     * (stems) PyTorch, Demucs and theirs.
     */
    static int packageMegabytes(Platform.Os os, Platform.Arch arch, boolean stems) {
        return switch (os) {
            case WINDOWS -> stems ? 147 : 176;
            case MACOS -> arch == Platform.Arch.X86_64 ? (stems ? 188 : 172) : (stems ? 111 : 154);
            case LINUX -> arch == Platform.Arch.ARM64 ? (stems ? 180 : 212) : (stems ? 226 : 229);
        };
    }

    private static void runStems() {
        Jobs.start("Set Up Stem Separation", control -> {
            RuntimePaths paths = AudioAnalysis.paths();
            Installer.stems(paths, control, AudioAnalysis.engineEnvironment());
            RuntimeState.set(paths.state(), RuntimeState.STEMS, "ok");
            Ui.info("Stem separation is set up", "Right-click an audio clip > Separate Stems.");
        });
    }

    // ---------- removal ----------

    /** Remove Audio Analysis Setup…: asks, then deletes Python, the environment, the model and the cache. */
    public static void remove(Object window) {
        RuntimePaths paths = AudioAnalysis.paths();
        if (Jobs.busy()) {
            Ui.toast("Wait for the running Audio Analysis job, or cancel it, first");
            return;
        }
        ConfirmDialog.show(window, "Remove Audio Analysis Setup", List.of(
            "OK permanently deletes the downloaded Python, the Audio Analysis environment,",
            "the separation model and every cached result (stems, analyses) in",
            String.valueOf(paths.home()),
            "",
            "Stems added to a project play from that cache until the project is saved with its files",
            "collected (File > Collect and Save); a project that still uses them loses that audio.",
            "",
            "Set it up again at any time from Audio Analysis."), Setup::runRemove);
    }

    private static void runRemove() {
        Jobs.start("Remove Audio Analysis Setup", control -> {
            RuntimePaths paths = AudioAnalysis.paths();
            control.phase(Status.REMOVING);
            RuntimeState.write(paths.state(), new java.util.Properties());
            List<Path> dirs = Installer.removable(paths);
            for (int i = 0; i < dirs.size(); i++) {
                control.check();
                Installer.deleteInside(paths.home(), dirs.get(i));
                control.progress((i + 1.0) / dirs.size());
            }
            Files.deleteIfExists(paths.pythonArchive());
            Ui.info("Audio Analysis setup removed", "");
        });
    }
}
