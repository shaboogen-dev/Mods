package com.bitwig.mods.shiftselectacrossgroups;

import bitwig.obf.Bg1; // a selection (here, of tracks)
import bitwig.obf.EvG; // the selection policy of tracks: how a track selection moves and fills
import bitwig.obf.SYw; // a selection item that stands for one track
import bitwig.obf.bYE; // a selection item
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import com.bitwig.flt.document.core.master.ruS; // a group of tracks; the project's top level is one too
import com.bitwig.flt.document.core.master.zwV; // a track
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Arranger: Shift-Select Across Groups.
 *
 * Bitwig fills a track range only when both ends have the same parent group, and then only with
 * the tracks directly in that group. Here a range takes every visible track between its ends in
 * arranger order, so the ends may sit in different groups, and the tracks of expanded groups
 * inside the range are included.
 *
 * Two hooks on the policy's range method stand for "fill the range here and return early". The
 * first fills the range. The second answers the method's first lookup, of the first end's parent,
 * with null after a fill, and that makes Bitwig's own code go straight to its return. When the
 * first hook can't fill the range, Bitwig's own range runs unchanged.
 */
public final class ShiftSelectAcrossGroups {
    private static final Bml.Mod MOD = Bml.mod("shift-select-across-groups");

    /** Set between the two hooks, which run one right after the other on the same thread. */
    private static final ThreadLocal<Boolean> FILLED = new ThreadLocal<>();

    private ShiftSelectAcrossGroups() {
    }

    /** Before Bitwig's range: selection, the two ends, and the collection to fill. */
    @Hook(target = EvG.class, method = "cfM(LBg1;LbYE;LbYE;Ljava/util/Collection;)V", at = At.ENTRY, id = "fill-range")
    public static void fillRange(@Arg(0) Bg1 selection, @Arg(1) bYE from, @Arg(2) bYE to, @Arg(3) Collection<Object> out) {
        FILLED.remove(); // a mark left by a range that never reached the lookup must not skip this one
        try {
            List<Object> range = visibleRange(selection, from, to);
            if (range != null) {
                out.addAll(range);
                FILLED.set(Boolean.TRUE);
            }
        } catch (RuntimeException failure) {
            MOD.log("Couldn't fill the range; using Bitwig's own", failure);
        }
    }

    /** Bitwig's lookup of the first end's parent group; null makes its range method return. */
    @Hook(target = EvG.class, method = "cfM(LBg1;LbYE;LbYE;Ljava/util/Collection;)V", at = At.REPLACE_CALL,
          callOwner = EvG.class, call = "cfM(LbYE;)Lcom/bitwig/flt/document/core/master/ruS;", occurrence = 1,
          id = "skip-bitwig-range")
    public static ruS firstEndParent(EvG policy, bYE from) {
        if (FILLED.get() != null) {
            FILLED.remove();
            return null;
        }
        return policy.cfM(from); // the parent group of a selection item
    }

    /** The tracks from one end to the other in arranger order; null when an end isn't a visible track. */
    private static List<Object> visibleRange(Bg1 selection, bYE from, bYE to) {
        if (!(from instanceof SYw first) || !(to instanceof SYw last)) return null;
        if (!(first.lYc() instanceof zwV) || !(last.lYc() instanceof zwV)) return null; // lYc: the item's track

        // gH_: the project; EV: its track list; xlY: the top-level group. FzB(true, false): the visible
        // tracks, going into expanded groups, with no entries for empty places in the list. It is the
        // list Bitwig's own next and previous track navigation walks.
        ruS root = selection.gH_().EV().xlY();
        List<?> visible = root.FzB(true, false);
        int a = visible.indexOf(from);
        int b = visible.indexOf(to);
        if (a < 0 || b < 0) return null;

        List<Object> range = new ArrayList<>();
        for (int i = Math.min(a, b); i <= Math.max(a, b); i++) {
            if (visible.get(i) instanceof SYw item && item.lYc() instanceof zwV track) range.add(track);
        }
        return range;
    }
}
