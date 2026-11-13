package com.bitwig.mods.selectedtracksview;

import java.util.List;
import java.util.Map;

import bitwig.obf.aBW; // a main window's project view
import bitwig.obf.aDr; // opens a dockable dialog in its own window
import bitwig.obf.aYz; // the window's device panel
import bitwig.obf.doF; // where a dialog is shown: docked in a panel or in its own window
import bitwig.obf.dve; // a widget
import bml.actions.ActionContext;
import bml.api.Bml;
import com.bitwig.flt.app.CHR; // the application
import com.bitwig.flt.app.TL; // the application: decides where expanded device views dock
import com.bitwig.flt.document.core.iface.RYd; // a channel: a track, group, effect track or the master
import com.bitwig.flt.document.core.iface.pHd; // a project
import com.google.common.collect.MapMaker;

/**
 * Device: Selected Tracks View.
 *
 * Shows the device chains of the selected tracks side by side, one row per track, in Bitwig's track
 * order ({@link TracksView}). It is shown where Bitwig shows The Grid's expanded view: docked, or in
 * its own window. The view shows the tracks selected when it opens; run again while it is open, the
 * action closes it, whatever is selected. Needs two or more tracks. Optionally hides the device
 * panel while open ({@link Options}). No hooks: opened by an action of Framework: Action and Menu
 * Registration.
 */
public final class SelectedTracksView {
    static final Bml.Mod MOD = Bml.mod("selected-tracks-view");

    /** The open view of each window. */
    private static final Map<aBW, TracksView> OPEN = new MapMaker().weakKeys().makeMap();

    private SelectedTracksView() {
    }

    /**
     * Whether the user last undocked the view, for this Bitwig session: docked until the user
     * undocks it, docked again once the user docks it (Bitwig tells the view, aDn.FzB() / lB_()).
     */
    static volatile boolean undocked;

    /** The action: closes the view if it is open, whatever is selected; else opens it on the selected tracks. */
    static void toggle(ActionContext context) {
        try {
            aBW window = Tracks.window(context);
            pHd project = window != null ? window.mtS() : null; // the window's project
            if (project == null) {
                MOD.log("no project window to show the view in");
                return;
            }
            TracksView view = OPEN.get(window);
            if (view != null && view.Pfd()) { // shown
                view.Hnt(); // close
                return;
            }
            if (view != null) {
                OPEN.remove(window);
                view.iy_();
            }
            List<RYd> tracks = Tracks.inProjectOrder(project, Tracks.selected(context, project));
            if (tracks.size() < 2) return; // needs two or more tracks
            open(window, project, tracks);
        } catch (Throwable t) {
            MOD.log("could not open or close the Selected Tracks view", t);
        }
    }

    /**
     * Opens the view. Where follows Bitwig's expanded device view (com.bitwig.flt.app.TL.cfM(dve, doH)):
     * docked where The Grid's docks, in its own window when the user last undocked it or when Bitwig
     * has no dock for it (EDIT has none; nor has a window without a device panel).
     *
     * TL finds the dock from the anchor's nearest panel widget above it (anchor.TMF(aYH.class)),
     * then that panel's dock host (aYH.pYg(): its layout node's focus target, the arranger in
     * ARRANGE and the mixer in MIX) and its dock (qFI()). The Grid's anchor is its device header,
     * inside the device panel, so the panel found is the device panel (aYz). The anchor here is a
     * child of the device panel: dev.1 used panel.lYc(dve.class).findFirst(), and dve.lYc includes
     * the widget itself (dve.cfM(Class, false, true) puts this first when it matches), so the anchor
     * was the device panel, TMF found a panel above it, and Bitwig fell back to its own window.
     */
    private static void open(aBW window, pHd project, List<RYd> tracks) {
        TracksView view = new TracksView(window, project, tracks);
        OPEN.put(window, view);
        view.title("Selected Tracks");
        TL app = (TL) CHR.Grk();
        dve anchor = undocked ? null : insideDevicePanel(window);
        doF where = anchor == null ? aDr.cfM : app.cfM(anchor, view); // own window, or docked
        dve owner = anchor != null ? anchor : window;
        view.cfM(owner, where); // show it
        if (Options.hideDevicePanel()) view.hideDevicePanel();
    }

    /** A widget inside the window's device panel (one of its children): where Bitwig looks for the dock. */
    private static dve insideDevicePanel(aBW window) {
        try {
            aYz panel = window.LFv(); // the window's device panel
            if (panel == null) return null;
            Object child = panel.YBm().findFirst().orElse(null); // its first child (YBm: the children, without itself)
            return child instanceof dve widget ? widget : null;
        } catch (Throwable t) {
            MOD.log("could not find the device panel; opening in its own window", t);
            return null;
        }
    }

    static void disposed(TracksView view) {
        OPEN.remove(view.window, view);
    }
}
