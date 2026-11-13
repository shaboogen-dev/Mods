package com.bitwig.mods.audioanalysis;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Where things are on each platform, as pure functions of the OS name, the architecture and the
 * folders, so they can be tested on any machine. No Bitwig names; the only file access is
 * {@link #missingWindowsRuntime}.
 */
public final class Platform {
    public enum Os { WINDOWS, MACOS, LINUX }

    public enum Arch { X86_64, ARM64, OTHER }

    /** The Python version the pinned packages are built for (cp312 wheels). */
    public static final String PYTHON_VERSION = "3.12";

    private Platform() { }

    public static Os os(String osName) {
        String n = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (n.startsWith("windows")) return Os.WINDOWS;
        if (n.startsWith("mac") || n.contains("darwin")) return Os.MACOS;
        return Os.LINUX;
    }

    public static Os current() {
        return os(System.getProperty("os.name"));
    }

    /** From Java's {@code os.arch}: "amd64"/"x86_64" or "aarch64"/"arm64". */
    public static Arch arch(String osArch) {
        String a = osArch == null ? "" : osArch.toLowerCase(Locale.ROOT);
        if (a.equals("amd64") || a.equals("x86_64") || a.equals("x64")) return Arch.X86_64;
        if (a.equals("aarch64") || a.equals("arm64")) return Arch.ARM64;
        return Arch.OTHER;
    }

    public static Arch currentArch() {
        return arch(System.getProperty("os.arch"));
    }

    /** The interpreter of an unpacked python-build-standalone build ({@code <home>/python}). */
    public static Path standalonePython(Path pythonDir, Os os) {
        return os == Os.WINDOWS ? pythonDir.resolve("python.exe") : pythonDir.resolve("bin").resolve("python3");
    }

    /** The interpreter inside a virtual environment. */
    public static Path venvPython(Path venv, Os os) {
        return os == Os.WINDOWS ? venv.resolve("Scripts").resolve("python.exe") : venv.resolve("bin").resolve("python3");
    }

    /** The PATH separator. */
    public static String pathSeparator(Os os) {
        return os == Os.WINDOWS ? ";" : ":";
    }

    /** The name of the PATH variable as it appears in {@code env} (Windows keeps "Path"). */
    public static String pathKey(Map<String, String> env) {
        for (String k : env.keySet()) if (k.equalsIgnoreCase("PATH")) return k;
        return "PATH";
    }

    /**
     * Microsoft's C++ runtime DLLs that onnxruntime, PyTorch and llvmlite need on Windows and the
     * downloaded Python doesn't carry (it has only vcruntime140): installed system-wide by the
     * Visual C++ Redistributable, which most Windows computers have.
     */
    public static final java.util.List<String> WINDOWS_RUNTIME = java.util.List.of("msvcp140.dll", "msvcp140_1.dll");
    public static final String WINDOWS_RUNTIME_URL = "https://aka.ms/vs/17/release/vc_redist.x64.exe";

    /** Which of {@link #WINDOWS_RUNTIME} are missing from {@code system32}. */
    public static java.util.List<String> missingWindowsRuntime(Path system32) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String dll : WINDOWS_RUNTIME) if (!java.nio.file.Files.isRegularFile(system32.resolve(dll))) out.add(dll);
        return out;
    }

    /** The Windows system folder, from SystemRoot. */
    public static Path system32(Map<String, String> env) {
        String root = null;
        for (Map.Entry<String, String> e : env.entrySet()) if (e.getKey().equalsIgnoreCase("SystemRoot")) root = e.getValue();
        return Path.of(root == null || root.isBlank() ? "C:\\Windows" : root, "System32");
    }

    /** The version a Python printed ("3.12") is the one the packages are built for. */
    public static boolean acceptable(String printedVersion) {
        return printedVersion != null && PYTHON_VERSION.equals(printedVersion.trim());
    }
}
