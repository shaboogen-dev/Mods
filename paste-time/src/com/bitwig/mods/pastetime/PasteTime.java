package com.bitwig.mods.pastetime;

import bitwig.obf.BA; // clipboard content copied from the arranger: clips with a time span
import bitwig.obf.Bg1; // the arranger panel that runs Paste Time
import bitwig.obf.HOH; // context for Bitwig's time edits
import bitwig.obf.NYt; // other clipboard content with a time span
import bitwig.obf.ICh; // what asks for an insertion point (the panel)
import bitwig.obf.OjS; // an insertion point's context
import bitwig.obf.PUS; // a kind of time selection, with its time edits (insert, remove, duplicate time)
import bitwig.obf.QkI; // an insertion point's mode
import bitwig.obf.SEJ; // a project's global track (markers, above the tracks)
import bitwig.obf.Tgk; // an automation lane
import bitwig.obf.XuI; // paste mode
import bitwig.obf.bYE; // the panel's selection, which gives insertion points
import bitwig.obf.fFV; // a time selection in automation lanes
import bitwig.obf.ktg; // Bitwig's time editor (insert, remove, duplicate time)
import bitwig.obf.qIC; // the clipboard being pasted
import bitwig.obf.ryQ; // clipboard content copied from automation lanes
import bitwig.obf.shO; // a track's arranger lane
import bitwig.obf.uGG; // where a paste goes (an insertion point)
import bitwig.obf.uoz; // an insertion point's source
import bitwig.obf.xdo; // the automation copied to the clipboard
import bitwig.obf.yXG; // an automation insertion point's context
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.This;
import com.bitwig.base.async.lBQ; // the paste's completion callback
import com.bitwig.flt.document.core.iface.clipboard.automation.TrackEditorAutomationInsertionPoint;
import com.bitwig.flt.document.core.iface.clipboard.clip.ArrangerClipInsertionPoint;
import com.bitwig.flt.document.core.master.Ac; // where pasted clips go in time
import com.bitwig.flt.document.core.master.Dys; // what tracks a paste creates are reported to
import com.bitwig.flt.document.core.master.FEn; // how a paste maps tracks
import com.bitwig.flt.document.core.master.IOJ; // an undoable change
import com.bitwig.flt.document.core.master.IQl; // the tracks being pasted
import com.bitwig.flt.document.core.master.Mwz; // maps pasted tracks onto the destination's tracks
import com.bitwig.flt.document.core.master.SUv; // Bitwig's arranger paste
import com.bitwig.flt.document.core.master.faW; // a mapping from copied to destination tracks
import com.bitwig.flt.document.core.master.g2L; // a project
import com.bitwig.flt.document.core.master.ruS; // a group track (also the project's top group)
import com.bitwig.flt.document.core.master.zwV; // a track
import com.bitwig.ramona.core.oDc; // a document object that undoable changes belong to
import java.util.ArrayList;
import java.util.List;

/**
 * Arranger: Paste Time.
 *
 * Bitwig's Paste Time pastes without making room. This makes it insert empty time as long as the
 * clipboard, then paste there, as one Undo step.
 *
 * In clip lanes the target only sets the start time. The time goes in on exactly the tracks that were
 * copied (the whole project if it was copied), and the copied content is pasted back onto those tracks.
 * Bitwig's clipboard holds copies of the copied tracks, which keep their tracks' IDs; the paste goes to the
 * original of the lane Copy Time ran from, and is checked to map every copied track onto its original.
 * Anything about an arranger time copy that can't be matched exactly stops the paste: nothing changes.
 * In automation lanes the time goes in on the selected lanes (where Bitwig's own Paste Time does nothing).
 * A time selection is pasted at its start. Other clipboards are left to Bitwig's own paste.
 */
public final class PasteTime {
    private static final Bml.Mod MOD = Bml.mod("paste-time");
    private static final double EPSILON = 1.0e-12;
    /** The panel running Paste Time, noted at its entry for the paste inside it. */
    private static final ThreadLocal<Bg1> PANEL = new ThreadLocal<>();
    /** A clip-lane Paste Time whose time is inserted inside Bitwig's paste, where it knows the position. */
    private static final ThreadLocal<Plan> PASTING = new ThreadLocal<>();
    /** plan()'s answer when Paste Time must change nothing. */
    private static final Plan REFUSED = new Plan(null, null, List.of(), 0, 0, null, null, null);

    private PasteTime() {
    }

