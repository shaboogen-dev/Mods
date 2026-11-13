package com.bitwig.mods.selectedtracksview;

import bml.api.Settings;
import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** The mod's option, on the Mods page of Bitwig's Settings. */
public final class Options implements PreferenceProvider {
    private static final Settings STORE = SelectedTracksView.MOD.settings();
    private static final String HIDE_DEVICE_PANEL = "hide_device_panel";

    /** Whether opening the view hides the device panel (off by default). */
    static boolean hideDevicePanel() {
        return STORE.getBoolean(HIDE_DEVICE_PANEL, false);
    }

    @Override
    public void declare(Preferences preferences) {
        Page page = preferences.page("mods", "Mods");
        Section section = preferences.section(page, "selected-tracks-view", "Selected Tracks View");
        preferences.checkbox("hide_device_panel", "Hide the device panel while the view is open")
                   .in(section)
                   .value(Options::hideDevicePanel, value -> STORE.putBoolean(HIDE_DEVICE_PANEL, value));
    }
}
