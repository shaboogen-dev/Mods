package com.bitwig.mods.panelscale;

import bml.panels.PanelScale;
import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** Settings > Mods > Scale Each Panel: one dropdown per panel. */
public final class ScaleSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods");
        Section section = preferences.section(mods, "panel_scale", "Scale Each Panel");
        String[] choices = PanelScale.choices().toArray(String[]::new);
        for (ScalePanels.Entry panel : ScalePanels.PANELS) {
            String id = panel.id();
            preferences.dropdown("scale_" + id, panel.label(), choices)
                       .in(section)
                       .value(() -> PanelScale.get(ScalePanels.MOD_ID, id), index -> PanelScale.set(ScalePanels.MOD_ID, id, index));
        }
    }
}
