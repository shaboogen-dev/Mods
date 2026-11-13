package com.bitwig.mods.panelsides;

import bml.panels.PanelProvider;
import bml.panels.Panels;

/**
 * Streamlined Views' registration with Framework: Panels: Bitwig's three views of the stack
 * (Tracks, Detail Editor, Mixer, with Bitwig's own icons, declared since they are no longer
 * Bitwig's own buttons) and the layout policy. They have no scale of their own: with Panel: Scale
 * Each Panel on, they and the device panel follow its entries (matched by Bitwig's panel classes).
 */
public final class StreamlinedPanels implements PanelProvider {
    @Override
    public void declare(Panels panels) {
        panels.bitwigView("tracks", "Tracks").icon("tracks_icon").scale(false); // scaled by Panel: Scale Each Panel's Tracks, when on
        panels.bitwigView("editor", "Detail Editor").icon("editor_icon").scale(false);
        panels.bitwigView("mixer", "Mixer").icon("mixer_icon").scale(false);
        panels.policy(new Streamlined());
    }
}
