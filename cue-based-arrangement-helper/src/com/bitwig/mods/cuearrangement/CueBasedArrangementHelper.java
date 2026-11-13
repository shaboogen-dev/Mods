package com.bitwig.mods.cuearrangement;

import bitwig.obf.AHG; // a cue marker, as the overview holds it
import bitwig.obf.DzC; // an arranger cue marker
import bitwig.obf.bih; // the arranger's timeline context
import bitwig.obf.cJK; // the Project Panel's cue marker section (a list)
import bitwig.obf.cKU; // a list's row pointer action (press, drag, release on rows)
import bitwig.obf.cLj; // a list row
import bitwig.obf.cLm; // a list
import bitwig.obf.dIR; // a pointer action that can turn into a drag and drop
import bitwig.obf.dJF; // a pointer event
import bitwig.obf.dJH; // pointer pressed
import bitwig.obf.dJI; // pointer released
import bitwig.obf.dJM; // pointer moved
import bitwig.obf.dUt; // a pointer action with press and release
import bitwig.obf.dpl; // the window's pointer dispatcher
import bitwig.obf.drX; // a pointer gesture
import bitwig.obf.dve; // a widget
import bitwig.obf.enQ; // widget coordinate conversions
import bitwig.obf.epa; // the pointer buttons held
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.Return;
import bml.api.This;
import com.bitwig.flt.widget.core.timeline.qc1; // the arranger overview (cue sections above the timeline)
import com.bitwig.flt.widget.core.timeline.zLm; // the overview's Alt gesture on a cue section
import com.bitwig.graphics.SYo; // a widget's graphics
import com.bitwig.ramona.core.OLR; // a list row's model key

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

/**
 * Reorders cue-defined sections: by dragging a cue row in the Project Panel's cue marker list, or by
 * Alt-dragging a section in the arranger overview. The move is SectionMove's.
 */
public final class CueBasedArrangementHelper {
    private static final Bml.Mod MOD = Bml.mod("cue-based-arrangement-helper");
    private static final double DRAG_THRESHOLD_PX = 8.0;
    private static final double DROP_GAP_HOLD_PX = 8.0;

    private CueBasedArrangementHelper() {}

    // ---------- The arranger's timeline context (for the time edits) ----------

    @Hook(target = bih.class, method = "<init>", at = At.BEFORE_RETURN, id = "capture-arrangement-helper-timeline-context")
    public static void captureTimelineContext(@This bih context) {
        SectionMove.timelineContextCreated(context);
    }

    // ---------- Project Panel: dragging a cue row ----------

    private record DragState(DzC sourceCue, double startY) {}

    /** The insertion slot shown while dragging; slot = the gap before cue n (n = count: after the last). */
    private record DropPreview(cLm list, cLj row, DzC targetCue, DzC indicatorCue, boolean indicatorBottom,
                               int slotIndex, int finalIndex, double boundaryY) {}

    private record DropResolution(cLj targetRow, DzC targetCue, boolean before, int slotIndex, int finalIndex,
                                  double boundaryY, DzC boundaryCue, boolean noop,
                                  cLj indicatorRow, DzC indicatorCue, boolean indicatorBottom) {}

    private static final Map<Object, DragState> DRAGS = Collections.synchronizedMap(new IdentityHashMap<>());
    private static volatile DropPreview dropPreview;
    /** Set at each pointer motion: whether this motion belongs to a cue row press (see allowNativeRowDrag). */
    private static boolean keepRowAction;

    @Hook(target = dUt.class, method = "cfM(LdJH;)V", at = At.ENTRY, id = "arrangement-helper-row-pointer-begin") // pointer pressed
    public static void pointerBegin(@This dUt action, @Arg(0) dJH event) {
        if (!isCueListAction(action) || event == null) return;
        try {
            clearDropPreview();
            double x = event.hbP(), y = event.Grk(); // local x, y
            DzC cue = rowCue(((cKU) action).hUR(x, y)); // the row hit
            if (cue == null) return;
            DRAGS.put(action, new DragState(cue, y));
        } catch (Throwable t) {
            MOD.log("Starting a cue row drag failed", t);
        }
    }