    @Hook(target = Bg1.class, method = "RBC", at = At.ENTRY, id = "capture-paste-time-context") // Paste Time
    public static void notePanel(@This Bg1 panel) {
        PANEL.set(panel);
        MOD.log("Paste Time started");
    }

    /**
     * Paste Time's insertion point. Bitwig gives none for Paste Time in automation lanes, so Paste Time
     * does nothing there; then the automation lane's insertion point for a plain paste is used.
     */
    @Hook(target = Bg1.class, method = "RBC", at = At.REPLACE_CALL, id = "paste-time-insertion-point",
          callOwner = bYE.class, call = "cfM(LICh;Z)LuGG;", occurrence = 1) // Paste Time; the insertion point for a paste (true: Paste Time)
    public static uGG insertionPoint(bYE selection, ICh panel, boolean pasteTime) {
        uGG point = selection.cfM(panel, pasteTime);
        if (point != null || !pasteTime) {
            return point;
        }
        try {
            if (inAutomationLanes(panel)) {
                uGG plain = selection.cfM(panel, false);
                MOD.log("Paste Time: automation lanes; plain paste insertion point "
                        + (plain == null ? "null" : plain.getClass().getName()));
                return plain;
            }
            MOD.log("Paste Time: Bitwig gives no insertion point");
        } catch (Throwable t) {
            MOD.log("Paste Time: no automation insertion point", t);
        }
        return null;
    }

    /** Whether the panel's time selection is in automation lanes (an arranger panel is its own time-edit context). */
    private static boolean inAutomationLanes(Object panel) {
        return panel instanceof HOH timeEdit && timeEdit.XO() instanceof fFV; // the kind of time selection
    }

    /** In place of Paste Time's paste: make room, then paste. */
    @Hook(target = Bg1.class, method = "RBC", at = At.REPLACE_CALL, id = "repair-paste-time-semantics",
          callOwner = uGG.class, call = "cfM", occurrence = 1) // Paste Time; the paste into an insertion point
    public static boolean pasteTime(uGG insertion, qIC clipboard, XuI mode, lBQ done) {
        Bg1 panel = PANEL.get();
        PANEL.remove();
        MOD.log("Paste Time: pasting into " + (insertion == null ? "null" : insertion.getClass().getName())
                + " from " + (clipboard == null ? "null" : clipboard.getClass().getName()));
        if (insertion == null || clipboard == null) {
            return insertion != null && insertion.cfM(clipboard, mode, done);
        }
        Plan plan;
        try {
            plan = plan(panel, insertion, clipboard);
        } catch (Throwable t) {
            MOD.log("Paste Time: couldn't work out the paste; Bitwig's own paste runs", t);
            plan = null;
        }
        if (plan == REFUSED) {
            return false;
        }
        if (plan == null) {
            return insertion.cfM(clipboard, mode, done);
        }
        IOJ change = beginChange(plan.document);
        try {
            if (plan.copiedLanes == null) {
                plan.editor.Hnt(plan.lanes, plan.start, plan.end, plan.timeEdit); // insert empty time
                return plan.insertion.cfM(clipboard, mode, done);
            }
            PASTING.set(plan); // insertWherePasted inserts the time
            boolean pasted = plan.insertion.cfM(clipboard, mode, done);
            if (PASTING.get() != null) {
                // Another paste path, or one Bitwig finishes later: it pastes as its own Paste Time does.
                MOD.log("Paste Time: Bitwig's paste returned before placing the clips; no time inserted");
            }
            return pasted;
        } catch (Throwable t) {
            // Never paste a second time once the document may have changed.
            MOD.log("Paste Time failed", t);
            return false;
        } finally {
            PASTING.remove();
            finishChange(change);
        }
    }

