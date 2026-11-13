import com.bitwig.mods.audioanalysis.Commands;
import com.bitwig.mods.audioanalysis.Download;
import com.bitwig.mods.audioanalysis.Installer;
import com.bitwig.mods.audioanalysis.LogFollower;
import com.bitwig.mods.audioanalysis.PipProgress;
import com.bitwig.mods.audioanalysis.Platform;
import com.bitwig.mods.audioanalysis.Platform.Arch;
import com.bitwig.mods.audioanalysis.Platform.Os;
import com.bitwig.mods.audioanalysis.ProcessRunner;
import com.bitwig.mods.audioanalysis.Progress;
import com.bitwig.mods.audioanalysis.PythonBuild;
import com.bitwig.mods.audioanalysis.RuntimePaths;
import com.bitwig.mods.audioanalysis.RuntimeState;
import com.bitwig.mods.audioanalysis.Status;
import com.bitwig.mods.audioanalysis.TarGz;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * Offline checks of Helper: Audio Analysis's pure parts: platform detection and paths per
 * platform, the pinned Python builds, unpacking (executable bits, links, refusals), the download's
 * checks that need no network, every command line, the pinned requirements, the engine environment,
 * setup state reading, the process runner (exit code, output, time-out, cancel) on a real child
 * process, removal staying inside the home, the setup's weighted progress, reading pip's and the
 * downloads' progress, and the status lines.
 * Run from the mod folder: java -cp <helper.jar>;<this folder's classes> RuntimeLogicTest
 */
public final class RuntimeLogicTest {
    static int checks;

    public static void main(String[] args) throws Exception {
        platforms();
        builds();
        unpacking();
        download();
        commands();
        requirements();
        environment();
        state();
        processes();
        deletion();
        progress();
        pip();
        System.out.println("RuntimeLogicTest OK (" + checks + " checks)");
    }

