package com.bitwig.mods.lookandfeel;

import bml.api.Bml;
import bml.api.Settings;

/** The mod's settings. Kept apart from the Settings page class, which needs the framework's classes. */
final class Options {
    private static final Settings STORE = Bml.mod("look-and-feel").settings();

    private static volatile boolean smoothScrolling = STORE.getBoolean("smooth_scrolling", true);

    private Options() {
    }

    static boolean smoothScrolling() {
        return smoothScrolling;
    }

    static void setSmoothScrolling(final boolean value) {
        smoothScrolling = value;
        STORE.putBoolean("smooth_scrolling", value);
    }
}