    /**
     * A row press normally turns into Bitwig's drag and drop once the pointer moves far enough, and the
     * press itself then never sees the release. For the cue list the press is kept until the release:
     * at each motion the insertion marker follows, and the button check that would start the drag and
     * drop says no (allowNativeRowDrag).
     */
    @Hook(target = dIR.class, method = "cfM(LdJM;)V", at = At.ENTRY, id = "arrangement-helper-cue-row-pointer-motion") // pointer moved
    public static void pointerMotion(@This dIR action, @Arg(0) dJM motion) {
        keepRowAction = isCueListAction(action);
        if (keepRowAction) updateDropPreview(action, motion);
    }

    @Hook(target = dIR.class, method = "cfM(LdJM;)V", at = At.REPLACE_CALL, id = "arrangement-helper-keep-cue-row-pointer-action",
          callOwner = epa.class, call = "cfM", occurrence = 1) // pointer moved; are the buttons held (to start a drag and drop)
    public static boolean allowNativeRowDrag(epa buttons) {
        return !keepRowAction && buttons.cfM();
    }

    @Hook(target = dUt.class, method = "cfM(LdJI;)V", at = At.BEFORE_RETURN, id = "arrangement-helper-row-pointer-end") // pointer released
    public static void pointerEnd(@This dUt action, @Arg(0) dJI event) {
        DragState st = DRAGS.remove(action);
        if (st == null || event == null) {
            clearDropPreview();
            return;
        }
        try {
            double x = event.hbP(), y = event.Grk();
            if (Math.abs(y - st.startY) < DRAG_THRESHOLD_PX) { // a click
                clearDropPreview();
                return;
            }
            DropResolution drop = resolveDrop((cKU) action, st.sourceCue, x, y);
            clearDropPreview();
            if (drop == null || drop.noop) return;
            if (drop.boundaryCue == null) SectionMove.moveToEnd(st.sourceCue);
            else SectionMove.moveToBoundary(st.sourceCue, drop.boundaryCue);
        } catch (Throwable t) {
            clearDropPreview();
            MOD.log("Moving the section failed", t);
        }
    }

    @Hook(target = cJK.class, method = "Hnt(Lcom/bitwig/graphics/SYo;)V", at = At.OVERRIDE_AFTER_SUPER,
          id = "arrangement-helper-cue-list-local-insertion-indicator") // paint
    public static void paintCueListIndicator(@This cJK list, @Arg(0) SYo g) {
        DropPreview preview = dropPreview;
        if (preview == null || g == null || preview.list != list) return;
        ArrangementIndicator.paintList(list, g, preview.boundaryY);
    }

    /** The cue list's row action, or null for any other action. */
    private static cLm actionList(dIR action) {
        if (action == null || action.getClass() != cKU.class) return null;
        try { return ((cKU) action).hbP(); } catch (Throwable t) { return null; }
    }

    private static boolean isCueListAction(dIR action) {
        cLm list = actionList(action);
        return list != null && list.getClass() == cJK.class;
    }

    private static DzC rowCue(cLj row) {
        if (row == null) return null;
        return row.Bpn() instanceof DzC cue ? cue : null; // the row's key
    }

