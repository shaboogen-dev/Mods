package com.bitwig.mods.trackcollapsekeys;

import bitwig.obf.ICh; // a selection of document objects
import bitwig.obf.aFq; // the arranger's track list widget
import bml.api.Bml;
import com.bitwig.flt.document.core.iface.WSA; // a track
import com.bitwig.flt.document.core.iface.kWY; // a group track
import com.bitwig.ramona.core.lBQ; // a boolean value of a document object

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Collapse Track and Expand Track act on the selected tracks while the arranger's track list has
 * focus. A group folds or unfolds; any other track goes to the smallest row height and back to the
 * height it had.
 */
public final class TrackCollapseKeys {
    private static final Bml.Mod MOD = Bml.mod("track-collapse-keys");

    /** The smallest arranger row, in Bitwig's height units. */
    private static final int MINIMUM = 23;
    /** Two rows, name and volume: the height new tracks show. Used when no earlier height is known. */
    private static final int DEFAULT = 46;
    /** The height to return to, per track, until Bitwig quits. The project keeps only the current height. */
    private static final Map<WSA, Integer> SAVED = new WeakHashMap<>();

    private TrackCollapseKeys() {
    }

    static void apply(aFq list, boolean expand) {
        try {
            ICh selection = list.BDm().BBT(); // its arranger panel's track selection
            if (selection == null) return;
            for (Object item : new ArrayList<Object>(selection.FzB())) { // the selected objects
                if (item instanceof kWY group) fold(group, expand);
                else if (item instanceof WSA track) resize(track, expand);
            }
        } catch (RuntimeException failure) {
            MOD.log("could not change the selected tracks", failure);
        }
    }

    private static void fold(kWY group, boolean expand) {
        lBQ expanded = group.nfq().FzB(); // the setting the group's folder button drives
        if (expanded != null && expanded.getBoolValue() != expand) expanded.setBoolValue(expand);
    }

    private static void resize(WSA track, boolean expand) {
        int current = track.tp2().fFM().Ucl(); // the track's current row height
        if (!expand) {
            if (current <= MINIMUM) return;
            SAVED.put(track, current);
            track.MRY(MINIMUM, false); // set the row height
        } else {
            Integer saved = SAVED.remove(track);
            if (current <= MINIMUM) track.MRY(saved != null ? saved : DEFAULT, false);
        }
    }
}
