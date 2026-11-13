package com.bitwig.mods.panelsides;

import bitwig.obf.aBW; // a window's project view (its layout widgets and bottom bar)
import bitwig.obf.akC; // a display profile: its windows and their layouts
import bitwig.obf.akQ; // a panel
import bitwig.obf.akn; // a mode of the main view area (ARRANGE, MIX, EDIT)
import bitwig.obf.dWb; // a row or column of widgets
import bitwig.obf.doH; // the dialog windows (plug-in windows, undocked views)
import bitwig.obf.dpN; // Bitwig's UI event loop
import bitwig.obf.dve; // a widget
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Settings;
import bml.api.Hook;
import bml.api.This;
import com.bitwig.base.geom.IBe; // the bottom bar's row style
import com.bitwig.flt.app.CHR; // the application (its windows)
import com.bitwig.flt.document_view_settings.rsB; // the base of a project's view settings: holds the current mode

/**
 * Panel: Streamlined Views (mod ID panel-sides). The helper class: the hooks and the setting.
 *
 * Bitwig builds each display profile as a tree of splitters at startup. Framework: Panels runs this
 * mod's layout policy (Streamlined) on each profile: the view stack in place of the main area's
 * modes, and the device panel inside the main area or across the full width. The hooks here make
 * Bitwig's modes presets of the stack and give the bottom bar the device panel's button. A change
 * of the setting rebuilds the window at once.
 */
public final class PanelSides {
    static final Bml.Mod MOD = Bml.mod("panel-sides");
    private static final Settings STORE = MOD.settings();
    private static final String FULL_WIDTH_KEY = "device_panel_full_width";

    /** The layout this session uses: read at startup, then changed with the setting. */
    private static volatile boolean deviceFullWidth = STORE.getBoolean(FULL_WIDTH_KEY, false);

    private PanelSides() {
    }

    // ---------- The setting (stored, and applied at once) ----------

    static boolean deviceFullWidth() {
        return deviceFullWidth;
    }

    static void setDeviceFullWidth(boolean full) {
        STORE.putBoolean(FULL_WIDTH_KEY, full);
        if (full == deviceFullWidth) return;
        deviceFullWidth = full;
        relayOut();
    }

    /**
     * Places every profile's device panel again and rebuilds the window, as Bitwig does when a
     * display profile is chosen in Settings: after the current event, with the documents' updates
     * held (puu / sc) and the open dialog windows hidden and shown again around the rebuild. The
     * project's view settings stay, and the paths don't change, so sizes and views stay as they were.
     */
    private static void relayOut() {
        dpN.TVZ().asyncExec(() -> { // dpN.TVZ(): Bitwig's UI event loop
            CHR app = CHR.Grk(); // the application
            app.puu();           // holds the documents' updates
            try {
                doH.JhA();       // hides the open dialog windows (plug-in windows, undocked views)
                try {
                    Streamlined.placeDevicesEverywhere(deviceFullWidth);
                    app.kTh();   // rebuilds the windows from the profile
                } finally {
                    doH.VpW();   // shows them again
                }
            } catch (Throwable failure) {
                MOD.log("Could not apply the changed layout; restart Bitwig to apply it.", failure);
            } finally {
                app.sc();        // releases the documents' updates
            }
        });
    }

    // ---------- Hooks: the view stack's modes and the device panel ----------

    /** Every mode change (chooser, "Switch to Mode", controllers, project load) passes here: its preset. */
    @Hook(target = rsB.class, method = "Hnt(Lakn;)V", at = At.ENTRY, id = "mode-preset")
    public static void modeChosen(@This rsB settings, @Arg(0) akn mode) {
        try {
            Streamlined.modeChosen(settings, mode);
        } catch (Throwable failure) {
            MOD.log("Could not apply the views of mode " + (mode == null ? null : mode.tSS()), failure);
        }
    }

    /**
     * The bottom bar's left group: a panel outside the main area gets Bitwig's button there. The
     * device panel across the full width is outside it, and has its own button already.
     */
    @Hook(target = akQ.class, method = "cfM(LdWb;LaBW;)V", at = At.REPLACE_CALL, id = "left-group-button",
          callOwner = akQ.class, call = "Hnt(LdWb;LaBW;)V", occurrence = 1)
    public static void leftGroupButton(akQ panel, dWb row, aBW window) {
        sideGroupButton(panel, row, window);
    }

    /** The bottom bar's right group: as the left group. */
    @Hook(target = akQ.class, method = "MRY(LdWb;LaBW;)V", at = At.REPLACE_CALL, id = "right-group-button",
          callOwner = akQ.class, call = "Hnt(LdWb;LaBW;)V", occurrence = 1)
    public static void rightGroupButton(akQ panel, dWb row, aBW window) {
        sideGroupButton(panel, row, window);
    }

    private static void sideGroupButton(akQ panel, dWb row, aBW window) {
        boolean devices;
        try {
            devices = Streamlined.isDevices(panel);
        } catch (Throwable failure) {
            devices = false;
        }
        if (!devices) panel.Hnt(row, window); // Bitwig's button for the panel
    }

    /** The bottom bar: the device panel's button in place of the per-mode panel buttons. */
    @Hook(target = aBW.class, method = "LEd()Ldve;", at = At.REPLACE_CALL, id = "device-button",
          callOwner = akC.class, call = "MRY(LaBW;Lcom/bitwig/base/geom/IBe;)Ldve;", occurrence = 1)
    public static dve deviceButton(akC profile, aBW window, IBe style) {
        try {
            dve row = Streamlined.deviceButton(profile, window, style);
            if (row != null) return row;
        } catch (Throwable failure) {
            MOD.log("Could not build the device panel button; the bottom bar keeps Bitwig's panel buttons.", failure);
        }
        return profile.MRY(window, style); // Bitwig's per-mode panel buttons
    }
}