    private static DropResolution resolveDrop(cKU action, DzC sourceCue, double x, double y) {
        cLj targetRow = action.hUR(x, y);
        DzC targetCue = rowCue(targetRow);
        if (targetCue == null) {
            // Between two rows the hit test finds no row. Keep the slot already shown while the pointer is
            // still close to it, so the marker doesn't flicker; anywhere else isn't a drop.
            return resolveSeparatorGap(action, sourceCue, x, y);
        }

        List<DzC> cues = SectionMove.cuesSorted(sourceCue);
        int sourceIdx = SectionMove.indexOf(cues, sourceCue);
        int targetIdx = SectionMove.indexOf(cues, targetCue);
        if (sourceIdx < 0 || targetIdx < 0) throw new IllegalStateException("the dragged or the target cue isn't in the cue list");

        cLm list = action.hbP();
        double rowHeight = targetRow.yiX();
        if (rowHeight <= 0.0) throw new IllegalStateException("target row has height " + rowHeight);

        // The pointer is in the list's coordinates; the row's upper or lower half decides before/after.
        double localY = enQ.MRY((dve) list, y, targetRow); // y from the list into the row
        boolean before = localY < rowHeight * 0.5;

        // One slot per gap: after row n and before row n+1 are the same place.
        int rawSlot = targetIdx + (before ? 0 : 1);
        int finalIndex = rawSlot > sourceIdx ? rawSlot - 1 : rawSlot;
        boolean noop = finalIndex == sourceIdx;

        double rowTop = enQ.MRY((dve) targetRow, 0.0, list);
        double boundaryY = rowTop + (before ? 0.0 : rowHeight);

        // The row that owns the slot: the row below it, or the last row's bottom edge for the final slot.
        DzC indicatorCue = rawSlot < cues.size() ? cues.get(rawSlot) : cues.get(cues.size() - 1);
        boolean indicatorBottom = rawSlot == cues.size();
        cLj indicatorRow = list.cfM((OLR) indicatorCue); // the row for that cue
        if (indicatorRow == null) indicatorRow = targetRow;

        DzC boundaryCue = null;
        if (!noop) {
            ArrayList<DzC> remaining = new ArrayList<>(cues);
            remaining.remove(sourceIdx);
            if (finalIndex < 0 || finalIndex > remaining.size()) throw new IllegalStateException("drop index out of range: " + finalIndex);
            if (finalIndex < remaining.size()) boundaryCue = remaining.get(finalIndex);
        }
        return new DropResolution(targetRow, targetCue, before, rawSlot, finalIndex, boundaryY, boundaryCue, noop,
                indicatorRow, indicatorCue, indicatorBottom);
    }

    private static DropResolution resolveSeparatorGap(cKU action, DzC sourceCue, double x, double y) {
        DropPreview preview = dropPreview;
        cLm list = actionList(action);
        if (preview == null || list == null || preview.list != list) return null;
        if (Math.abs(y - preview.boundaryY) > DROP_GAP_HOLD_PX) return null;
        if (x < 0.0 || y < 0.0 || x > list.Sxo() || y > list.yiX()) return null; // outside the list

        List<DzC> cues = SectionMove.cuesSorted(sourceCue);
        int sourceIdx = SectionMove.indexOf(cues, sourceCue);
        if (sourceIdx < 0) throw new IllegalStateException("the dragged cue isn't in the cue list");
        int finalIndex = preview.finalIndex;
        ArrayList<DzC> remaining = new ArrayList<>(cues);
        remaining.remove(sourceIdx);
        if (finalIndex < 0 || finalIndex > remaining.size()) return null;
        DzC boundaryCue = finalIndex < remaining.size() ? remaining.get(finalIndex) : null;

        int targetIdx = SectionMove.indexOf(cues, preview.targetCue);
        boolean before = targetIdx >= 0 && preview.slotIndex == targetIdx;
        boolean noop = finalIndex == sourceIdx;
        return new DropResolution(preview.row, preview.targetCue, before, preview.slotIndex, finalIndex, preview.boundaryY,
                boundaryCue, noop, preview.row, preview.indicatorCue, preview.indicatorBottom);
    }

    private static void updateDropPreview(dIR action, dJM motion) {
        DragState st = DRAGS.get(action);
        if (st == null || motion == null) return;
        try {
            double x = motion.hbP(), y = motion.Grk();
            if (Math.abs(y - st.startY) < DRAG_THRESHOLD_PX) {
                clearDropPreview();
                return;
            }
            DropResolution drop = resolveDrop((cKU) action, st.sourceCue, x, y);
            if (drop == null || drop.noop) {
                clearDropPreview();
                return;
            }
            setDropPreview(new DropPreview(actionList(action), drop.indicatorRow, drop.targetCue, drop.indicatorCue,
                    drop.indicatorBottom, drop.slotIndex, drop.finalIndex, drop.boundaryY));
        } catch (Throwable t) {
            clearDropPreview();
            MOD.log("Showing the insertion marker failed", t);
        }
    }

