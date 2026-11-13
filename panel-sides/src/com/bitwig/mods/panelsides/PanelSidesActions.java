package com.bitwig.mods.panelsides;

import bitwig.obf.aCp; // the project window
import bml.actions.ActionProvider;
import bml.actions.Actions;

/**
 * "Show or Hide Device Panel" (shortcut, Commander), through Framework: Actions and Menus: what
 * the device panel button does. The views' actions (Show, Add or Remove in the Split, Equalise
 * View Sizes) are Framework: Panels'.
 */
public final class PanelSidesActions implements ActionProvider {
    /** Bitwig's own "Panel Management" category in the shortcut settings and the Commander. */
    private static final String PANELS = "panel_management";

    @Override
    public void declare(Actions actions) {
        actions.action("panel_sides_toggle_devices", "Show or Hide Device Panel")
               .category(PANELS)
               .onRun(context -> toggleDevices(context.widget()));
    }

    private static void toggleDevices(Object window) {
        try {
            if (window instanceof aCp projectWindow) Streamlined.toggleDevices(projectWindow.Dah()); // Dah(): its current project view
        } catch (Throwable failure) {
            PanelSides.MOD.log("Could not show or hide the device panel", failure);
        }
    }
}
