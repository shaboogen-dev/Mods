package com.bitwig.mods.capturemidi;

import bml.api.Bml;
import bml.api.Settings;

/** The mod's two options, kept in BML's per-user settings for the mod. */
final class Options {
    static final Bml.Mod MOD = Bml.mod("capture-midi");
    private static final Settings STORE = MOD.settings();

    static final int KEEP_MIN = 10, KEEP_MAX = 600, KEEP_DEFAULT = 120;
    static final int PAUSE_MIN = 0, PAUSE_MAX = 60, PAUSE_DEFAULT = 5;

    private Options() {
    }

    /** How far back a capture reaches, in seconds. */
    static int keepSeconds() {
        return clamp(STORE.getInt("keep_seconds", KEEP_DEFAULT), KEEP_MIN, KEEP_MAX);
    }

    static void setKeepSeconds(double seconds) {
        STORE.putInt("keep_seconds", clamp((int) Math.round(seconds), KEEP_MIN, KEEP_MAX));
    }

    /** A silence longer than this (seconds) starts a new phrase; only the latest phrase is captured. 0: off. */
    static int pauseSeconds() {
        return clamp(STORE.getInt("pause_seconds", PAUSE_DEFAULT), PAUSE_MIN, PAUSE_MAX);
    }

    static void setPauseSeconds(double seconds) {
        STORE.putInt("pause_seconds", clamp((int) Math.round(seconds), PAUSE_MIN, PAUSE_MAX));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