    private static void setDropPreview(DropPreview next) {
        DropPreview old = dropPreview;
        if (old != null && next != null && old.slotIndex == next.slotIndex) return; // same slot: nothing moves
        dropPreview = next;
        dve repaintRoot = next != null && next.list != null ? (dve) next.list
                : old != null && old.list != null ? (dve) old.list
                : next != null ? next.row : old != null ? old.row : null;
        if (old != null && old.row != null && (next == null || old.row != next.row)) repaint(old.row);
        if (next != null && next.row != null) repaint(next.row);
        repaint(repaintRoot);
    }

    private static void clearDropPreview() {
        DropPreview old = dropPreview;
        if (old == null) return;
        dropPreview = null;
        dve repaintRoot = old.list != null ? (dve) old.list : old.row;
        repaint(old.row);
        if (repaintRoot != old.row) repaint(repaintRoot);
    }

    private static void repaint(dve widget) {
        if (widget == null) return;
        try {
            widget.jbi(); // request repaint
        } catch (Throwable t) {
            MOD.log("Repaint failed", t);
        }
    }

    // ---------- Arranger overview: Alt-dragging a section ----------

    private static final class OverviewDragState {
        final DzC sourceCue;
        final qc1 widget;
        final double startX, startY;
        final DoubleUnaryOperator timeToX;
        boolean dispatchAnchored;
        double screenOriginX, screenOriginY;

        OverviewDragState(DzC sourceCue, qc1 widget, double startX, double startY, DoubleUnaryOperator timeToX) {
            this.sourceCue = sourceCue;
            this.widget = widget;
            this.startX = startX;
            this.startY = startY;
            this.timeToX = timeToX;
        }
    }

    private record OverviewDrop(int slotIndex, int finalIndex, double boundaryX, DzC boundaryCue, boolean noop) {}

    private record OverviewPreview(qc1 widget, int slotIndex, double boundaryX) {}

    private record OverviewReleasePlan(DzC sourceCue, DzC boundaryCue) {}

    private record TransformCandidate(DoubleUnaryOperator forward, double score) {}

    private static final Map<Object, OverviewDragState> OVERVIEW_DRAGS = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<Object, OverviewReleasePlan> OVERVIEW_RELEASES = Collections.synchronizedMap(new IdentityHashMap<>());
    /** Set when a release is a drag, so Bitwig's click zoom (in the same release, same thread) is skipped. */
    private static final ThreadLocal<Boolean> OVERVIEW_SUPPRESS_ZOOM = new ThreadLocal<>();
    private static volatile OverviewPreview overviewPreview;
    private static volatile OverviewDragState activeOverviewDrag;

    /** The press on a section: the gesture knows its cue and where it was pressed. */
    @Hook(target = zLm.class, method = "cfM(LdJH;)V", at = At.OVERRIDE_BEFORE_SUPER,
          id = "arrangement-helper-overview-local-pointer-begin") // pointer pressed
    public static void overviewGestureBegin(@This zLm gesture, @Arg(0) dJH event) {
        if (gesture == null || gesture.getClass() != zLm.class) return;
        try {
            DzC sourceCue = gestureCue(gesture);
            qc1 widget = gesture.qoU(); // its overview
            if (sourceCue == null || widget == null || widget.getClass() != qc1.class) return;
            double startX = ((drX) gesture).FzB, startY = ((drX) gesture).IwV; // where it was pressed
            OverviewDragState st = new OverviewDragState(sourceCue, widget, startX, startY, timeToX(widget, sourceCue, startX));
            OVERVIEW_DRAGS.put(widget, st);
            activeOverviewDrag = st;
            clearOverviewPreviewFor(widget);
        } catch (Throwable t) {
            MOD.log("Starting an overview drag failed", t);
        }
    }