    static void platforms() {
        eq(Platform.os("Windows 11"), Os.WINDOWS);
        eq(Platform.os("Mac OS X"), Os.MACOS);
        eq(Platform.os("Linux"), Os.LINUX);
        eq(Platform.os("FreeBSD"), Os.LINUX);
        eq(Platform.os(null), Os.LINUX);
        eq(Platform.arch("amd64"), Arch.X86_64);
        eq(Platform.arch("x86_64"), Arch.X86_64);
        eq(Platform.arch("aarch64"), Arch.ARM64);
        eq(Platform.arch("arm64"), Arch.ARM64);
        eq(Platform.arch("x86"), Arch.OTHER);
        eq(Platform.arch(null), Arch.OTHER);
        eq(Platform.standalonePython(Path.of("C:\\r\\python"), Os.WINDOWS), Path.of("C:\\r\\python", "python.exe"));
        eq(Platform.standalonePython(Path.of("/r/python"), Os.MACOS), Path.of("/r/python", "bin", "python3"));
        eq(Platform.standalonePython(Path.of("/r/python"), Os.LINUX), Path.of("/r/python", "bin", "python3"));
        eq(Platform.venvPython(Path.of("C:\\r\\venv"), Os.WINDOWS), Path.of("C:\\r\\venv", "Scripts", "python.exe"));
        eq(Platform.venvPython(Path.of("/r/venv"), Os.MACOS), Path.of("/r/venv", "bin", "python3"));
        eq(Platform.venvPython(Path.of("/r/venv"), Os.LINUX), Path.of("/r/venv", "bin", "python3"));
        eq(Platform.pathSeparator(Os.WINDOWS), ";");
        eq(Platform.pathSeparator(Os.LINUX), ":");
        yes(Platform.acceptable("3.12"));
        yes(Platform.acceptable("3.12\n"));
        no(Platform.acceptable("3.11"));
        no(Platform.acceptable("3.13"));
        no(Platform.acceptable("3.15"));
        no(Platform.acceptable(""));
        no(Platform.acceptable(null));
        eq(Platform.pathKey(Map.of("Path", "x")), "Path");
        eq(Platform.pathKey(Map.of("PATH", "x")), "PATH");
        eq(Platform.pathKey(Map.of()), "PATH");
        eq(Platform.system32(Map.of("SystemRoot", "D:\\Win")), Path.of("D:\\Win", "System32"));
        eq(Platform.system32(Map.of()), Path.of("C:\\Windows", "System32"));
        try {
            Path sys = Files.createTempDirectory("aa-sys32");
            eq(Platform.missingWindowsRuntime(sys), List.of("msvcp140.dll", "msvcp140_1.dll"));
            Files.writeString(sys.resolve("msvcp140.dll"), "x");
            eq(Platform.missingWindowsRuntime(sys), List.of("msvcp140_1.dll"));
            Files.writeString(sys.resolve("msvcp140_1.dll"), "x");
            eq(Platform.missingWindowsRuntime(sys), List.of());
            yes(Installer.windowsRuntimeProblem(List.of("msvcp140.dll")).contains(Platform.WINDOWS_RUNTIME_URL));
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    static void builds() {
        eq(PythonBuild.ALL.size(), 5);
        Set<String> targets = new HashSet<>();
        for (PythonBuild b : PythonBuild.ALL) {
            yes(b.url().startsWith("https://github.com/astral-sh/python-build-standalone/releases/download/" + PythonBuild.RELEASE + "/"));
            yes(b.url().endsWith("-" + b.target() + "-install_only.tar.gz"));
            yes(b.url().contains("cpython-" + PythonBuild.VERSION + "%2B" + PythonBuild.RELEASE));
            yes(b.sha256().matches("[0-9a-f]{64}"));
            yes(b.size() > 10_000_000L && b.size() < 100_000_000L);
            yes(targets.add(b.target()));
        }
        yes(PythonBuild.VERSION.startsWith(Platform.PYTHON_VERSION + "."));
        eq(PythonBuild.of(Os.WINDOWS, Arch.X86_64).target(), "x86_64-pc-windows-msvc");
        eq(PythonBuild.of(Os.MACOS, Arch.ARM64).target(), "aarch64-apple-darwin");
        eq(PythonBuild.of(Os.MACOS, Arch.X86_64).target(), "x86_64-apple-darwin");
        eq(PythonBuild.of(Os.LINUX, Arch.X86_64).target(), "x86_64-unknown-linux-gnu");
        eq(PythonBuild.of(Os.LINUX, Arch.ARM64).target(), "aarch64-unknown-linux-gnu");
        eq(PythonBuild.of(Os.WINDOWS, Arch.ARM64), null);
        eq(PythonBuild.of(Os.LINUX, Arch.OTHER), null);
        eq(PythonBuild.of(Os.WINDOWS, Arch.X86_64).sizeText(), "47 MB");
    }

    // ---------- unpacking ----------

    static void unpacking() throws Exception {
        Path dir = Files.createTempDirectory("aa-tar");
        Path good = dir.resolve("good.tar.gz");
        String longName = "python/lib/" + "a".repeat(120) + "/deep.py";
        try (OutputStream o = new GZIPOutputStream(Files.newOutputStream(good))) {
            tarEntry(o, "python/", '5', 0755, new byte[0], "");
            tarEntry(o, "python/bin/python3.12", '0', 0755, "#!binary".getBytes(StandardCharsets.UTF_8), "");
            tarEntry(o, "python/bin/python3", '2', 0777, new byte[0], "python3.12");
            tarEntry(o, "python/lib/os.py", '0', 0644, "import sys\n".getBytes(StandardCharsets.UTF_8), "");
            tarEntry(o, "././@LongLink", 'L', 0644, (longName + "\0").getBytes(StandardCharsets.UTF_8), "");
            tarEntry(o, longName.substring(0, 99), '0', 0644, "x = 1\n".getBytes(StandardCharsets.UTF_8), "");
            byte[] pax = paxRecord("path", "python/share/pax-named.txt");
            tarEntry(o, "PaxHeader/x", 'x', 0644, pax, "");
            tarEntry(o, "python/share/short", '0', 0644, "pax".getBytes(StandardCharsets.UTF_8), "");
            tarEntry(o, "python/lib/os-copy.py", '1', 0644, new byte[0], "python/lib/os.py");
            o.write(new byte[1024]);
        }
        Path out = dir.resolve("out");
        TarGz.extract(good, out, () -> false);
        eq(Files.readString(out.resolve("python/bin/python3.12")), "#!binary");
        eq(Files.readString(out.resolve("python/lib/os.py")), "import sys\n");
        eq(Files.readString(out.resolve(longName)), "x = 1\n");
        eq(Files.readString(out.resolve("python/share/pax-named.txt")), "pax");
        no(Files.exists(out.resolve("python/share/short")));
        eq(Files.readString(out.resolve("python/lib/os-copy.py")), "import sys\n");
        eq(Files.readString(out.resolve("python/bin/python3")), "#!binary"); // a link, or a copy where links can't be made
        if (Files.getFileAttributeView(out, PosixFileAttributeView.class) != null) {
            yes(Files.isSymbolicLink(out.resolve("python/bin/python3")));
            Set<PosixFilePermission> p = Files.getPosixFilePermissions(out.resolve("python/bin/python3.12"));
            yes(p.contains(PosixFilePermission.OWNER_EXECUTE) && p.contains(PosixFilePermission.OTHERS_EXECUTE));
            no(Files.getPosixFilePermissions(out.resolve("python/lib/os.py")).contains(PosixFilePermission.OWNER_EXECUTE));
        }
        // refusals: entries and links that would land outside the folder
        refused(dir, "escape", o -> tarEntry(o, "python/../../evil.txt", '0', 0644, "x".getBytes(), ""));
        refused(dir, "absolute", o -> tarEntry(o, "/etc/evil", '0', 0644, "x".getBytes(), ""));
        refused(dir, "drive", o -> tarEntry(o, "C:/evil", '0', 0644, "x".getBytes(), ""));
        refused(dir, "link-out", o -> tarEntry(o, "python/bin/evil", '2', 0777, new byte[0], "../../../outside"));
        refused(dir, "link-abs", o -> tarEntry(o, "python/bin/evil", '2', 0777, new byte[0], "/usr/bin/python3"));
        no(Files.exists(dir.resolve("evil.txt")));
        eq(TarGz.class.getSimpleName(), "TarGz");
    }

    interface Writer { void write(OutputStream o) throws Exception; }

    static void refused(Path dir, String name, Writer w) throws Exception {
        Path archive = dir.resolve(name + ".tar.gz");
        try (OutputStream o = new GZIPOutputStream(Files.newOutputStream(archive))) {
            w.write(o);
            o.write(new byte[1024]);
        }
        boolean refused = false;
        try { TarGz.extract(archive, dir.resolve(name), () -> false); } catch (java.io.IOException e) { refused = true; }
        yes(refused);
    }

    static byte[] paxRecord(String key, String value) {
        String body = " " + key + "=" + value + "\n";
        int len = body.length() + 1;
        while (String.valueOf(len).length() + body.length() != len) len++;
        return (len + body).getBytes(StandardCharsets.UTF_8);
    }

    static void tarEntry(OutputStream o, String name, char type, int mode, byte[] data, String link) throws Exception {
        byte[] h = new byte[512];
        put(h, 0, 100, name);
        put(h, 100, 8, String.format("%07o", mode));
        put(h, 108, 8, "0000000");
        put(h, 116, 8, "0000000");
        put(h, 124, 12, String.format("%011o", data.length));
        put(h, 136, 12, "00000000000");
        h[156] = (byte) type;
        put(h, 157, 100, link);
        put(h, 257, 6, "ustar");
        put(h, 263, 2, "00");
        for (int i = 148; i < 156; i++) h[i] = ' ';
        int sum = 0;
        for (byte b : h) sum += b & 0xff;
        put(h, 148, 8, String.format("%06o", sum) + "\0 ");
        o.write(h);
        o.write(data);
        int pad = (512 - data.length % 512) % 512;
        o.write(new byte[pad]);
    }

    static void put(byte[] h, int off, int len, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(b, 0, h, off, Math.min(len, b.length));
    }

    // ---------- download (no network) ----------

    static void download() throws Exception {
        Path dir = Files.createTempDirectory("aa-dl");
        Path f = dir.resolve("f");
        Files.writeString(f, "abc");
        eq(Download.sha256(f), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        boolean refused = false;
        try { Download.fetch("http://example.com/x", dir.resolve("x"), "00", 0, () -> false, null); } catch (java.io.IOException e) { refused = true; }
        yes(refused); // https only
        no(Files.exists(dir.resolve("x")));
    }

    // ---------- commands ----------

    static void commands() {
        RuntimePaths p = new RuntimePaths(Path.of("C:\\Users\\a\\AppData\\Local\\BML\\mods\\audio-analysis-runtime\\runtime"),
            Path.of("D:\\BML\\mods\\audio-analysis-runtime\\engine"), Os.WINDOWS);
        Path py = p.python();
        eq(py, p.home().resolve("venv").resolve("Scripts").resolve("python.exe"));
        eq(p.basePython(), p.home().resolve("python").resolve("python.exe"));
        eq(p.pythonArchive().getParent(), p.home());
        eq(p.huggingFace(), p.home().resolve("models").resolve("huggingface"));
        RuntimePaths mac = new RuntimePaths(Path.of("/Users/a/Library/Application Support/BML/mods/x/runtime"), Path.of("/e"), Os.MACOS);
        eq(mac.basePython(), mac.home().resolve("python").resolve("bin").resolve("python3"));
        eq(Commands.version(List.of("C:\\py\\python.exe")), List.of("C:\\py\\python.exe", "-c", Commands.VERSION_SCRIPT));
        eq(Commands.createVenv(p.basePython(), p.venv()), List.of(p.basePython().toString(), "-m", "venv", p.venv().toString()));
        eq(Commands.pipInstall(py, p.coreRequirements()), List.of(py.toString(), "-m", "pip", "install",
            "--disable-pip-version-check", "--no-input", "--no-deps", "--only-binary=:all:", "-r",
            p.engine().resolve("requirements-core.txt").toString()));
        List<String> torch = Commands.pipInstallTorchCpu(py);
        yes(torch.contains("--index-url"));
        yes(torch.contains(Commands.TORCH_CPU_INDEX));
        yes(torch.contains("--no-deps") && torch.contains("--only-binary=:all:"));
        eq(torch.get(torch.size() - 1), "torch==2.11.0+cpu");
        eq(Commands.engine(py, List.of("a.wav", "--quiet")), List.of(py.toString(), "-m", "audio_dna_engine", "a.wav", "--quiet"));
        eq(Commands.script(py, Path.of("k.py"), List.of("a.wav")), List.of(py.toString(), "k.py", "a.wav"));
        eq(Commands.verifyCore(py).get(1), "-c");
        yes(Commands.verifyCore(py).get(2).contains("nmp.onnx"));
        yes(Commands.fetchModel(py).get(2).contains("htdemucs"));
        // removal covers the downloaded Python and stays inside the home
        List<Path> removable = Installer.removable(p);
        yes(removable.contains(p.pythonDir()) && removable.contains(p.venv()) && removable.contains(p.models()));
        for (Path r : removable) yes(r.startsWith(p.home()) && !r.equals(p.home()));
    }

    static void requirements() throws Exception {
        String core, stems;
        try {
            core = Files.readString(Path.of("package/engine/requirements-core.txt"));
            stems = Files.readString(Path.of("package/engine/requirements-stems.txt"));
        } catch (java.io.IOException e) {
            throw new AssertionError("run from the mod folder: " + e);
        }
        // the versions the dialogs name, and the ones Installer and Commands rely on
        yes(core.contains("\nbasic-pitch==0.4.0\n") && core.contains("\nnumpy==1.26.4\n") && core.contains("\nonnxruntime==1.19.2\n"));
        yes(core.contains("\nimageio-ffmpeg==0.8.1\n"));
        no(core.contains("tensorflow"));
        yes(stems.contains("\ndemucs==4.1.0\n") && stems.contains("\nnumpy==1.26.4\n"));
        yes(stems.contains("\ntorch==2.11.0; sys_platform != \"darwin\" or platform_machine != \"x86_64\"\n"));
        yes(stems.contains("\ntorch==2.2.2; sys_platform == \"darwin\" and platform_machine == \"x86_64\"\n"));
        yes(Commands.TORCH_LINUX.startsWith("torch==2.11.0+"));
        no(stems.contains("torchaudio") && !stems.contains("# "));
        no(stems.contains("\nsphn"));
        // every line is an exact pin (a lock: pip runs with --no-deps)
        for (String file : List.of(core, stems))
            for (String line : file.split("\\R")) if (!line.isBlank() && !line.startsWith("#")) yes(line.matches("[A-Za-z0-9_.-]+==[0-9][^;\\s]*(; .+)?"));
    }

    static void environment() {
        RuntimePaths w = new RuntimePaths(Path.of("C:\\h"), Path.of("C:\\e"), Os.WINDOWS);
        Map<String, String> in = new LinkedHashMap<>();
        in.put("Path", "C:\\Windows");
        in.put("PYTHONHOME", "C:\\bad");
        in.put("PythonPath", "C:\\old");
        Map<String, String> env = Commands.engineEnvironment(in, w);
        no(env.containsKey("PYTHONHOME"));
        eq(env.get("PythonPath"), "C:\\e;C:\\old");
        eq(env.get("Path"), "C:\\Windows"); // PATH as inherited: the engine never looks there for ffmpeg
        no(env.containsKey("PATH"));
        no(env.containsKey("AUDIO_DNA_MEDIA_BIN"));
        eq(env.get("TORCH_HOME"), Path.of("C:\\h", "models").toString());
        eq(env.get("HF_HOME"), Path.of("C:\\h", "models", "huggingface").toString());
        eq(env.get("HF_HUB_OFFLINE"), "1");
        eq(env.get("HF_HUB_DISABLE_TELEMETRY"), "1");
        eq(env.get("AUDIO_DNA_STEMS_CACHE"), Path.of("C:\\h", "cache", "stems").toString());
        eq(env.get("PYTHONNOUSERSITE"), "1");
        eq(env.get("PYTHONDONTWRITEBYTECODE"), "1");
        eq(env.get("PYTHONUNBUFFERED"), "1");
        eq(env.get("PIP_NO_CACHE_DIR"), "1");
        eq(env.get("PIP_CONFIG_FILE"), "nul");
        eq(in.get("PYTHONHOME"), "C:\\bad"); // the input is not changed
        Map<String, String> online = Commands.online(env);
        no(online.containsKey("HF_HUB_OFFLINE"));
        eq(online.get("HF_HOME"), env.get("HF_HOME"));
        eq(env.get("HF_HUB_OFFLINE"), "1");

        RuntimePaths l = new RuntimePaths(Path.of("/h"), Path.of("/e"), Os.LINUX);
        Map<String, String> env2 = Commands.engineEnvironment(Map.of("PATH", "/usr/bin"), l);
        eq(env2.get("PYTHONPATH"), Path.of("/e").toString());
        eq(env2.get("PATH"), "/usr/bin");
        no(env2.containsKey("AUDIO_DNA_MEDIA_BIN"));
        eq(env2.get("PIP_CONFIG_FILE"), "/dev/null");
        no(Commands.engineEnvironment(Map.of("PIP_INDEX_URL", "https://mirror.example"), l).containsKey("PIP_INDEX_URL"));
    }

    static void state() throws Exception {
        Path dir = Files.createTempDirectory("aa-state");
        RuntimePaths p = new RuntimePaths(dir, dir.resolve("engine"), Platform.current());
        no(RuntimeState.coreReady(p));
        no(RuntimeState.stemsReady(p));
        Files.writeString(p.state(), "core=ok\nstems=ok\n");
        no(RuntimeState.coreReady(p)); // no interpreter yet
        Files.createDirectories(p.python().getParent());
        Files.writeString(p.python(), "x");
        yes(RuntimeState.coreReady(p));
        no(RuntimeState.stemsReady(p)); // no model yet
        Path snapshot = p.huggingFace().resolve("hub").resolve("models--adefossez--HTDemucs").resolve("snapshots").resolve("abc");
        Files.createDirectories(snapshot);
        Files.writeString(snapshot.resolve("htdemucs.yaml"), "models: ['955717e8']");
        no(RuntimeState.stemsReady(p)); // the bag's description alone isn't the model
        Files.writeString(snapshot.resolve("955717e8.safetensors"), "");
        no(RuntimeState.stemsReady(p)); // empty
        Files.writeString(snapshot.resolve("955717e8.safetensors"), "weights");
        yes(RuntimeState.stemsReady(p));
        Path legacy = Files.createTempDirectory("aa-legacy");
        Files.createDirectories(legacy.resolve("hub").resolve("checkpoints"));
        Files.writeString(legacy.resolve("hub").resolve("checkpoints").resolve("955717e8-8726e21a.th"), "weights");
        yes(RuntimeState.hasModel(legacy)); // Demucs's older host, in TORCH_HOME
        no(RuntimeState.hasModel(legacy.resolve("missing")));
        Files.writeString(p.state(), "core=failed\n");
        no(RuntimeState.coreReady(p));
        Files.writeString(p.state(), "\u0000garbage=\\u12");
        no(RuntimeState.coreReady(p)); // unreadable state is "not set up"
    }

    static void processes() throws Exception {
        Path dir = Files.createTempDirectory("aa-proc");
        Path src = dir.resolve("Child.java");
        Files.writeString(src, "public class Child { public static void main(String[] a) throws Exception {"
            + " System.out.println(\"hello \" + System.getenv(\"AA_TEST\")); System.err.println(\"to stderr\");"
            + " if (a.length > 0) Thread.sleep(Long.parseLong(a[0])); System.exit(a.length > 1 ? Integer.parseInt(a[1]) : 0); } }");
        String java = Path.of(System.getProperty("java.home"), "bin", Platform.current() == Os.WINDOWS ? "java.exe" : "java").toString();
        Map<String, String> env = new LinkedHashMap<>(System.getenv());
        env.put("AA_TEST", "world");

        ProcessRunner.Result ok = ProcessRunner.run(List.of(java, src.toString()), env, dir, dir.resolve("logs/ok.log"),
            Duration.ofSeconds(60), () -> false, null);
        yes(ok.ok());
        yes(ok.output().contains("hello world"));
        yes(ok.output().contains("to stderr"));

        ProcessRunner.Result fail = ProcessRunner.run(List.of(java, src.toString(), "0", "3"), env, dir, dir.resolve("logs/fail.log"),
            Duration.ofSeconds(60), () -> false, null);
        eq(fail.exitCode(), 3);
        no(fail.ok());

        long t0 = System.nanoTime();
        ProcessRunner.Result late = ProcessRunner.run(List.of(java, src.toString(), "60000"), env, dir, dir.resolve("logs/late.log"),
            Duration.ofSeconds(3), () -> false, null);
        yes(late.timedOut());
        no(late.ok());
        yes((System.nanoTime() - t0) < 30_000_000_000L);

        long start = System.nanoTime();
        int[] polls = {0};
        ProcessRunner.Result cancelled = ProcessRunner.run(List.of(java, src.toString(), "60000"), env, dir, dir.resolve("logs/cancel.log"),
            Duration.ofMinutes(5), () -> System.nanoTime() - start > 3_000_000_000L, () -> polls[0]++);
        yes(cancelled.cancelled());
        no(cancelled.timedOut());
        yes(polls[0] > 3);
    }

    static void deletion() throws Exception {
        Path home = Files.createTempDirectory("aa-home");
        Path venv = home.resolve("venv");
        Files.createDirectories(venv.resolve("Lib").resolve("site"));
        Files.writeString(venv.resolve("Lib").resolve("site").resolve("x.py"), "x");
        Path outside = Files.createTempDirectory("aa-outside");
        Files.writeString(outside.resolve("keep.txt"), "keep");
        boolean refused = false;
        try { Installer.deleteInside(home, outside); } catch (java.io.IOException e) { refused = true; }
        yes(refused);
        yes(Files.exists(outside.resolve("keep.txt")));
        refused = false;
        try { Installer.deleteInside(home, home); } catch (java.io.IOException e) { refused = true; }
        yes(refused);
        refused = false;
        try { Installer.deleteInside(home, home.resolve("..").resolve(outside.getFileName())); } catch (java.io.IOException e) { refused = true; }
        yes(refused);
        Installer.deleteInside(home, venv);
        no(Files.exists(venv));
        yes(Files.isDirectory(home));
        Installer.deleteInside(home, home.resolve("missing")); // nothing there: fine
        Path single = home.resolve("python-download.tar.gz");
        Files.writeString(single, "x");
        Installer.deleteInside(home, single); // a file, too
        no(Files.exists(single));
        // a link inside the home to a folder outside: the link goes, what it points to stays
        Path python = home.resolve("python");
        Files.createDirectories(python);
        try {
            Files.createSymbolicLink(python.resolve("lib"), outside);
            Installer.deleteInside(home, python);
            no(Files.exists(python));
            yes(Files.exists(outside.resolve("keep.txt")));
        } catch (UnsupportedOperationException | java.io.IOException e) {
            Installer.deleteInside(home, python); // Windows without the right to make links
        }
    }

    static void progress() throws Exception {
        // weighting: a fresh core setup is 1.5 + 1.4 + 6 + 54 + 4 = 66.9 "seconds"
        List<Double> seen = new java.util.ArrayList<>();
        Progress plan = Installer.corePlan(new Progress(seen::add), true, true);
        plan.start("python-download");
        near(plan.fraction(), 0);
        plan.within(0.5);
        near(plan.fraction(), 0.75 / 66.9);
        plan.start("python-unpack");
        near(plan.fraction(), 1.5 / 66.9);
        plan.start("venv");
        plan.start("packages");
        near(plan.fraction(), 8.9 / 66.9);
        plan.within(0.5);
        near(plan.fraction(), (8.9 + 27) / 66.9);
        plan.start("check");
        near(plan.fraction(), 62.9 / 66.9);
        plan.complete();
        near(seen.get(seen.size() - 1), 1);
        for (int i = 1; i < seen.size(); i++) yes(seen.get(i) >= seen.get(i - 1)); // never goes down
        // Python and the environment already there: the packages are most of it
        Progress again = Installer.corePlan(new Progress(null), false, false);
        again.start("packages");
        again.within(1);
        near(again.fraction(), 54 / 58.0);
        // a step added later (the environment made again) lowers the fraction, but what is reported doesn't go down
        List<Double> out = new java.util.ArrayList<>();
        Progress late = new Progress(out::add).step("a", 1).step("b", 1);
        late.start("a");
        late.within(1);
        late.step("c", 2);
        late.start("b");
        near(late.fraction(), 0.25);
        near(out.get(out.size() - 1), 0.5);
        // stems: packages 70, model 9; Linux: PyTorch 45, packages 25, model 9
        Progress stems = Installer.stemsPlan(new Progress(null), false);
        stems.start("packages");
        stems.start("model");
        near(stems.fraction(), 70 / 79.0);
        Progress linux = Installer.stemsPlan(new Progress(null), true);
        linux.start("torch");
        linux.start("packages");
        near(linux.fraction(), 45 / 79.0);
        // out-of-range values are clamped
        Progress clamp = new Progress(null).step("x", 1);
        clamp.start("x");
        clamp.within(7);
        near(clamp.fraction(), 1);
        clamp.within(Double.NaN);
        near(clamp.fraction(), 0);
        Progress empty = new Progress(null);
        near(empty.fraction(), 0);

        // tqdm bars: only the big download counts
        near(Progress.tqdmFraction(" 45%|####5     | 38.0M/84.1M [00:03<00:04, 12.3MB/s]", 20e6), 0.45);
        near(Progress.tqdmFraction("100%|##########| 1.20k/1.20k [00:00<00:00]", 20e6), -1);
        near(Progress.tqdmFraction("model.safetensors:  62%|######2   | 52.4M/84.1M [00:04<00:02, 13.1MB/s]", 20e6), 0.62);
        near(Progress.tqdmFraction("100%|##########| 80.2M/80.2M [00:06<00:00, 12.9MB/s]", 20e6), 1);
        near(Progress.tqdmFraction(" 10%|#         | 8.40MB/84.1MB", 20e6), 0.10);
        near(Progress.tqdmFraction("Collecting numpy==1.26.4", 20e6), -1);
        near(Progress.tqdmFraction(null, 0), -1);
        Path dir = Files.createTempDirectory("aa-progress");
        Path pf = dir.resolve("job.progress");
        near(Progress.readFraction(pf), -1);
        Files.writeString(pf, "0.4231\n");
        near(Progress.readFraction(pf), 0.4231);
        Files.writeString(pf, "1.7");
        near(Progress.readFraction(pf), 1);
        Files.writeString(pf, "");
        near(Progress.readFraction(pf), -1);
        Files.writeString(pf, "nan");
        near(Progress.readFraction(pf), -1);

        // the log follower: new complete lines only, \r as progress bars write, a shorter file starts again
        Path log = dir.resolve("setup.log");
        LogFollower f = new LogFollower(log);
        eq(f.poll(), List.of());
        Files.writeString(log, "Collecting a==1\nColl");
        eq(f.poll(), List.of("Collecting a==1"));
        Files.writeString(log, "Collecting a==1\nCollecting b==2\r\n 10%|# | 1M/9M\r 20%|## | 2M/9M\r");
        eq(f.poll(), List.of("Collecting b==2", " 10%|# | 1M/9M", " 20%|## | 2M/9M"));
        eq(f.poll(), List.of());
        Files.writeString(log, "new\n");
        eq(f.poll(), List.of("new"));
    }

    static void pip() throws Exception {
        List<String> core = Files.readAllLines(Path.of("package/engine/requirements-core.txt"));
        List<String> stems = Files.readAllLines(Path.of("package/engine/requirements-stems.txt"));
        // core: 46 lines, 44 packages on Windows; pyreadline3 is Windows only; numba/llvmlite have one line per platform pair
        eq(PipProgress.packageCount(core, Os.WINDOWS, Arch.X86_64), 44);
        eq(PipProgress.packageCount(core, Os.MACOS, Arch.ARM64), 43);
        eq(PipProgress.packageCount(core, Os.MACOS, Arch.X86_64), 43);
        eq(PipProgress.packageCount(core, Os.LINUX, Arch.X86_64), 43);
        eq(PipProgress.packageCount(core, Os.LINUX, Arch.ARM64), 43);
        // stems: 30 lines, 29 packages on Windows (colorama), 28 elsewhere; torch once on every platform
        eq(PipProgress.packageCount(stems, Os.WINDOWS, Arch.X86_64), 29);
        eq(PipProgress.packageCount(stems, Os.MACOS, Arch.X86_64), 28);
        eq(PipProgress.packageCount(stems, Os.LINUX, Arch.ARM64), 28);
        eq(PipProgress.packageCount(List.of("a==1; sys_platform == \"darwin\" and platform_machine == \"x86_64\""), Os.MACOS, Arch.ARM64), 0);
        eq(PipProgress.packageCount(List.of("a==1; sys_platform == \"darwin\" and platform_machine == \"x86_64\""), Os.MACOS, Arch.X86_64), 1);
        eq(PipProgress.packageCount(List.of("a==1; sys_platform != \"darwin\" or platform_machine != \"x86_64\""), Os.WINDOWS, Arch.X86_64), 1);
        eq(PipProgress.packageCount(List.of("# c", "", "Foo_Bar==1", "foo-bar==1"), Os.LINUX, Arch.X86_64), 1);

        // pip with PEP 658 metadata: resolve (metadata), then downloads, then unpack
        PipProgress p = new PipProgress(3);
        eq(Status.packages(p), "Installing packages (0 of 3)");
        near(p.fraction(), 0);
        p.line("Collecting audioread==3.1.0 (from -r requirements-core.txt (line 10))");
        p.line("  Downloading audioread-3.1.0-py3-none-any.whl.metadata (9.0 kB)");
        near(p.fraction(), 0.05);
        eq(p.handled(), 0);
        p.line("Collecting basic-pitch==0.4.0 (from -r r.txt (line 11))");
        p.line("Collecting Numpy==1.26.4 (from -r r.txt (line 12))");
        p.line("Collecting numpy==1.26.4"); // the same package again doesn't count twice
        near(p.fraction(), 0.15);
        p.line("Downloading audioread-3.1.0-py3-none-any.whl (23 kB)");
        eq(p.handled(), 1);
        near(p.fraction(), 0.15);
        p.line("Downloading basic_pitch-0.4.0-py2.py3-none-any.whl (271 kB)");
        near(p.fraction(), 0.15 + 0.6 / 3);
        p.line("   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ 271.4/271.4 kB 3.1 MB/s eta 0:00:00");
        p.line("Downloading numpy-1.26.4-cp312-cp312-win_amd64.whl (15.5 MB)");
        eq(Status.packages(p), "Installing packages (3 of 3)");
        near(p.fraction(), 0.15 + 0.6 * 2 / 3);
        p.line("Installing collected packages: numpy, audioread, basic-pitch");
        near(p.fraction(), 0.75);
        eq(Status.packages(p), "Unpacking the packages");
        p.line("Successfully installed audioread-3.1.0 basic-pitch-0.4.0 numpy-1.26.4");
        near(p.fraction(), 1);
        // older pip: each wheel downloaded while resolving; already-installed packages (stem setup)
        PipProgress q = new PipProgress(4);
        q.line("Requirement already satisfied: numpy==1.26.4 in c:\\venv\\lib\\site-packages (from -r r.txt (line 20)) (1.26.4)");
        q.line("Requirement already satisfied: idna==3.20 in c:\\venv\\lib\\site-packages");
        q.line("Collecting demucs==4.1.0");
        q.line("  Downloading https://files.pythonhosted.org/packages/x/demucs-4.1.0-py3-none-any.whl (78 kB)");
        eq(q.handled(), 3);
        q.line("Collecting torch==2.11.0");
        q.line("  Downloading torch-2.11.0-cp312-cp312-win_amd64.whl (110.1 MB)");
        near(q.fraction(), 0.15 + 0.6 * 0.5);
        eq(Status.packages(q), "Installing packages (4 of 4)");
        q.line("Installing collected packages: torch, demucs");
        near(q.fraction(), 0.75);
        // nothing to do
        PipProgress r = new PipProgress(1);
        r.line("Requirement already satisfied: torch==2.11.0+cpu in /venv/lib/python3.12/site-packages");
        near(r.fraction(), 0.75);
        r.line(null);

        // status lines: short, no ellipsis
        for (String s : List.of(Status.downloadingPython("68 MB"), Status.UNPACKING_PYTHON, Status.CREATING_ENVIRONMENT,
                "Installing packages (46 of 46)", Status.UNPACKING_PACKAGES, Status.CHECKING, Status.INSTALLING_PYTORCH,
                Status.downloadingModel(Installer.MODEL_MB), Status.REMOVING, Status.CANCELLING)) {
            yes(s.length() <= 40);
            no(s.endsWith("…") || s.endsWith("."));
        }
        eq(Status.downloadingPython("47 MB"), "Downloading Python (47 MB)");
        eq(Status.downloadingModel(84), "Downloading the model (84 MB)");
        eq(Status.plain("Detecting the key of a.wav…"), "Detecting the key of a.wav");
        eq(Status.plain("Adding 3 notes..."), "Adding 3 notes");
        eq(Status.plain(" Waiting … "), "Waiting");
        eq(Status.plain(null), "");
        eq(Status.plain("…"), "");
    }

    static void near(double a, double b) {
        checks++;
        if (Math.abs(a - b) > 1e-9) throw new AssertionError("check " + checks + ": " + a + " != " + b);
    }

    static void yes(boolean b) {
        checks++;
        if (!b) throw new AssertionError("expected true (check " + checks + ")");
    }

    static void no(boolean b) {
        yes(!b);
    }

    static void eq(Object a, Object b) {
        checks++;
        if (a == null ? b != null : !a.equals(b)) throw new AssertionError("check " + checks + ": " + a + " != " + b);
    }
}