    /**
     * Inside Bitwig's arranger paste, where it maps the copied tracks onto the project's: check that none
     * lands anywhere but on its original. If one does, no time is inserted and the mismatch is logged (the paste can't
     * be stopped here; the checks in clipLanePlan are meant to make this not happen).
     */
    @Hook(target = SUv.class, at = At.REPLACE_CALL, id = "check-tracks-pasted-back",
          method = "cfM(Lcom/bitwig/flt/document/core/master/g2L;LshO;DDLjava/util/List;LshO;DD"
                  + "Lcom/bitwig/flt/document/core/master/CHR;Li3D;ZLBz;Lcom/bitwig/base/async/oRV;ZZ"
                  + "Lcom/bitwig/flt/document/core/master/vPI;)V", // paste clips from lanes onto a destination lane
          callOwner = Mwz.class, occurrence = 1,
          call = "cfM(Lcom/bitwig/flt/document/core/master/IQl;LshO;LshO;Lcom/bitwig/flt/document/core/master/FEn;"
                  + "Lcom/bitwig/flt/document/core/master/Dys;)Lcom/bitwig/flt/document/core/master/faW;") // map the tracks
    public static faW checkTracksPastedBack(IQl pasted, shO source, shO destination, FEn options, Dys created) {
        faW mapping = Mwz.cfM(pasted, source, destination, options, created);
        Plan plan = PASTING.get();
        if (plan == null) {
            return mapping;
        }
        try {
            for (int i = 0; i < plan.copiedLanes.size(); i++) {
                shO copiedLane = (shO) plan.copiedLanes.get(i);
                shO original = (shO) plan.lanes.get(i);
                shO to = mapping == null ? null : mapping.cfM(copiedLane); // where Bitwig pastes it
                // No lane: Bitwig pastes nothing through the mapping there (the global track's markers are
                // copied separately, onto this project's global track).
                if (to != null && to.Iwz() != original.Iwz()) { // its track
                    PASTING.remove();
                    MOD.log("Paste Time: Bitwig maps copied track " + describe(copiedLane) + " to " + describe(to)
                            + ", not its original " + describe(original) + "; no time inserted");
                    return mapping;
                }
            }
            MOD.log("Paste Time: Bitwig pastes the copied tracks onto their originals");
        } catch (Throwable t) {
            PASTING.remove();
            MOD.log("Paste Time: checking where Bitwig pastes failed; no time inserted", t);
        }
        return mapping;
    }

    /**
     * Inside Bitwig's arranger paste, after it has mapped the copied tracks and read what to paste, where
     * the clips' new position is worked out: insert the time there on the copied lanes, before the clips
     * are placed.
     */
    @Hook(target = SUv.class, at = At.REPLACE_CALL, id = "insert-where-pasted",
          method = "cfM(Lcom/bitwig/flt/document/core/master/g2L;LshO;DDLjava/util/List;LshO;DD"
                  + "Lcom/bitwig/flt/document/core/master/CHR;Li3D;ZLBz;Lcom/bitwig/base/async/oRV;ZZ"
                  + "Lcom/bitwig/flt/document/core/master/vPI;)V", // paste clips from lanes onto a destination lane
          callOwner = SUv.class, call = "cfM(DDD)Lcom/bitwig/flt/document/core/master/Ac;",
          occurrence = 1) // the clips' new position (copied start, destination start, destination end)
    public static Ac insertWherePasted(double copiedStart, double start, double end) {
        Plan plan = PASTING.get();
        if (plan != null) {
            PASTING.remove(); // once per Paste Time
            try {
                if (Math.abs(end - start) > EPSILON) {
                    MOD.log("Paste Time: Bitwig pastes into a range (" + start + ".." + end + "); no time inserted");
                } else {
                    double length = plan.end - plan.start;
                    MOD.log("Paste Time: inserting " + start + ".." + (start + length) + " in "
                            + plan.lanes.size() + " copied track(s)");
                    plan.editor.Hnt(plan.lanes, start, start + length, plan.timeEdit); // insert empty time
                }
            } catch (Throwable t) {
                MOD.log("Paste Time: inserting time failed; the paste goes ahead", t);
            }
        }
        return SUv.cfM(copiedStart, start, end);
    }

