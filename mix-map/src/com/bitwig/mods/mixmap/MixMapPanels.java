package com.bitwig.mods.mixmap;

import bml.api.Bml;
import bml.panels.PanelProvider;
import bml.panels.Panels;

/**
 * Panel: Mix Map's registration with Framework: Panels: one view, its icon, its content (the stage
 * and the list), its scale (the setting is the mod's own, in Settings > Mods: MixMapSettings) and
 * its saved value, the hidden tracks (their ids separated by commas, one set per display profile in
 * the project's view settings, shared by every place Mix Map is shown; never in the project).
 */
public final class MixMapPanels implements PanelProvider {
    static final String MOD_ID = "mix-map";
    static final Bml.Mod MOD = Bml.mod(MOD_ID);
    static final String VIEW_ID = "mix_map";
    static final String HIDDEN_TRACKS = "hidden_tracks";

    @Override
    public void declare(Panels panels) {
        panels.view(VIEW_ID, "Mix Map")
              .icon("mix_map_icon")               // the mod's own SVG (mod.json resources)
              .content(MixMapWidget::create)      // built for each window and each place
              .replaces("mixer")                  // in MIX (without a stack): the main content's choice, with the mixer
              .savedText(HIDDEN_TRACKS, "")       // the hidden tracks, empty by default
              .scaleSetting(false);               // its scale's setting is in Settings > Mods (MixMapSettings)
    }
}
