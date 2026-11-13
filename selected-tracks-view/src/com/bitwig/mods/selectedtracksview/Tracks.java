package com.bitwig.mods.selectedtracksview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import bitwig.obf.ICh; // a selection of document objects
import bitwig.obf.aBW; // a main window's project view
import bitwig.obf.big; // the view's selection object a selection menu opens on
import bitwig.obf.dve; // a widget
import bitwig.obf.iCC; // a selection of channels (with its current one)
import bml.actions.ActionContext;
import com.bitwig.flt.document.core.iface.RYd; // a channel: a track, group, effect track or the master
import com.bitwig.flt.document.core.iface.VBr; // a group's track lists
import com.bitwig.flt.document.core.iface.iYN; // a channel's name, colour and group
import com.bitwig.flt.document.core.iface.kWY; // a group of tracks (the project's root is one)
import com.bitwig.flt.document.core.iface.pHd; // a project
import com.bitwig.ramona.core.brd; // a model object's list, observable

/** Which window, which project and which tracks an action applies to, in Bitwig's track order. */
final class Tracks {
    private Tracks() {
    }

    /**
     * The window's project view: the clicked widget's (a right-click menu), or the one inside the
     * project window a shortcut or the Commander ran in.
     */
    static aBW window(ActionContext context) {
        if (!(context.widget() instanceof dve widget)) return null;
        if (widget instanceof aBW window) return window;
        Object above = widget.TMF(aBW.class); // the nearest one above the widget
        if (above instanceof aBW window) return window;
        Object below = widget.lYc(aBW.class).findFirst().orElse(null); // the first one below the project window
        return below instanceof aBW window ? window : null;
    }

    /** The tracks of the right-click menu's selection (empty for a shortcut or the Commander). */
    static List<RYd> ofMenu(ActionContext context) {
        if (!(context.selection() instanceof big view)) return List.of();
        ICh selection = view.UNj(); // the selection behind the view
        return selection != null ? channels(selection.FzB()) : List.of();
    }

    /** The project's track selection (the arranger's, linked to the mixer's), as Mix Map reads it. */
    static List<RYd> ofProject(pHd project) {
        iCC selection = project.wIw().E3(); // wIw(): the project's view settings; E3(): the track selection
        return selection != null ? channels(selection.FzB()) : List.of();
    }

    /** The selected tracks: the menu's selection, or the project's for a shortcut or the Commander. */
    static List<RYd> selected(ActionContext context, pHd project) {
        List<RYd> tracks = ofMenu(context);
        return tracks.isEmpty() ? ofProject(project) : tracks;
    }

    /** The channels among the items that have a device chain, each once. */
    private static List<RYd> channels(List<?> items) {
        List<RYd> out = new ArrayList<>();
        Set<RYd> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object item : items) {
            if (item instanceof RYd channel && channel.Ucl() != null && seen.add(channel)) out.add(channel); // Ucl(): its device chain
        }
        return out;
    }

    /**
     * The tracks in Bitwig's order: the project's tracks top to bottom, each group followed by its
     * own tracks, then the effect tracks and the rest of the root's lists. A track not found there
     * (the master, if it isn't in those lists) keeps its place in the selection, after them.
     */
    static List<RYd> inProjectOrder(pHd project, List<RYd> tracks) {
        Map<Object, Integer> position = new IdentityHashMap<>();
        walk(project.VpW(), position, 0); // VpW(): the project's root group
        List<RYd> known = new ArrayList<>(), unknown = new ArrayList<>();
        for (RYd track : tracks) (position.containsKey(track) ? known : unknown).add(track);
        known.sort((a, b) -> Integer.compare(position.get(a), position.get(b)));
        known.addAll(unknown);
        return known;
    }

    private static void walk(VBr group, Map<Object, Integer> position, int depth) {
        if (group == null || depth > 64) return;
        for (List<?> list : List.of(group.uva(), group.sut(), group.wYK())) { // uva(): its tracks; sut(): its effect tracks
            for (Object item : list) {
                if (position.containsKey(item)) continue;
                position.put(item, position.size());
                if (item instanceof VBr inner) walk(inner, position, depth + 1); // a group's own tracks
            }
        }
    }

    /**
     * The observable track lists a track's removal shows up in: its group's, and every group's
     * above it up to the project's root (deleting a group removes its tracks with it).
     */
    static void listsAbove(RYd track, kWY root, Set<brd> into) {
        Object group = track.QNk(); // the group holding it
        int depth = 0;
        while (group instanceof VBr lists && depth++ < 64) {
            addLists(lists, into);
            group = group instanceof iYN channel ? channel.QNk() : null;
        }
        if (root != null) addLists(root, into);
    }

    private static void addLists(VBr group, Set<brd> into) {
        for (brd list : new brd[] {group.fFM(), group.jG(), group.Coi()}) { // its tracks, effect tracks and the third list
            if (list != null) into.add(list);
        }
    }

    /** Whether the track is still in the project: Bitwig clears a track's project when it is deleted. */
    static boolean present(RYd track, pHd project) {
        return track.apV() == project; // apV(): the project it belongs to, null once it is removed
    }
}
