package com.bitwig.mods.audiotomidi;

/** What to transcribe. */
public enum Target {
    MELODY("Melody"), HARMONY("Harmony"), DRUMS("Drums");

    public final String label;

    Target(String label) {
        this.label = label;
    }
}
