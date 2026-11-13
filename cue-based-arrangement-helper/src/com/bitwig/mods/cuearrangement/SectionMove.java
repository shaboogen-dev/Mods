package com.bitwig.mods.cuearrangement;

import bitwig.obf.Bz;  // Bitwig's context for time-range copies
import bitwig.obf.DzC; // an arranger cue marker
import bitwig.obf.HOH; // context for Bitwig's time edits
import bitwig.obf.PSn; // the collection a cue marker lives in, ordered by time
import bitwig.obf.SEJ; // a project's global track (markers, above the tracks)
import bitwig.obf.YvI; // a timed event (cue markers are ones)
import bitwig.obf.bih; // the arranger's timeline context
import bitwig.obf.cFN; // a timeline's time-range context
import bitwig.obf.i3D; // how a time-range copy treats the destination
import bitwig.obf.ktg; // Bitwig's time editor (insert, remove, duplicate time)
import bitwig.obf.shO; // a track's arranger lane
import bml.api.Bml;
import com.bitwig.base.async.oRV; // the completion argument of a time-range copy
import com.bitwig.flt.document.core.master.CHR; // optional extra argument of a time-range copy
import com.bitwig.flt.document.core.master.IOJ; // an undoable change
import com.bitwig.flt.document.core.master.SUv; // Bitwig's arranger time-range copy
import com.bitwig.flt.document.core.master.g2L; // a project
import com.bitwig.flt.document.core.master.zwV; // a track
import com.bitwig.ramona.core.oDc; // a document object that undoable changes belong to

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The cue markers of the current project and the section move itself.
 *
 * A cue marker starts a section that ends at the next cue marker (the last one at the end of the
 * arrangement). Moving a section to another cue boundary is done with Bitwig's own operations, as one
 * Undo step: insert empty time at the destination, move the cue marker that sat on that boundary to
 * the far edge of the gap, copy the section's track content into the gap (Bitwig's Duplicate Time
 * copy), move the section's own cue marker into the gap, then delete the section's old time.
 */
final class SectionMove {
    static final double EPS = 1.0e-9;
    private static final Bml.Mod MOD = Bml.mod("cue-based-arrangement-helper");

    private static volatile bih timelineContext;

    private SectionMove() {}

    static void timelineContextCreated(bih context) {
        if (context != null) timelineContext = context;
    }

    // ---------- Cue list ----------

    /**
     * The cue markers of the arrangement a cue belongs to, by time: the events of the cue's own
     * collection (the project's cue markers), read when needed, so added and deleted cue markers are
     * always current and no panel needs to have shown them.
     */
    static List<DzC> cuesSorted(DzC anyCue) {
        ArrayList<DzC> list = new ArrayList<>();
        PSn collection = anyCue == null ? null : anyCue.zpW(); // its collection
        if (collection == null) return list;
        for (Object event : collection.cfM()) { // its events
            if (event instanceof DzC cue) list.add(cue);
        }
        list.sort(Comparator.comparingDouble(c -> {
            try { return position(c); } catch (Throwable t) { return Double.POSITIVE_INFINITY; }
        }));
        // Equal positions are kept apart on purpose: a collision would show as two cues, not one.
        return list;
    }

    /** The cue marker's own time. */
    static double position(DzC cue) {
        return cue.x3t();
    }

    /** The end of the cue's section: the next cue's time, or the end of the arrangement for the last cue. */
    static double sectionEnd(DzC cue) {
        return cue.QWG();
    }

    static String name(DzC cue) {
        try { return cue.cfM(); } catch (Throwable t) { return String.valueOf(cue); }
    }

    static int indexOf(List<DzC> list, Object target) {
        for (int i = 0; i < list.size(); i++) if (list.get(i) == target) return i;
        return -1;
    }

