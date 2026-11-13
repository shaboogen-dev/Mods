package com.bitwig.mods.audioanalysis;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The setup's steps, without Bitwig: download and check the pinned Python, unpack it, make the
 * virtual environment, install the locked packages, fetch the separation model, remove it all.
 * {@link Setup} runs these in a background job after Bitwig's dialog; a test harness can run them
 * outside Bitwig. Everything happens inside the runtime's home.
 */
public final class Installer {
    /** What a step needs from the job running it. */
    public interface Steps {
        void phase(String text);

        boolean cancelled();

        /** Throws when cancelled. */
        void check();

        ProcessRunner.Result run(List<String> command, Map<String, String> env, Path workDir, String logName,
                                 Duration timeout) throws IOException, InterruptedException;

        /** Like the other {@code run}, calling {@code onPoll} about five times a second while it runs. */
        default ProcessRunner.Result run(List<String> command, Map<String, String> env, Path workDir, String logName,
                                         Duration timeout, Runnable onPoll) throws IOException, InterruptedException {
            return run(command, env, workDir, logName, timeout);
        }

        void log(String line);

        /** The whole task is {@code fraction} (0 to 1) done. */
        default void progress(double fraction) { }
    }

    // ---------- the steps' weights: their measured seconds on Windows x64 (8 Nov) ----------

    static final String PYTHON_DOWNLOAD = "python-download", PYTHON_UNPACK = "python-unpack", VENV = "venv",
        PACKAGES = "packages", CHECK = "check", TORCH = "torch", MODEL = "model";

    /** Core setup: Python download 1.3-1.6 s, unpack 1.4 s, venv 6 s, packages 54 s, check 4 s. */
    public static Progress corePlan(Progress plan, boolean downloadPython, boolean makeVenv) {
        if (downloadPython) plan.step(PYTHON_DOWNLOAD, 1.5).step(PYTHON_UNPACK, 1.4);
        if (makeVenv) plan.step(VENV, 6);
        return plan.step(PACKAGES, 54).step(CHECK, 4);
    }

    /**
     * Stem setup: packages 70 s, model 9 s. On Linux PyTorch comes first from its own index; it is
     * most of the download, so of the 70 s it gets 45.
     */
    public static Progress stemsPlan(Progress plan, boolean linux) {
        if (linux) plan.step(TORCH, 45).step(PACKAGES, 25);
        else plan.step(PACKAGES, 70);
        return plan.step(MODEL, 9);
    }

    private Installer() { }

    // ---------- Python ----------

    /**
     * The Python the environment is made from: the downloaded build, downloading and unpacking it
     * first when it isn't there.
     */
    public static Path basePython(RuntimePaths paths, Steps steps)
            throws IOException, InterruptedException {
        return basePython(paths, steps,
            new Progress(steps::progress).step(PYTHON_DOWNLOAD, 1.5).step(PYTHON_UNPACK, 1.4));
    }

    /** Whether {@link #basePython} will download Python. */
    static boolean downloadsPython(RuntimePaths paths) {
        return !Files.isRegularFile(paths.basePython());
    }

    private static Path basePython(RuntimePaths paths, Steps steps, Progress plan)
            throws IOException, InterruptedException {
        if (Files.isRegularFile(paths.basePython())) return paths.basePython();
        PythonBuild build = PythonBuild.of(paths.os(), Platform.currentArch());
        if (build == null) throw new IllegalStateException("there is no Python build for this computer ("
            + paths.os() + ", " + System.getProperty("os.arch") + ")");
        installPython(paths, build, steps, plan);
        return paths.basePython();
    }

    /** Downloads {@code build}, checks its SHA-256, unpacks it into {@code <home>/python}. */
    public static void installPython(RuntimePaths paths, PythonBuild build, Steps steps) throws IOException {
        installPython(paths, build, steps, new Progress(steps::progress).step(PYTHON_DOWNLOAD, 1.5).step(PYTHON_UNPACK, 1.4));
    }

    private static void installPython(RuntimePaths paths, PythonBuild build, Steps steps, Progress plan) throws IOException {
        Path home = paths.home();
        Files.createDirectories(home);
        plan.start(PYTHON_DOWNLOAD);
        steps.phase(Status.downloadingPython(build.sizeText()));
        Path archive = paths.pythonArchive();
        long t0 = System.nanoTime();
        long size = build.size();
        Download.fetch(build.url(), archive, build.sha256(), size, steps::cancelled,
            size > 0 ? bytes -> plan.within((double) bytes / size) : null);
        steps.log("downloaded and checked " + build.url() + " (SHA-256 " + build.sha256() + ") in " + seconds(t0));
        steps.check();
        plan.start(PYTHON_UNPACK);
        steps.phase(Status.UNPACKING_PYTHON);
        Path partial = home.resolve("python.partial");
        deleteInside(home, partial);
        try {
            t0 = System.nanoTime();
            TarGz.extract(archive, partial, steps::cancelled);
            Path unpacked = partial.resolve("python");
            if (!Files.isRegularFile(Platform.standalonePython(unpacked, paths.os())))
                throw new IOException("the Python archive has no interpreter where expected");
            deleteInside(home, paths.pythonDir());
            Files.move(unpacked, paths.pythonDir(), StandardCopyOption.ATOMIC_MOVE);
            steps.log("unpacked into " + paths.pythonDir() + " in " + seconds(t0));
        } finally {
            deleteInside(home, partial);
            Files.deleteIfExists(archive);
        }
        if (paths.os() != Platform.Os.WINDOWS && !Files.isExecutable(paths.basePython()))
            throw new IOException("the unpacked Python isn't executable: " + paths.basePython());
        plan.within(1);
    }

