package com.bitwig.mods.dragselectionfollowscontent;

import bitwig.obf.huo; // what Bitwig uses to handle selected notes and audio events, and nothing else
import bitwig.obf.iGx; // the editor's selection: its items, their bounds and its time selection
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.This;
import com.bitwig.flt.widget.core.timeline.interaction.KXR; // dragging a selection in a timeline

/**
 * Detail Editor: Drag Selection Follows Content.
 *
 * Bitwig lets a selection of notes or audio events hold a time selection wider than the events,
 * and a drag selection leaves it at the range dragged. Here, when the selection is events, the
 * time selection stays on them: from the earliest start to the latest end. Every other kind of
 * selection (clips, automation, markers and so on), and a time selection with nothing selected,
 * never gets past the check in {@link #isEvents}.
 */
public final class DragSelectionFollowsContent {
    private static final Bml.Mod MOD = Bml.mod("drag-selection-follows-content");

    private DragSelectionFollowsContent() {
    }

    /**
     * After Bitwig has worked out the selection's bounds and time selection. Runs when the
     * selection or its events change, and at the start of Duplicate.
     */
    @Hook(target = iGx.class, method = "ADn", at = At.BEFORE_RETURN, id = "follow-events")
    public static void followEvents(@This iGx selection) {
        try {
            if (!isEvents(selection)) {
                return;
            }
            double start = selection.UkU(); // the earliest start of the selected items
            double end = selection.FhD(); // the latest end of the selected items
            if (usable(start, end)) {
                selection.vpw(start); // the time selection's start
                selection.uWE(end); // the time selection's end
            }
        } catch (RuntimeException e) {
            MOD.log("couldn't update the time selection", e);
        }
    }

    /**
     * When the mouse is released after dragging a selection. Each drag step leaves the time
     * selection at the range dragged, after the update above, so the trim for a drag happens here.
     * This repeats the end of a drag step with the bounds: Bitwig's own setter, then a repaint.
     */
    @Hook(target = KXR.class, method = "cfM(LdJI;)V", at = At.BEFORE_RETURN, id = "trim-on-release")
    public static void trimOnRelease(@This KXR drag) {
        try {
            if (!(drag.qoU().Hnt().FzB() instanceof iGx selection) || !isEvents(selection)) {
                return;
            }
            selection.wZs(false); // bring the bounds up to date if they are out of date
            double start = selection.UkU();
            double end = selection.FhD();
            if (usable(start, end) && (start != selection.lYc() || end != selection.wGc())) {
                selection.cfM(start, end);
                drag.qoU().jbi(); // repaint the timeline
            }
        } catch (RuntimeException e) {
            MOD.log("couldn't trim the time selection", e);
        }
    }

    /** Whether the selection is notes, audio events or both. */
    private static boolean isEvents(iGx selection) {
        return selection.AeR() > 0 && selection.FAD() instanceof huo; // AeR: how many items
    }

    private static boolean usable(double start, double end) {
        return Double.isFinite(start) && Double.isFinite(end) && end > start;
    }
}