    /**
     * The gesture gets the press and the release but no motion, so the motion comes from the window's
     * pointer dispatcher, which sees every pointer event: absolute x, y and the event's deltas. The first
     * event after the press gives the overview's origin on screen (press position minus local press
     * position); later ones are converted back to overview coordinates. Without an Alt drag on a section
     * it returns at once.
     */
    @Hook(target = dpl.class, method = "cfM(LduG;DDDDDDJLeoV;ZLepa;ZZDI)V", at = At.ENTRY,
          id = "arrangement-helper-overview-live-pointer-dispatch") // dispatch a pointer event
    public static void overviewPointerProcess(@Arg(1) double absoluteX, @Arg(2) double absoluteY,
                                              @Arg(5) double deltaX, @Arg(6) double deltaY) {
        OverviewDragState st = activeOverviewDrag;
        if (st == null) return;
        try {
            if (!st.dispatchAnchored) {
                st.screenOriginX = absoluteX - deltaX - st.startX;
                st.screenOriginY = absoluteY - deltaY - st.startY;
                st.dispatchAnchored = true;
            }
            updateOverviewDragPreview(st, absoluteX - st.screenOriginX);
        } catch (Throwable t) {
            MOD.log("Following the overview drag failed", t);
        }
    }

    /** Bitwig's help line puts the gesture's modifier in front; this is the action's wording. */
    @Hook(target = zLm.class, method = "Hnt(DDLeoV;)Ljava/lang/String;", at = At.OVERRIDE_INSTEAD,
          id = "arrangement-helper-overview-local-drag-help") // the help text for a drag
    public static String overviewDragHelp(@This zLm gesture) {
        return "Move Section";
    }

    /**
     * The release: the destination is worked out here, before Bitwig's release code can change the
     * overview's zoom, and the move runs at the end of the release (overviewGestureReleaseCommit).
     */
    @Hook(target = zLm.class, method = "cfM(LdJI;)V", at = At.ENTRY, id = "arrangement-helper-overview-release-prepare") // pointer released
    public static void overviewGestureReleasePrepare(@This zLm gesture, @Arg(0) dJI event) {
        if (gesture == null || event == null || gesture.getClass() != zLm.class) return;
        OVERVIEW_RELEASES.remove(gesture);
        OVERVIEW_SUPPRESS_ZOOM.remove();
        try {
            DzC sourceCue = gestureCue(gesture);
            qc1 widget = gesture.qoU();
            if (sourceCue == null || widget == null || widget.getClass() != qc1.class) return;

            // The marker only follows the pointer; the drop is decided from the release itself.
            OverviewDragState active = activeOverviewDrag;
            if (active != null && active.widget == widget) activeOverviewDrag = null;
            OVERVIEW_DRAGS.remove(widget);
            clearOverviewPreviewFor(widget);

            double startX = ((drX) gesture).FzB, startY = ((drX) gesture).IwV;
            double endX = ((dJF) event).hbP();
            if (Math.abs(endX - startX) < DRAG_THRESHOLD_PX) return; // a click: Bitwig zooms to the section

            // From here on it is a drag, even if it changes nothing: no click zoom.
            OVERVIEW_SUPPRESS_ZOOM.set(Boolean.TRUE);
            OverviewDragState st = new OverviewDragState(sourceCue, widget, startX, startY, timeToX(widget, sourceCue, startX));
            OverviewDrop drop = resolveOverviewDrop(st, endX);
            if (drop == null || drop.noop) return;
            OVERVIEW_RELEASES.put(gesture, new OverviewReleasePlan(sourceCue, drop.boundaryCue));
        } catch (Throwable t) {
            try {
                qc1 widget = gesture.qoU();
                if (widget != null) {
                    OverviewDragState active = activeOverviewDrag;
                    if (active != null && active.widget == widget) activeOverviewDrag = null;
                    OVERVIEW_DRAGS.remove(widget);
                }
            } catch (Throwable ignored) {}
            OVERVIEW_RELEASES.remove(gesture);
            clearOverviewPreview();
            MOD.log("Working out the overview drop failed", t);
        }
    }