    /**
     * Sets a cue marker's time the way Bitwig's own cue drag does: out of its collection, new time, back
     * in. Setting the time alone would leave the collection's order stale when the cue passes another one.
     */
    private static void setPosition(DzC cue, double position) {
        PSn parent = cue.zpW(); // its collection
        if (parent == null) {
            cue.zpW(position);
            return;
        }
        boolean removed = false;
        try {
            parent.cfM((YvI) cue, false); // take out, keeping it alive
            removed = true;
            cue.zpW(position);
        } finally {
            if (removed) parent.FzB((YvI) cue); // put back
        }
    }

    // ---------- The move ----------

    /** Moves the cue's section so that it starts where boundaryCue is now. */
    static void moveToBoundary(DzC cue, DzC boundaryCue) {
        move(cue, boundaryCue, null);
    }

    /** Moves the cue's section to the end of the arrangement. */
    static void moveToEnd(DzC cue) {
        List<DzC> cues = cuesSorted(cue);
        if (cues.isEmpty()) throw new IllegalStateException("no cue markers");
        move(cue, null, sectionEnd(cues.get(cues.size() - 1)));
    }

    private static void move(DzC cue, DzC boundaryCue, Double boundaryOverride) {
        g2L project = cue.hUR(g2L.class) instanceof g2L p ? p : null; // the project it belongs to
        bih timeline = timelineContext;
        if (project == null) throw new IllegalStateException("the cue marker's project can't be found");
        if (timeline == null) throw new IllegalStateException("the arranger's timeline isn't available yet");

        List<DzC> cues = cuesSorted(cue);
        int idx = indexOf(cues, cue);
        int boundaryIdx = boundaryCue == null ? cues.size() : indexOf(cues, boundaryCue);
        if (idx < 0 || (boundaryCue != null && boundaryIdx < 0))
            throw new IllegalStateException("the cue or the boundary isn't in the current cue list");
        if (cues.size() < 2) throw new IllegalStateException("needs at least two cue markers");

        double sourceStart = position(cues.get(idx));
        double sourceEnd = sectionEnd(cues.get(idx));
        double duration = sourceEnd - sourceStart;
        if (!Double.isFinite(duration) || duration <= EPS) throw new IllegalStateException("invalid section length " + duration);
        double boundary = boundaryOverride != null ? boundaryOverride : position(boundaryCue);
        if (idx == cues.size() - 1 && boundary > sourceStart + EPS)
            throw new IllegalStateException("the last section can move earlier but not later");

        // A boundary at the section's own start or end changes nothing.
        if (Math.abs(boundary - sourceStart) <= EPS || Math.abs(boundary - sourceEnd) <= EPS) return;
        boolean earlier = boundary < sourceStart;
        boolean later = boundary > sourceEnd;
        if (!earlier && !later) throw new IllegalStateException("the boundary lies inside the section");

        Lanes lanes = lanes(project, timeline);
        ArrayList<shO> copyLanes = new ArrayList<>(lanes.all);
        copyLanes.removeIf(x -> x == lanes.global); // the copy takes the tracks only; cue markers move separately
        if (copyLanes.isEmpty()) throw new IllegalStateException("no track lanes besides the global one");

        // Inserting before the section shifts it right by its length.
        double movedStart = earlier ? sourceStart + duration : sourceStart;
        double movedEnd = earlier ? sourceEnd + duration : sourceEnd;

        IOJ change = beginChange(lanes.global, "Move Song Section");
        try {
            lanes.editor.Hnt(lanes.all, boundary, boundary + duration, lanes.context); // insert empty time

            double gapEnd = boundary + duration;
            if (boundaryCue != null) {
                double after = position(boundaryCue);
                if (Math.abs(after - boundary) <= EPS) {
                    setPosition(boundaryCue, gapEnd);
                    after = position(boundaryCue);
                }
                if (Math.abs(after - gapEnd) > EPS)
                    throw new IllegalStateException("the boundary cue didn't land at the gap's end: expected " + gapEnd + ", got " + after);
            }

            copyTracks(project, lanes.anchor, boundary, gapEnd, copyLanes, lanes.anchor, movedStart, movedEnd, lanes.copyMode, lanes.aux);

            setPosition(cue, boundary);
            double now = position(cue);
            if (Math.abs(now - boundary) > EPS)
                throw new IllegalStateException("the section's cue didn't land at the gap: expected " + boundary + ", got " + now);

            lanes.editor.FzB(lanes.all, movedStart, movedEnd, lanes.context); // delete the old time
        } finally {
            finishChange(change);
        }
        MOD.log("Moved section " + name(cue) + " (" + sourceStart + ".." + sourceEnd + ") to "
                + (boundaryCue == null ? "the end" : "before " + name(boundaryCue)));
    }

