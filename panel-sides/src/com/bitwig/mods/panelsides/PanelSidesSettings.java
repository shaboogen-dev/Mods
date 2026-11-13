package com.bitwig.mods.panelsides;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** Settings > Mods > Streamlined Views (through Framework: Settings): the device panel across the full width, applied at once. */
public final class PanelSidesSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods"); // the page every mod shares: this exact ID
        Section section = preferences.section(mods, "streamlined_views", "Streamlined Views");
        preferences.checkbox("device-panel-full-width", "Device panel across the full width")
                   .in(section)
                   .value(PanelSides::deviceFullWidth, PanelSides::setDeviceFullWidth);
    }
}
