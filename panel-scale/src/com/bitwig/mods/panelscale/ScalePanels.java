package com.bitwig.mods.panelscale;

import java.util.List;

import bml.panels.PanelProvider;
import bml.panels.Panels;

/**
 * Bitwig's main panels, each declared with Framework: Panels by its class, so each has a scale
 * wherever Bitwig shows it. The classes share no ancestor below Bitwig's panel class, so a panel
 * matches one entry. The settings are this mod's own (ScaleSettings), not the framework's list.
 */
public final class ScalePanels implements PanelProvider {
    static final String MOD_ID = "panel-scale";

    /** A panel: its ID (the stored scale's key), its label (Bitwig's own name for it) and its class. */
    record Entry(String id, String label, Class<?> panelClass) {
    }

    static final List<Entry> PANELS = List.of(
            new Entry("tracks", "Tracks", bitwig.obf.ajv.class),              // the arranger
            new Entry("editor", "Detail Editor", bitwig.obf.ajP.class),
            new Entry("mixer", "Mixer", bitwig.obf.akj.class),                // the big mixer and the small one
            new Entry("devices", "Device panel", bitwig.obf.ajS.class),
            new Entry("inspector", "Inspector", bitwig.obf.akc.class),
            new Entry("browser", "Browser Panel", bitwig.obf.ajz.class),      // akW, the Browser panel, is its only subclass
            new Entry("project", "Project Panel", bitwig.obf.akT.class),      // with its own tabs (Settings, Info, ...)
            new Entry("studio", "Studio Monitoring Panel", bitwig.obf.alc.class),
            new Entry("mappings", "Mappings Panel", bitwig.obf.akf.class),
            new Entry("keyboard", "On-Screen Keyboard", bitwig.obf.akt.class));

    @Override
    public void declare(Panels panels) {
        for (Entry panel : PANELS) {
            panels.bitwigPanel(panel.id(), panel.label(), panel.panelClass()).scaleSetting(false);
        }
    }
}