    /** What to insert and where, null to leave the paste to Bitwig, or REFUSED to change nothing. */
    private static Plan plan(Bg1 panel, uGG insertion, qIC clipboard) {
        if (panel == null) {
            return skip("no arranger panel");
        }
        if (insertion instanceof ArrangerClipInsertionPoint clips && !inAutomationLanes(panel)) {
            return clipLanePlan(clips, clipboard);
        }
        double start, selectedEnd;
        if (insertion instanceof ArrangerClipInsertionPoint clips) { // a clip insertion point in automation lanes
            start = clips.nVd(); // destination start
            selectedEnd = clips.ucQ(); // destination end
        } else if (insertion instanceof TrackEditorAutomationInsertionPoint automation) { // an automation lane
            start = automation.TtY(); // destination start
            selectedEnd = automation.fJR(); // destination end
        } else {
            return skip("not an arranger clip or automation lane: " + insertion.getClass().getName());
        }
        if (!(panel instanceof HOH own && own.XO() instanceof fFV automationLanes)) { // the kind of time selection
            return skip("an automation insertion point without an automation time selection");
        }
        double[] span = clipboardSpan(clipboard);
        if (span == null) {
            return skip("the clipboard has no time span: " + clipboard.getClass().getName());
        }
        double duration = span[1] - span[0];
        double end = start + duration;
        if (!usable(start, duration)) {
            return skip("no usable length or position (" + start + " + " + duration + ")");
        }
        uGG target = insertion;
        if (Double.isFinite(selectedEnd) && Math.abs(selectedEnd - start) > EPSILON) {
            // A time selection: paste at its start, as Bitwig's own single-position insertion points do.
            if (insertion instanceof ArrangerClipInsertionPoint clips) {
                uoz source = clips.iHb(); // source
                OjS context = clips.eLY(); // context
                QkI pointMode = clips.qoU(); // mode
                target = new ArrangerClipInsertionPoint(source, start, context, pointMode);
            } else {
                TrackEditorAutomationInsertionPoint automation = (TrackEditorAutomationInsertionPoint) insertion;
                Tgk lane = automation.eLY(); // the automation lane
                QkI pointMode = automation.iHb(); // mode
                yXG context = automation.nVd(); // context
                target = new TrackEditorAutomationInsertionPoint(lane, start, start, pointMode, context);
            }
        }
        List<?> lanes = panel.FzB(); // the selected automation lanes
        if (lanes == null || lanes.isEmpty() || !(lanes.get(0) instanceof oDc document)) {
            return skip("no target lanes");
        }
        MOD.log("Paste Time: inserting " + start + ".." + end + " in " + lanes.size() + " automation lane(s)");
        // Time edits in automation lanes, as Insert Silence there; the panel is the time-edit context.
        return new Plan(target, automationLanes, lanes, start, end, own, document, null);
    }

    /**
     * Clip lanes: paste the copied time back onto the tracks it was copied from, at the target's start.
     * Each copied lane is matched to its original by its track's ID; the paste goes to the original of the
     * lane Copy Time ran from (the clipboard's anchor), and the time is inserted inside the paste. A time
     * copy that can't be matched exactly is refused.
     */
    private static Plan clipLanePlan(ArrangerClipInsertionPoint clips, qIC clipboard) {
        if (!(arrangerClips(clipboard) instanceof BA copied)) {
            return skip("the clipboard holds no time copied in the arranger: " + clipboard.getClass().getName());
        }
        double start = clips.nVd(); // destination start
        double duration = copied.hbP() - copied.LaE(); // the copied time's end minus its start
        shO anchor = copied.IwV; // the lane Copy Time ran from
        List<?> copiedLanes = copied.qoU(); // the lanes copied
        g2L project = clips.iHb() instanceof shO here ? here.cc_() : null; // the project pasted into
        MOD.log("Paste Time: copied anchor " + describe(anchor) + " in " + describe(anchor == null ? null : anchor.cc_())
                + "; pasting into " + describe(project) + "; copied lanes " + describeAll(copiedLanes));
        if (!usable(start, duration)) {
            return refuse("no usable length or position (" + start + " + " + duration + ")");
        }
        if (anchor == null || copiedLanes == null || copiedLanes.isEmpty() || project == null) {
            return refuse("no copied lanes or no project");
        }
        List<Object> originals = new ArrayList<>();
        for (Object lane : copiedLanes) {
            shO original = lane instanceof shO copiedLane ? original(copiedLane, project) : null;
            if (original == null) {
                return refuse("copied lane " + describe(lane) + " has no original in this project"
                        + " (deleted, copied in another project, or not a track's lane)");
            }
            originals.add(original);
        }
        shO anchorOriginal = original(anchor, project);
        if (anchorOriginal == null) {
            return refuse("the lane Copy Time ran from has no original in this project: " + describe(anchor));
        }
        HOH timeEdit = HOH.QSH(); // a time-edit context
        if (timeEdit == null) {
            return refuse("no time-edit context");
        }
        OjS context = clips.eLY(); // context
        QkI pointMode = clips.qoU(); // mode
        uGG target = new ArrangerClipInsertionPoint(anchorOriginal, start, context, pointMode);
        MOD.log("Paste Time: pasting " + originals.size() + " copied track(s) back onto " + describeAll(originals)
                + " at " + start);
        // Time edits in clip lanes.
        return new Plan(target, ktg.PD3(), originals, start, start + duration, timeEdit, anchorOriginal,
                copiedLanes);
    }