    /** Bitwig's zoom to the clicked section, skipped after a drag. */
    @Hook(target = zLm.class, method = "cfM(LdJI;)V", at = At.REPLACE_CALL, id = "arrangement-helper-overview-suppress-click-zoom-on-drag",
          callOwner = qc1.class, call = "MRY", occurrence = 1) // pointer released; zoom to the cue's section
    public static void overviewMaybeZoom(qc1 widget, AHG cue) {
        boolean suppress = Boolean.TRUE.equals(OVERVIEW_SUPPRESS_ZOOM.get());
        OVERVIEW_SUPPRESS_ZOOM.remove();
        if (!suppress) widget.MRY(cue);
    }

    @Hook(target = zLm.class, method = "cfM(LdJI;)V", at = At.BEFORE_RETURN, id = "arrangement-helper-overview-release-commit") // pointer released
    public static void overviewGestureReleaseCommit(@This zLm gesture, @Arg(0) dJI event) {
        OverviewReleasePlan plan = OVERVIEW_RELEASES.remove(gesture);
        if (plan == null) return;
        try {
            if (plan.boundaryCue == null) SectionMove.moveToEnd(plan.sourceCue);
            else SectionMove.moveToBoundary(plan.sourceCue, plan.boundaryCue);
        } catch (Throwable t) {
            MOD.log("Moving the section failed", t);
        }
    }

    @Hook(target = qc1.class, method = "cfM(Lcom/bitwig/graphics/SYo;)V", at = At.OVERRIDE_AFTER_SUPER,
          id = "arrangement-helper-overview-local-insertion-indicator") // paint
    public static void paintOverviewIndicator(@This qc1 widget, @Arg(0) SYo g) {
        OverviewPreview preview = overviewPreview;
        if (preview == null || g == null || preview.widget != widget) return;
        ArrangementIndicator.paintOverview(widget, g, preview.boundaryX);
    }

    private static DzC gestureCue(zLm gesture) {
        return gesture.MRY instanceof DzC cue ? cue : null; // the gesture's cue
    }

    private static void updateOverviewDragPreview(OverviewDragState st, double x) {
        if (Math.abs(x - st.startX) < DRAG_THRESHOLD_PX) {
            clearOverviewPreviewFor(st.widget);
            return;
        }
        OverviewDrop drop = resolveOverviewDrop(st, x);
        if (drop == null || drop.noop) {
            clearOverviewPreviewFor(st.widget);
            return;
        }
        setOverviewPreview(new OverviewPreview(st.widget, drop.slotIndex, drop.boundaryX));
    }

    /**
     * The overview has two inverse transforms between time and x (Hnt and r_). Which one is time to x is
     * decided here from the pressed section: the one whose x range for the section holds the press, is
     * inverted by the other, lies on the overview and increases.
     */
    private static DoubleUnaryOperator timeToX(qc1 widget, DzC sourceCue, double pressX) {
        DoubleUnaryOperator a = widget::Hnt, b = widget::r_;
        double t0 = SectionMove.position(sourceCue);
        double t1 = SectionMove.sectionEnd(sourceCue);
        if (!(t1 > t0 + SectionMove.EPS)) throw new IllegalStateException("invalid section for the overview transform");
        TransformCandidate ca = transformCandidate(widget, a, b, t0, t1, pressX);
        TransformCandidate cb = transformCandidate(widget, b, a, t0, t1, pressX);
        TransformCandidate best = ca.score >= cb.score ? ca : cb;
        if (best.score < 0.0)
            throw new IllegalStateException("couldn't tell the overview's time-to-x transform (scores " + ca.score + ", " + cb.score + ")");
        return best.forward;
    }

