package com.bitwig.mods.sharedtoolmode;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** The on/off checkbox, on the Mods page of Bitwig's Settings. */
public final class SharedToolModeSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page page = preferences.page("mods", "Mods");
        Section section = preferences.section(page, "shared-tool-mode", "Shared Tool Mode");
        preferences.checkbox("enabled", "Same tool in every editor")
                   .in(section)
                   .value(SharedToolMode::enabled, SharedToolMode::setEnabled);
    }
}