    /** The "3.12" a Python prints, or "" when it doesn't run. */
    static String versionOf(Path python, Steps steps, Map<String, String> env) throws IOException, InterruptedException {
        if (!Files.isRegularFile(python)) return "";
        ProcessRunner.Result r = steps.run(Commands.version(List.of(python.toString())), env, null, "python-version",
            Duration.ofSeconds(30));
        return r.ok() ? lastLine(r.output()) : "";
    }

    // ---------- core ----------

    /** The core part: Python, the environment, the analysis packages, a check that they load. */
    public static void core(RuntimePaths paths, Steps steps, Map<String, String> env)
            throws IOException, InterruptedException {
        Files.createDirectories(paths.home());
        if (paths.os() == Platform.Os.WINDOWS) {
            List<String> missing = Platform.missingWindowsRuntime(Platform.system32(env));
            if (!missing.isEmpty()) throw new IllegalStateException(windowsRuntimeProblem(missing));
        }
        Progress plan = corePlan(new Progress(steps::progress), downloadsPython(paths), !Files.isRegularFile(paths.python()));
        Path base = basePython(paths, steps, plan);
        if (Files.isRegularFile(paths.python()) && !Platform.acceptable(versionOf(paths.python(), steps, env))) {
            steps.log("the environment doesn't run (or isn't " + Platform.PYTHON_VERSION + "): making it again");
            deleteInside(paths.home(), paths.venv());
            plan.step(VENV, 6);
        }
        if (!Files.isRegularFile(paths.python())) {
            plan.start(VENV);
            steps.phase(Status.CREATING_ENVIRONMENT);
            ProcessRunner.Result r = steps.run(Commands.createVenv(base, paths.venv()), env, paths.home(), "setup-venv",
                Duration.ofMinutes(5));
            if (!r.ok() || !Files.isRegularFile(paths.python())) throw new IllegalStateException(problem(paths, "creating the Python environment", r));
        }
        plan.start(PACKAGES);
        ProcessRunner.Result pip = pip(paths, steps, plan, Commands.pipInstall(paths.python(), paths.coreRequirements()),
            packageCount(paths.coreRequirements(), paths.os()), null, env, "setup-core", Duration.ofMinutes(60));
        if (!pip.ok()) throw new IllegalStateException(problem(paths, "installing the packages", pip));
        plan.start(CHECK);
        steps.phase(Status.CHECKING);
        ProcessRunner.Result verify = steps.run(Commands.verifyCore(paths.python()), env, paths.home(), "setup-check",
            Duration.ofMinutes(5));
        if (!verify.ok() || !verify.output().contains("AUDIO_ANALYSIS_CORE_OK"))
            throw new IllegalStateException(problem(paths, "checking the installation", verify));
        plan.complete();
    }

    // ---------- stems ----------

    /** Stem separation: PyTorch and Demucs into the environment, then the htdemucs model. */
    public static void stems(RuntimePaths paths, Steps steps, Map<String, String> env) throws IOException, InterruptedException {
        boolean linux = paths.os() == Platform.Os.LINUX;
        Progress plan = stemsPlan(new Progress(steps::progress), linux);
        if (linux) {
            plan.start(TORCH);
            ProcessRunner.Result torch = pip(paths, steps, plan, Commands.pipInstallTorchCpu(paths.python()), 1,
                Status.INSTALLING_PYTORCH, env, "setup-torch", Duration.ofMinutes(90));
            if (!torch.ok()) throw new IllegalStateException(problem(paths, "installing PyTorch", torch));
        }
        plan.start(PACKAGES);
        ProcessRunner.Result pip = pip(paths, steps, plan, Commands.pipInstall(paths.python(), paths.stemsRequirements()),
            packageCount(paths.stemsRequirements(), paths.os()), null, env, "setup-stems", Duration.ofMinutes(90));
        if (!pip.ok()) throw new IllegalStateException(problem(paths, "installing Demucs", pip));
        plan.start(MODEL);
        steps.phase(Status.downloadingModel(MODEL_MB));
        Files.createDirectories(paths.models());
        ModelWatch watch = new ModelWatch(paths, plan);
        ProcessRunner.Result model = steps.run(Commands.fetchModel(paths.python()), Commands.online(env), paths.home(),
            "setup-model", Duration.ofMinutes(30), watch::poll);
        if (!model.ok() || !RuntimeState.hasModel(paths.models()))
            throw new IllegalStateException(problem(paths, "downloading the model", model));
        plan.complete();
    }

