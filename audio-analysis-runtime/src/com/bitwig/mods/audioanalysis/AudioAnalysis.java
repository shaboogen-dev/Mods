package com.bitwig.mods.audioanalysis;

import bml.api.Bml;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Helper: Audio Analysis. What the audio clip tools share: where the Python runtime lives,
 * whether it is set up, how to run the engine, the selected audio clip ({@link AudioSelection}),
 * background jobs ({@link Jobs}) and the project changes ({@link Insert}).
 *
 * <p>Nothing is downloaded or installed until the user chooses Set Up (and confirms Bitwig's dialog
 * that says what will be downloaded and where). Everything lives in this mod's per-user data folder
 * in BML's settings folder: delete it, or use Remove Audio Analysis Setup, to remove it.
 */
public final class AudioAnalysis {
    public static final String MOD_ID = "audio-analysis-runtime";
    public static final Bml.Mod MOD = Bml.mod(MOD_ID);

    private static volatile RuntimePaths paths;

    private AudioAnalysis() { }

    /** The runtime's folders: its home in BML's per-user folder, and the shipped engine. */
    public static RuntimePaths paths() {
        RuntimePaths p = paths;
        if (p == null) {
            synchronized (AudioAnalysis.class) {
                p = paths;
                if (p == null) {
                    Path home = MOD.resource("runtime"); // preserve directory in BML's per-user folder
                    Path engine = MOD.resource("engine"); // the shipped engine (read only)
                    paths = p = new RuntimePaths(home, engine, Platform.current());
                }
            }
        }
        return p;
    }

    /** The core part (Audio to MIDI, Detect Key) is set up. Cheap: menus ask it as they open. */
    public static boolean coreReady() {
        return readiness()[0];
    }

    /** Stem separation is set up (with its model on disk). */
    public static boolean stemsReady() {
        return readiness()[1];
    }

    private static volatile boolean[] ready;
    private static volatile long readyAt;

    /** The setup state, read from disk at most once every two seconds. */
    private static boolean[] readiness() {
        boolean[] r = ready;
        long now = System.nanoTime();
        if (r == null || now - readyAt > 2_000_000_000L) {
            RuntimePaths p = paths();
            r = new boolean[] {RuntimeState.coreReady(p), RuntimeState.stemsReady(p)};
            ready = r;
            readyAt = now;
        }
        return r;
    }

    /** Forget the cached setup state (after a setup or removal). */
    static void stateChanged() {
        ready = null;
    }

    /**
     * True when the core is set up; otherwise says how to set it up in a toast (Bitwig's transient notification).
     * {@code tool} names the tool ("Detect Key").
     */
    public static boolean requireCore(String tool) {
        if (coreReady()) return true;
        Ui.notify(tool + " needs Audio Analysis set up first: right-click an audio clip > Audio Analysis > Set Up Audio Analysis…");
        return false;
    }

    /** As {@link #requireCore}, for stem separation. */
    public static boolean requireStems(String tool) {
        if (!requireCore(tool)) return false;
        if (stemsReady()) return true;
        Ui.notify(tool + " needs stem separation set up first: right-click an audio clip > Audio Analysis > Set Up Stem Separation…");
        return false;
    }

    /** The environment for the engine and the tools' scripts. */
    public static Map<String, String> engineEnvironment() {
        return Commands.engineEnvironment(System.getenv(), paths());
    }

    /** The interpreter of the set-up runtime. */
    public static Path python() {
        return paths().python();
    }

    /** The runtime's cache folder (created). */
    public static Path cache() throws java.io.IOException {
        Path c = paths().cache();
        Files.createDirectories(c);
        return c;
    }

    /**
     * A key for a sample file's analysis results: its path, size and modification time, so an edited
     * file gets new results.
     */
    public static String audioKey(File audio) {
        return audioKey(audio.getAbsolutePath(), audio.length(), audio.lastModified());
    }

    public static String audioKey(String absolutePath, long length, long lastModified) {
        return Integer.toUnsignedString((absolutePath + "|" + length + "|" + lastModified).hashCode(), 36);
    }
}