    private static TransformCandidate transformCandidate(qc1 widget, DoubleUnaryOperator forward, DoubleUnaryOperator inverse,
                                                         double t0, double t1, double pressX) {
        try {
            double x0 = forward.applyAsDouble(t0);
            double x1 = forward.applyAsDouble(t1);
            if (!Double.isFinite(x0) || !Double.isFinite(x1) || Math.abs(x1 - x0) < 0.001)
                return new TransformCandidate(forward, -1000.0);

            double rt0 = inverse.applyAsDouble(x0);
            double rt1 = inverse.applyAsDouble(x1);
            double span = Math.max(SectionMove.EPS, Math.abs(t1 - t0));
            double inverseError = (Math.abs(rt0 - t0) + Math.abs(rt1 - t1)) / span;

            double width = widget.Sxo();
            double lo = Math.min(x0, x1), hi = Math.max(x0, x1);
            double tolerance = Math.max(12.0, Math.abs(x1 - x0) * 0.15);
            boolean containsPress = pressX >= lo - tolerance && pressX <= hi + tolerance;
            boolean saneRange = x0 >= -width * 0.25 && x0 <= width * 1.25 && x1 >= -width * 0.25 && x1 <= width * 1.25;
            boolean increasing = x1 > x0;

            double score = 100.0 - inverseError * 100.0;
            if (containsPress) score += 100.0;
            if (saneRange) score += 25.0;
            if (increasing) score += 25.0;
            return new TransformCandidate(forward, score);
        } catch (Throwable t) {
            return new TransformCandidate(forward, -1000.0);
        }
    }

    private static double overviewX(OverviewDragState st, double time) {
        double x = st.timeToX.applyAsDouble(time);
        if (!Double.isFinite(x)) throw new IllegalStateException("no overview x for time " + time);
        return x;
    }

    /** The cue boundary (or the end of the arrangement) nearest to the pointer. */
    private static OverviewDrop resolveOverviewDrop(OverviewDragState st, double pointerX) {
        List<DzC> cues = SectionMove.cuesSorted(st.sourceCue);
        int sourceIdx = SectionMove.indexOf(cues, st.sourceCue);
        if (sourceIdx < 0 || cues.isEmpty()) return null;

        int count = cues.size();
        double[] bx = new double[count + 1];
        for (int i = 0; i < count; i++) bx[i] = overviewX(st, SectionMove.position(cues.get(i)));
        bx[count] = overviewX(st, SectionMove.sectionEnd(cues.get(count - 1)));

        int rawSlot = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < bx.length; i++) {
            double d = Math.abs(pointerX - bx[i]);
            if (d < bestDistance) {
                bestDistance = d;
                rawSlot = i;
            }
        }

        int finalIndex = rawSlot > sourceIdx ? rawSlot - 1 : rawSlot;
        boolean noop = finalIndex == sourceIdx;
        ArrayList<DzC> remaining = new ArrayList<>(cues);
        remaining.remove(sourceIdx);
        if (finalIndex < 0 || finalIndex > remaining.size()) return null;
        DzC boundaryCue = finalIndex < remaining.size() ? remaining.get(finalIndex) : null;
        if (sourceIdx == count - 1 && rawSlot == count) noop = true; // the last section can't move later
        return new OverviewDrop(rawSlot, finalIndex, bx[rawSlot], boundaryCue, noop);
    }

    private static void setOverviewPreview(OverviewPreview next) {
        OverviewPreview old = overviewPreview;
        if (old != null && next != null && old.widget == next.widget && old.slotIndex == next.slotIndex) return;
        overviewPreview = next;
        repaint(next != null ? next.widget : old != null ? old.widget : null);
    }

    private static void clearOverviewPreview() {
        OverviewPreview old = overviewPreview;
        if (old == null) return;
        overviewPreview = null;
        repaint(old.widget);
    }

    private static void clearOverviewPreviewFor(qc1 widget) {
        OverviewPreview old = overviewPreview;
        if (old == null || old.widget != widget) return;
        overviewPreview = null;
        repaint(old.widget);
    }
}
