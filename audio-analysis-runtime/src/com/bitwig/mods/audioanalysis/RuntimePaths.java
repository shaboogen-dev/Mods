package com.bitwig.mods.audioanalysis;

import java.nio.file.Path;

/**
 * The runtime's folders. {@code home} is the mod's per-user data folder from BML (a {@code preserve}
 * directory resource, so it lives in BML's settings folder and survives updates); {@code engine} is
 * the Python engine shipped in the mod's own folder (read only).
 */
public record RuntimePaths(Path home, Path engine, Platform.Os os) {
    /** The downloaded Python (python-build-standalone), unpacked. */
    public Path pythonDir() { return home.resolve("python"); }
    public Path basePython() { return Platform.standalonePython(pythonDir(), os); }
    /** Where the Python archive is downloaded to, and deleted from once unpacked. */
    public Path pythonArchive() { return home.resolve("python-download.tar.gz"); }
    public Path venv() { return home.resolve("venv"); }
    public Path python() { return Platform.venvPython(venv(), os); }
    public Path cache() { return home.resolve("cache"); }
    public Path stemsCache() { return cache().resolve("stems"); }
    /** TORCH_HOME, with HF_HOME inside: the stem model is downloaded here by the stem setup, and only there. */
    public Path models() { return home.resolve("models"); }
    public Path huggingFace() { return models().resolve("huggingface"); }
    public Path logs() { return home.resolve("logs"); }
    public Path state() { return home.resolve("state.properties"); }
    public Path coreRequirements() { return engine.resolve("requirements-core.txt"); }
    public Path stemsRequirements() { return engine.resolve("requirements-stems.txt"); }
}
