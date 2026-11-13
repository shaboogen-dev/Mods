package com.bitwig.mods.choosepanelsides;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** Settings > Mods > Choose Sides: the Inspector's side and the Browser panel's side, applied at once. */
public final class SidesSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods"); // the page every mod shares: this exact ID
        Section section = preferences.section(mods, "choose_panel_sides", "Choose Sides");
        preferences.choice("inspector_side", "Inspector side", "Left", "Right")
                   .in(section)
                   .value(ChoosePanelSides::inspectorSide, ChoosePanelSides::setInspectorSide);
        preferences.choice("browser_side", "Browser panel side", "Left", "Right")
                   .in(section)
                   .value(ChoosePanelSides::browserSide, ChoosePanelSides::setBrowserSide);
    }
}
