package com.bitwig.mods.lookandfeel;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** The mod's section on the Settings > Mods page. */
public final class LookAndFeelSettings implements PreferenceProvider {
    @Override
    public void declare(final Preferences preferences) {
        final Page mods = preferences.page("mods", "Mods"); // the page every mod shares: this exact ID
        final Section section = preferences.section(mods, "look-and-feel", "Smooth Scrolling");
        preferences.checkbox("smooth_scrolling", "Smooth scrolling")
                   .in(section)
                   .value(Options::smoothScrolling, Options::setSmoothScrolling);
    }
}