    /** The htdemucs model's download, measured (Hugging Face, adefossez/HTDemucs). */
    public static final int MODEL_MB = 84;

    /**
     * Runs one pip install, its progress counted from pip's lines into the current step of
     * {@code plan}, with the status line "Installing packages (k of n)" (or {@code fixedStatus}).
     */
    private static ProcessRunner.Result pip(RuntimePaths paths, Steps steps, Progress plan, List<String> command, int total,
                                            String fixedStatus, Map<String, String> env, String logName, Duration timeout)
            throws IOException, InterruptedException {
        PipProgress pip = new PipProgress(total);
        LogFollower follower = new LogFollower(paths.logs().resolve(logName + ".log"));
        String[] shown = { fixedStatus != null ? fixedStatus : Status.packages(pip) };
        steps.phase(shown[0]);
        Runnable poll = () -> {
            for (String line : follower.poll()) pip.line(line);
            plan.within(pip.fraction());
            String s = fixedStatus != null ? fixedStatus : Status.packages(pip);
            if (!s.equals(shown[0])) {
                shown[0] = s;
                steps.phase(s);
            }
        };
        ProcessRunner.Result r = steps.run(command, env, paths.home(), logName, timeout, poll);
        if (r.ok()) plan.within(1);
        return r;
    }

    /** The packages a lock file installs on this computer (for "k of n"); at least 1. */
    static int packageCount(Path requirements, Platform.Os os) {
        try {
            return Math.max(1, PipProgress.packageCount(Files.readAllLines(requirements), os, Platform.currentArch()));
        } catch (IOException e) {
            return 1;
        }
    }

    /**
     * The model download's progress: the percentage of the download's own progress bar in the log
     * (huggingface_hub and torch.hub both print one), counting only a bar for a file of 20 MB or more
     * (not the small config files); until one appears, the bytes in the models folder against the
     * measured size, at most 90 %.
     */
    static final class ModelWatch {
        private final RuntimePaths paths;
        private final Progress plan;
        private final LogFollower follower;
        private boolean barSeen;
        private int polls;

        ModelWatch(RuntimePaths paths, Progress plan) {
            this.paths = paths;
            this.plan = plan;
            this.follower = new LogFollower(paths.logs().resolve("setup-model.log"));
        }

        void poll() {
            double best = -1;
            for (String line : follower.poll()) {
                double f = Progress.tqdmFraction(line, 20e6);
                if (f >= 0) best = f;
            }
            if (best >= 0) {
                barSeen = true;
                plan.within(best);
            } else if (!barSeen && ++polls % 5 == 0) { // about once a second
                plan.within(Math.min(0.9, folderBytes(paths.models()) / (MODEL_MB * 1e6)));
            }
        }

        private static long folderBytes(Path dir) {
            try (var files = Files.walk(dir)) {
                return files.filter(f -> Files.isRegularFile(f, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    .mapToLong(f -> {
                        try {
                            return Files.size(f);
                        } catch (IOException e) {
                            return 0;
                        }
                    }).sum();
            } catch (IOException | RuntimeException e) {
                return 0;
            }
        }
    }

    // ---------- removal ----------

    /** What Remove deletes, all inside the home. */
    public static List<Path> removable(RuntimePaths paths) {
        return List.of(paths.venv(), paths.pythonDir(), paths.home().resolve("python.partial"), paths.models(),
            paths.cache(), paths.logs());
    }

    /** Deletes {@code dir} and everything in it, only if it is inside {@code home}. Links are deleted, not followed. */
    public static void deleteInside(Path home, Path dir) throws IOException {
        Path h = home.toAbsolutePath().normalize();
        Path d = dir.toAbsolutePath().normalize();
        if (!d.startsWith(h) || d.equals(h)) throw new IOException("refusing to delete outside the runtime folder: " + d);
        if (!Files.exists(d, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isDirectory(d, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            Files.delete(d);
            return;
        }
        Files.walkFileTree(d, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path folder, IOException e) throws IOException {
                if (e != null) throw e;
                Files.delete(folder);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // ---------- messages ----------

    /** Microsoft's C++ runtime is missing (Windows): what to install. */
    public static String windowsRuntimeProblem(List<String> missing) {
        return "Audio Analysis needs Microsoft's Visual C++ Redistributable (x64; " + String.join(", ", missing)
            + " is missing). Install it from " + Platform.WINDOWS_RUNTIME_URL + ", then choose Set Up again";
    }

    static String lastLine(String output) {
        String[] lines = output == null ? new String[0] : output.strip().split("\\R");
        return lines.length == 0 ? "" : lines[lines.length - 1].strip();
    }

    static String problem(RuntimePaths paths, String step, ProcessRunner.Result r) {
        if (r.timedOut()) return step + " took too long";
        String last = lastLine(r.output());
        return step + " failed" + (last.isEmpty() ? "" : " (" + (last.length() > 160 ? last.substring(0, 160) + "…" : last) + ")")
            + "; details in " + paths.logs();
    }

    private static String seconds(long t0) {
        return String.format(java.util.Locale.ROOT, "%.1f s", (System.nanoTime() - t0) / 1e9);
    }
}
