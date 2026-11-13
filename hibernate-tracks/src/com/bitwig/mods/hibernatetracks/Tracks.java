package com.bitwig.mods.hibernatetracks;

import bitwig.obf.ICh; // a selection of document objects
import bitwig.obf.aYp; // an arranger panel
import bitwig.obf.big; // the view's selection object a selection menu opens on
import bitwig.obf.dve; // a widget
import bitwig.obf.xJo; // a track
import bml.actions.ActionContext;
import bml.api.Bml;
import com.bitwig.flt.app.CHR; // the application window controller (shows notifications)
import com.bitwig.flt.document.core.master.ruS; // a group track

import java.util.ArrayList;
import java.util.List;

/** The tracks an action applies to, and how the mod tells the user something. */
final class Tracks {
    static final Bml.Mod MOD = Bml.mod("hibernate-tracks");

    private Tracks() {
    }

    /** The selected objects: of the menu's selection, or of the arranger panel for a shortcut or the Commander. */
    static List<Object> selected(ActionContext context) {
        List<Object> items = new ArrayList<>();
        ICh selection = null;
        if (context.selection() instanceof big view) {
            selection = view.UNj(); // the selection behind the view
        } else if (context.widget() instanceof dve window) {
            selection = arrangerSelection(window);
        }
        if (selection != null) items.addAll(selection.FzB()); // the selected objects
        return items;
    }

    private static ICh arrangerSelection(dve window) {
        Object panel = window.lYc(aYp.class).findFirst().orElse(null); // first arranger panel below the window
        return panel instanceof aYp arranger ? arranger.BBT() : null; // its track selection
    }

    static boolean anyToHibernate(List<Object> items) {
        for (Object item : items) {
            if (item instanceof ruS) return true; // shown so a group gets the reason it is refused
            if (item instanceof xJo track && track.jhH() && Pairing.sourceId(track) == null) return true; // jhH: active
        }
        return false;
    }

    static boolean anyToWake(List<Object> items) {
        for (Object item : items) {
            if (item instanceof xJo track && (!track.jhH() || Pairing.sourceId(track) != null)) return true;
        }
        return false;
    }

    /**
     * True if a selected track is one half of a hibernated pair. Runs each time the track menu opens,
     * so only a copy, or a deactivated track that may be a source, is looked up in the project.
     */
    static boolean anyPair(List<Object> items) {
        for (Object item : items) {
            if (!(item instanceof xJo track)) continue;
            if (track.jhH() && Pairing.sourceId(track) == null) continue; // active and not a copy: not a pair
            if (Pairing.find(track).problem() == null) return true;
        }
        return false;
    }

    /** Null for a plain track; otherwise why it can't hibernate: a group, its master, an effect track, the main track. */
    static String refusal(Object item) {
        if (item instanceof ruS) return "Groups can't hibernate yet. Select the tracks inside the group.";
        if (!(item instanceof xJo track)) return "Only tracks can hibernate.";
        ruS parent = track.zjb(); // the group holding it
        if (parent == null || parent.Lqt() == track) return "Groups and the main track can't hibernate.";
        if (track.UAr()) return "Effect tracks can't hibernate."; // UAr: is an effect track
        return null;
    }

    /** Tells the user which sends didn't carry over; says nothing if they all did. */
    static void noteMissingSends(List<String> targets) {
        if (targets.isEmpty()) return;
        note("Couldn't copy the send to " + String.join(", ", targets) + ".");
    }

    /** Tells the user about sidechains tapping inside a device chain; says nothing if there are none. */
    static void noteStuck(int count, String text) {
        if (count == 0) return;
        note((count == 1 ? "A sidechain " : count + " sidechains ") + text);
    }

    /** A message in Bitwig's notification bar. */
    static void note(String text) {
        MOD.log(text);
        CHR.Grk().hUR(text);
    }
}