    /**
     * The project's lane a copied lane came from, or null: the lane of the track with the same ID anywhere
     * in the project; for a copy of the whole project, the project's global track (markers, above the
     * tracks) or its top group.
     */
    private static shO original(shO copiedLane, g2L project) {
        zwV track = copiedLane.Iwz(); // its track
        if (track == null) {
            return null;
        }
        if (track instanceof SEJ global) { // a global track: not inside the top group, one per project
            g2L copiedFrom = track.cc_(); // the copy's project
            if ((copiedFrom != null && copiedFrom.tHK() != global) || copiedLane != global.bU_()) { // its global track; its lane
                return null;
            }
            return project.tHK().bU_(); // the project's global track's lane
        }
        boolean groupLane = track instanceof ruS group && copiedLane == group.ef_(); // a group's own lane
        if (!groupLane && copiedLane != track.bU_()) { // the track's arranger lane
            return null;
        }
        ruS top = project.spd(); // the project's top group
        g2L copiedFrom = track.cc_(); // the copy's project (the clipboard's own, or this one)
        // By its ID: the top group itself (a whole-project copy; TMF searches only inside a group) or a track in it.
        boolean wholeProject = (copiedFrom != null && track == copiedFrom.spd()) || top.WLh().equals(track.WLh());
        zwV found = wholeProject ? top : top.TMF(track.WLh());
        if (found == null) {
            return null;
        }
        if (groupLane) {
            return found instanceof ruS group ? group.ef_() : null;
        }
        return found.bU_();
    }

    private static boolean usable(double start, double duration) {
        return Double.isFinite(duration) && duration > EPSILON && Double.isFinite(start)
                && Double.isFinite(start + duration);
    }

    private static Plan skip(String reason) {
        MOD.log("Paste Time: Bitwig's own paste runs (" + reason + ")");
        return null;
    }

    private static Plan refuse(String reason) {
        MOD.log("Paste Time: nothing pasted or inserted (" + reason + ")");
        return REFUSED;
    }

    /** A Bitwig object for the log: its class and identity, and its track's identity and ID for a lane. */
    private static String describe(Object object) {
        if (object == null) {
            return "null";
        }
        String text = object.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(object));
        try {
            if (object instanceof shO lane && lane.Iwz() instanceof zwV track) { // its track
                text += "(track " + track.getClass().getName() + "@"
                        + Integer.toHexString(System.identityHashCode(track)) + " " + track.WLh() + ")"; // its ID
            }
        } catch (Throwable t) {
            text += "(no track: " + t + ")";
        }
        return text;
    }

    private static String describeAll(List<?> objects) {
        if (objects == null) {
            return "null";
        }
        List<String> texts = new ArrayList<>();
        for (Object object : objects) {
            texts.add(describe(object));
        }
        return texts.toString();
    }

    /** The clipboard's clips copied from the arranger, or null if it has none. */
    private static BA arrangerClips(qIC clipboard) {
        if (clipboard instanceof BA clips) {
            return clips;
        }
        try {
            return clipboard.Hnt(BA.class) instanceof BA clips ? clips : null; // its content of that type
        } catch (Throwable t) {
            MOD.log("Paste Time: reading the clipboard's arranger content failed", t);
            return null;
        }
    }

    /** The clipboard's start and end, or null if it has none. */
    private static double[] clipboardSpan(qIC clipboard) {
        if (arrangerClips(clipboard) instanceof BA clips) {
            return new double[] {clips.LaE(), clips.hbP()}; // start, end
        }
        if (clipboard instanceof ryQ automation) {
            xdo copied = automation.Hnt; // the copied automation
            return copied == null ? null : new double[] {0, copied.TMF()}; // its length
        }
        if (clipboard.Hnt(NYt.class) instanceof NYt other) {
            return new double[] {other.Grk(), other.tp2()}; // start, end
        }
        return null;
    }

    /** Starts one Undo step named "Paste Time" around the insert and the paste; null if it can't. */
    private static IOJ beginChange(oDc document) {
        try {
            IOJ change = new IOJ();
            return change.cfM(document, "Paste Time") ? change : null; // begin
        } catch (Throwable t) {
            MOD.log("Paste Time: no single Undo step; pasting anyway", t);
            return null;
        }
    }

    private static void finishChange(IOJ change) {
        if (change == null) {
            return;
        }
        try {
            change.MRY(); // finish
        } catch (Throwable t) {
            MOD.log("Paste Time: finishing the Undo step failed", t);
        }
    }

    /**
     * lanes: where the time goes in. copiedLanes: the clipboard's lanes, lanes' copies in the same order,
     * when the time goes in inside Bitwig's paste (clip lanes); null when it goes in before it (automation lanes).
     */
    private record Plan(uGG insertion, PUS editor, List<?> lanes, double start, double end, HOH timeEdit,
                        oDc document, List<?> copiedLanes) {
    }
}
