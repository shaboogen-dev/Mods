package com.bitwig.mods.linewaveforms;

import bml.api.Bml;
import bml.api.Settings;

/** The mod's settings. Kept apart from the Settings page class, which needs the framework's classes. */
final class Options {
    private static final Settings STORE = Bml.mod("line-waveforms").settings();

    /** Read on every waveform drawn. */
    private static volatile boolean lineWaveforms = STORE.getBoolean("line_waveforms", true);

    private Options() {
    }

    static boolean lineWaveforms() {
        return lineWaveforms;
    }

    static void setLineWaveforms(final boolean value) {
        lineWaveforms = value;
        STORE.putBoolean("line_waveforms", value);
    }
}
