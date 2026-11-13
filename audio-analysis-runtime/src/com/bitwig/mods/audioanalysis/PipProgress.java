package com.bitwig.mods.audioanalysis;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * How far a {@code pip install --no-deps -r <lock file>} is, from the lines pip prints. The lock file
 * is complete and pinned, so the number of packages is known before pip starts
 * ({@link #packageCount}). pip's lines, stable across pip 20.3 to 26:
 * <ul>
 *   <li>{@code Collecting <name>==<version> ...}: a package is being resolved;</li>
 *   <li>{@code Requirement already satisfied: ...}: it is there already (stem setup: numpy and
 *       others came with the core setup; Linux: torch from PyTorch's index);</li>
 *   <li>{@code Downloading <file>.whl (12.3 MB)} (or a URL ending in {@code .whl}): a wheel's download
 *       starts, so the one before has finished; {@code <file>.whl.metadata} lines are only metadata;</li>
 *   <li>{@code Installing collected packages: ...}: every download is done, pip unpacks them all;</li>
 *   <li>{@code Successfully installed ...}: done.</li>
 * </ul>
 * Within pip, resolving counts 15 %, downloading 60 % (by wheels) and unpacking 25 %. Pure: tested offline.
 */
public final class PipProgress {
    static final double RESOLVE = 0.15, DOWNLOAD = 0.60;

    private final int total;
    private final Set<String> collected = new LinkedHashSet<>();
    private int satisfied;
    private int wheels;
    private boolean installing;
    private boolean finished;

    public PipProgress(int total) {
        this.total = Math.max(1, total);
    }

    /** One line of pip's output. */
    public void line(String raw) {
        if (raw == null) return;
        String line = raw.strip();
        if (line.startsWith("Collecting ")) {
            collected.add(name(line.substring(11)));
        } else if (line.startsWith("Requirement already satisfied:")) {
            satisfied++;
        } else if ((line.startsWith("Downloading ") || line.startsWith("Using cached ") || line.startsWith("File was already downloaded "))
                && isWheel(line)) {
            wheels++;
        } else if (line.startsWith("Installing collected packages")) {
            installing = true;
        } else if (line.startsWith("Successfully installed")) {
            installing = true;
            finished = true;
        }
    }

    /** How far pip is, 0 to 1. */
    public double fraction() {
        if (finished) return 1;
        double resolved = Math.min(1, (double) (collected.size() + satisfied) / total);
        int toFetch = Math.max(0, total - satisfied);
        double fetched;
        if (installing || toFetch == 0) fetched = 1;
        else fetched = Math.min(1, (double) Math.max(0, wheels - 1) / toFetch);
        if (installing) resolved = 1;
        return RESOLVE * resolved + DOWNLOAD * fetched;
    }

    /** Packages handled so far, for "Installing packages (12 of 46)": those already there plus those fetched or being fetched. */
    public int handled() {
        if (installing) return total;
        return Math.min(total, satisfied + wheels);
    }

    public int total() {
        return total;
    }

    /** True once pip has started unpacking what it downloaded. */
    public boolean unpacking() {
        return installing && !finished;
    }

    private static boolean isWheel(String line) {
        String l = line.toLowerCase(Locale.ROOT);
        int i = l.indexOf(".whl");
        return i >= 0 && !l.startsWith(".metadata", i + 4);
    }

    private static String name(String spec) {
        int end = 0;
        while (end < spec.length() && "=<>!~;[ (".indexOf(spec.charAt(end)) < 0) end++;
        return spec.substring(0, end).toLowerCase(Locale.ROOT).replace('_', '-');
    }

    // ---------- the lock file ----------

    /**
     * The number of packages a lock file installs on this platform: its pinned lines whose
     * environment marker holds, each package once.
     */
    public static int packageCount(List<String> lines, Platform.Os os, Platform.Arch arch) {
        Set<String> names = new LinkedHashSet<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("-")) continue;
            int semi = line.indexOf(';');
            String spec = semi < 0 ? line : line.substring(0, semi);
            if (semi >= 0 && !marker(line.substring(semi + 1).strip(), os, arch)) continue;
            names.add(name(spec.strip()));
        }
        return names.size();
    }

    /**
     * Evaluates the markers the lock files use: comparisons ({@code ==}, {@code !=}) of
     * {@code sys_platform} and {@code platform_machine}, joined by {@code and} / {@code or}
     * ({@code and} binds tighter). Anything else counts as true.
     */
    static boolean marker(String marker, Platform.Os os, Platform.Arch arch) {
        for (String any : marker.split("\\s+or\\s+")) {
            boolean all = true;
            for (String term : any.split("\\s+and\\s+")) all &= term(term.strip(), os, arch);
            if (all) return true;
        }
        return false;
    }

    private static boolean term(String term, Platform.Os os, Platform.Arch arch) {
        boolean equal;
        String[] parts;
        if (term.contains("!=")) {
            parts = term.split("!=", 2);
            equal = false;
        } else if (term.contains("==")) {
            parts = term.split("==", 2);
            equal = true;
        } else {
            return true;
        }
        String variable = parts[0].strip();
        String value = parts[1].strip().replaceAll("^[\"']|[\"']$", "");
        String actual = switch (variable) {
            case "sys_platform" -> sysPlatform(os);
            case "platform_machine" -> machine(os, arch);
            default -> null;
        };
        if (actual == null) return true;
        return actual.equals(value) == equal;
    }

    static String sysPlatform(Platform.Os os) {
        return switch (os) {
            case WINDOWS -> "win32";
            case MACOS -> "darwin";
            case LINUX -> "linux";
        };
    }

    static String machine(Platform.Os os, Platform.Arch arch) {
        return switch (arch) {
            case X86_64 -> os == Platform.Os.WINDOWS ? "AMD64" : "x86_64";
            case ARM64 -> os == Platform.Os.WINDOWS ? "ARM64" : os == Platform.Os.MACOS ? "arm64" : "aarch64";
            case OTHER -> "";
        };
    }
}
