package com.bitwig.mods.audioanalysis;

import java.util.List;

/**
 * The Python the setup downloads: one pinned CPython 3.12 build per platform from
 * python-build-standalone (Astral, MPL 2.0; github.com/astral-sh/python-build-standalone), the
 * {@code install_only} variant. The archive is checked against {@link #sha256} before it is
 * unpacked; a mismatch stops the setup. Pure data, tested offline.
 */
public record PythonBuild(Platform.Os os, Platform.Arch arch, String target, String url, String sha256, long size) {
    public static final String VERSION = "3.12.16";
    public static final String RELEASE = "20261026";
    public static final String HOST = "github.com";
    private static final String BASE = "https://github.com/astral-sh/python-build-standalone/releases/download/"
        + RELEASE + "/cpython-" + VERSION + "%2B" + RELEASE + "-";

    public static final List<PythonBuild> ALL = List.of(
        build(Platform.Os.WINDOWS, Platform.Arch.X86_64, "x86_64-pc-windows-msvc",
            "590dfcbe3a34c07301e6332e13e2188b98655cd072b08afa45a7998dc4c22bc5", 47_113_364L),
        build(Platform.Os.MACOS, Platform.Arch.ARM64, "aarch64-apple-darwin",
            "fbf4afef53c2a6b72bb741bc06d5671a9dfaf87bd84e5aa73ac9b3d0d589f14e", 25_172_086L),
        build(Platform.Os.MACOS, Platform.Arch.X86_64, "x86_64-apple-darwin",
            "a962e47df522889eaa7ff0d6a56203f1079f68aabb65ffdbe8455f362bc769f3", 24_873_291L),
        build(Platform.Os.LINUX, Platform.Arch.X86_64, "x86_64-unknown-linux-gnu",
            "6a79e922e50006b601f29e688407659da1c2b86ebcd4e9c7254cf234be8b89de", 67_921_566L),
        build(Platform.Os.LINUX, Platform.Arch.ARM64, "aarch64-unknown-linux-gnu",
            "a945427411dc6851a1393533b74a4a1ac434321ae397f2ec69897a9b4fde032b", 53_051_192L));

    private static PythonBuild build(Platform.Os os, Platform.Arch arch, String target, String sha256, long size) {
        return new PythonBuild(os, arch, target, BASE + target + "-install_only.tar.gz", sha256, size);
    }

    /** The build for this platform, or null where there is none (Windows on ARM, 32-bit, ...). */
    public static PythonBuild of(Platform.Os os, Platform.Arch arch) {
        for (PythonBuild b : ALL) if (b.os == os && b.arch == arch) return b;
        return null;
    }

    /** "47 MB". */
    public String sizeText() {
        return Math.round(size / 1_000_000.0) + " MB";
    }
}