    /** The project's lanes for the time edits, and the objects Bitwig's time edits need. */
    private record Lanes(shO global, shO anchor, List<shO> all, HOH context, Bz aux, ktg editor, i3D copyMode) {}

    private static Lanes lanes(g2L project, bih timeline) {
        SEJ globalTrack = project.tHK();
        if (globalTrack == null) throw new IllegalStateException("the project has no global track");
        zwV globalTrackLanes = globalTrack.ELS();
        if (globalTrackLanes == null) throw new IllegalStateException("the global track has no track tree");
        shO global = globalTrackLanes.bU_();
        if (global == null) throw new IllegalStateException("the global track has no arranger lane");

        cFN range = timeline.FzB();
        if (!(range instanceof HOH context)) throw new IllegalStateException("the timeline's range context isn't a time-edit context: " + range);
        Bz aux = context.gm_();
        if (aux == null) throw new IllegalStateException("the time-edit context has no copy context");
        ktg editor = ktg.PD3();
        if (editor == null) throw new IllegalStateException("no time editor");
        i3D copyMode = i3D.MRY; // copy

        // Every track's lane, from the project's own traversal of its tracks (true: with the arranger's tree).
        ArrayList<shO> all = new ArrayList<>();
        IdentityHashMap<shO, Boolean> seen = new IdentityHashMap<>();
        Stream<?> graph = project.uWE(true);
        try {
            Iterator<?> it = graph.iterator();
            while (it.hasNext()) {
                Object node = it.next();
                shO lane = null;
                if (node instanceof shO l) lane = l;
                else if (node instanceof zwV track) {
                    try { lane = track.bU_(); } catch (Throwable ignored) { lane = null; }
                }
                if (lane != null && seen.put(lane, Boolean.TRUE) == null) all.add(lane);
            }
        } finally {
            graph.close();
        }
        // The global lane takes part too, so cue markers move with the time edits.
        if (seen.put(global, Boolean.TRUE) == null) all.add(global);
        if (all.size() <= 1) throw new IllegalStateException("found no track lanes; nothing changed");

        shO anchor = null;
        for (shO lane : all) {
            if (lane != global) { anchor = lane; break; }
        }
        if (anchor == null) throw new IllegalStateException("no track lane to anchor the copy");
        // As in Bitwig's Duplicate Time: the first lane is both the copy's source and destination anchor,
        // and the list holds all lanes.
        all.remove(anchor);
        all.add(0, anchor);
        return new Lanes(global, anchor, all, context, aux, editor, copyMode);
    }

    /** Bitwig's Duplicate Time copy, with the same arguments: destination first, then source. */
    private static void copyTracks(g2L project, shO destination, double destinationStart, double destinationEnd,
                                   List<shO> sources, shO source, double sourceStart, double sourceEnd,
                                   i3D mode, Bz aux) {
        oRV done = SUv.cfM(aux, false);
        SUv.cfM(project, destination, destinationStart, destinationEnd, sources, source, sourceStart, sourceEnd,
                (CHR) null, mode, false, true, aux, done);
    }

    // ---------- Undo ----------

    private static IOJ beginChange(oDc document, String label) {
        try {
            IOJ change = new IOJ();
            return change.cfM(document, label) ? change : null; // begin
        } catch (Throwable t) {
            MOD.log("No single Undo step; moving anyway", t);
            return null;
        }
    }

    private static void finishChange(IOJ change) {
        if (change == null) return;
        try {
            change.MRY(); // finish
        } catch (Throwable t) {
            MOD.log("Finishing the Undo step failed", t);
        }
    }
}
