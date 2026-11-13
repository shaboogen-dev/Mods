package com.bitwig.mods.mixmap;

import bml.panels.PanelScale;
import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/**
 * Settings > Mods > Mix Map: the panel's scale. The value is Framework: Panels' own (the same one
 * the button's right-click menu sets), so a change applies at once in every window.
 */
public final class MixMapSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods"); // the page every mod shares: this exact ID
        Section section = preferences.section(mods, "mix_map", "Mix Map");
        preferences.dropdown("scale", "Scale", PanelScale.choices().toArray(String[]::new))
                   .in(section)
                   .value(() -> PanelScale.get(MixMapPanels.MOD_ID, MixMapPanels.VIEW_ID),
                          choice -> PanelScale.set(MixMapPanels.MOD_ID, MixMapPanels.VIEW_ID, choice));
    }
}
