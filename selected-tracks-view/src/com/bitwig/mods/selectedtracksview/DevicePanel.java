package com.bitwig.mods.selectedtracksview;

import bitwig.obf.AQw; // a saved on/off value (a panel's "is shown")
import bitwig.obf.LJt; // a saved text value (a tab group's choice)
import bitwig.obf.aBW; // a main window's project view
import bitwig.obf.ajF; // a tab group (several panels, one shown)
import bitwig.obf.ajS; // the device panel's layout node
import bitwig.obf.akQ; // a panel's layout node

/**
 * The window's device panel shown or hidden, through Bitwig's own toggle: aBW.cfM(ajS.class, false),
 * what Toggle Device Panel runs (through aBW.UNj(false), which Bitwig 6.0 doesn't have). It flips the panel's own
 * "is shown" when it has one, or, for a tab of a tab group (the device panel in ARRANGE, MIX and EDIT
 * of the stock layouts), chooses the tab or closes the group ("" as its choice). Whether the panel
 * is shown is read the same way, from the window's view settings (aBW.RDJ()).
 */
final class DevicePanel {
    private DevicePanel() {
    }

    /** The device panel's node in the window's current mode, or null. */
    private static akQ node(aBW window) {
        Object node = window.cfM(ajS.class); // as Bitwig's toggle finds it: the current mode first
        return node instanceof akQ panel ? panel : null;
    }

    /** Bitwig's Toggle Device Panel. */
    private static void toggle(aBW window) {
        window.cfM(ajS.class, false);
    }

    /** Whether the panel is shown: TRUE, FALSE, or null when it can't be told. */
    private static Boolean shown(aBW window, akQ panel) {
        AQw own = panel.cfM(window.RDJ()); // its own "is shown", if it has one
        if (own != null) return own.getBoolValue();
        Object group = panel.IwV(ajF.class); // the tab group holding it
        if (group instanceof ajF tabs) {
            LJt choice = tabs.cfM(window.RDJ()); // the group's chosen tab
            if (choice != null) return panel.tSS().equals(choice.getValue());
        }
        return null;
    }

    /**
     * Hides the device panel if it is shown. Returns its node when this call hid it, null when it
     * was already hidden, can't be told or didn't hide (Bitwig's toggle only focuses a tab of a group
     * that can't close).
     */
    static Object hide(aBW window) {
        try {
            akQ panel = node(window);
            if (panel == null) return null;
            Boolean before = shown(window, panel);
            if (!Boolean.TRUE.equals(before)) return null; // already hidden, or can't be told: left as it is
            toggle(window);
            if (Boolean.FALSE.equals(shown(window, panel))) return panel;
            SelectedTracksView.MOD.log("Bitwig's toggle didn't hide the device panel");
        } catch (Throwable t) {
            SelectedTracksView.MOD.log("could not hide the device panel", t);
        }
        return null;
    }

    /**
     * Shows the device panel this view hid, if it is still hidden and is still the panel of the
     * window's current mode (another mode keeps its own panel as it is).
     */
    static void restore(aBW window, Object hidden) {
        try {
            akQ panel = node(window);
            if (panel == null || panel != hidden) return; // another mode is shown: its device panel is left as it is
            if (Boolean.FALSE.equals(shown(window, panel))) toggle(window);
        } catch (Throwable t) {
            SelectedTracksView.MOD.log("could not show the device panel again", t);
        }
    }
}
