package com.bitwig.mods.pitchexpressionpointer;

import bitwig.obf.dJI; // a mouse release
import bitwig.obf.dfI; // a document's undo history
import bitwig.obf.doW; // a double-click action
import bitwig.obf.dpN; // the application: Bitwig's own texts
import bitwig.obf.eoV; // a set of modifier keys
import bitwig.obf.jWn; // Bitwig's own texts for drag actions
import bitwig.obf.nkK; // a point whose curve can bend
import com.bitwig.base.geom.DlD; // a rectangle in whole pixels
import com.bitwig.base.geom.DoublePoint; // a mouse position
import com.bitwig.flt.document.core.master.IOJ; // an undoable change
import com.bitwig.flt.widget.core.timeline.RsX; // shared curve-editing helpers
import com.bitwig.flt.widget.core.timeline.XzI; // the point and curve engine
import com.bitwig.flt.widget.core.timeline.interaction.pwQ; // a hit: a location on a curve
import com.bitwig.flt.widget.core.timeline.kig; // the piano roll's note grid
import com.bitwig.graphics.SYo; // a paint context
import com.bitwig.graphics.eD; // a paint context moved to a widget's origin
import com.bitwig.ramona.core.AVL; // a document object, as Bitwig finds its undo history
import com.bitwig.ramona.core.oDc; // a document object that undoable changes belong to

/**
 * A double-click with Alt held: on a point it deletes the point, around a point it resets the S-curve,
 * on a line it resets the line.
 *
 * Bitwig decides what is a double-click and only then asks the grid what it does. Its first click has
 * already happened by then, and on a line that click added a point. So a click that adds a point is one
 * Undo step that this class remembers, and the double-click starts with Bitwig's own Undo of that step:
 * the point goes and the curve is exactly as it was before the first click.
 */
final class DoubleClick {
    /** The point added by the last click, until the next press. */
    private record FirstClick(XzI grid, Object step, double x, double y) { // step: the Undo step, by identity
    }

    private static volatile FirstClick firstClick;

    private DoubleClick() {
    }

    /** A new press in any widget: the click before it can no longer be the first of a double-click. */
    static void notePress() {
        firstClick = null;
    }

    /** Runs what a click adds a point with, as one Undo step, and remembers that step. */
    static void addingPoint(XzI grid, dJI release, Runnable insert) {
        IOJ change = new IOJ();
        dfI history = null;
        Object before = null;
        boolean opened = false;
        try {
            oDc document = (oDc) (Object) grid.lq_();
            history = dfI.cfM((AVL) document);
            before = history == null ? null : history.ejd().eLY();
            opened = history != null && change.cfM(document, jWn.cfM("mouse.insert_point.desc"));
        } catch (Throwable t) {
            PitchExpressionPointer.contain("add-point step", t);
        }
        try {
            insert.run();
        } finally {
            try {
                if (opened) {
                    change.MRY(); // finish
                    Object after = history.ejd().eLY();
                    if (after != before) {
                        firstClick = new FirstClick(grid, after, release.hbP(), release.Grk());
                    }
                }
            } catch (Throwable t) {
                PitchExpressionPointer.contain("add-point step", t);
            }
        }
    }

    /**
     * What a double-click does here, or null for Bitwig's own answer. This only chooses: Bitwig also asks
     * it for the status-bar hints. The edit is in the action it returns.
     */
    static doW choose(kig grid, double x, double y, eoV mods) {
        DoublePoint at = new DoublePoint(x, y);
        FirstClick first = firstClick;
        if (first != null && first.grid == grid
                && Math.hypot(x - first.x, y - first.y) <= PitchExpressionPointer.MOVE_POINT_RADIUS * grid.mvz()) {
            return new doW(resetText(), press -> resetAfterFirstClick(grid, at, first, press));
        }

        eoV plain = PitchExpressionPointer.withoutAlt(mods);
        double scale = grid.mvz();
        DlD bounds = grid.kf();
        double from = grid.Hnt((double) bounds.FzB());
        double to = grid.Hnt((double) bounds.TMF());
        SYo paint = new eD(grid.afn().IwV(), -bounds.FzB(), -bounds.IwV(), true);
        try {
            doW onPoint = aroundPoint(grid, at, plain, scale, from, to, paint, bounds);
            if (onPoint != null) {
                return onPoint;
            }
            pwQ hit = grid.MRY(at, false);
            if (hit == null || !RsX.cfM(hit.E3()) || !hit.tp2()) {
                return null; // out of reach, as for a click: Bitwig's own double-click
            }
            if (hit.TtY() == null && !PitchExpressionPointer.onNote(grid, at, plain, from, to, paint, bounds)) {
                return null; // off a note with no points, as for a click
            }
            return lineCanReset(hit) ? asOneStep(grid, RsX.cfM(hit, grid)) : new doW(resetText(), press -> {
            });
        } finally {
            paint.iy_();
        }
    }

    /** Bitwig's own actions for the zones of a point: delete it, edit the value, reset both sides of it. */
    private static doW aroundPoint(kig grid, DoublePoint at, eoV plain, double scale, double from, double to,
                                   SYo paint, DlD bounds) {
        long padding = (long) Math.ceil(PitchExpressionPointer.pointPadding() * scale);
        pwQ point = grid.cfM(at, true, true, plain, from, to, paint, bounds, padding);
        if (point == null || point.TtY() == null) {
            return null;
        }
        double fromPoint = Math.hypot(at.Hnt() - grid.r_(point.ODA()),
                                      at.FzB() - grid.cfM(point.nVd(), point.E3())) / scale;
        if (point.MIO() || fromPoint <= PitchExpressionPointer.MOVE_POINT_RADIUS) {
            return asOneStep(grid, RsX.MRY(point, grid));
        }
        if (fromPoint > PitchExpressionPointer.S_CURVE_RADIUS) {
            return null;
        }
        if (point.iCS()) { // on the point's value text
            return grid.cfM(point, plain);
        }
        if (RsX.FzB(point.wGc())) {
            return asOneStep(grid, RsX.cfM((nkK) point.TtY(), point.TtY(), grid));
        }
        return null;
    }

    /** Undoes the first click, then resets whatever line is there now. */
    private static void resetAfterFirstClick(kig grid, DoublePoint at, FirstClick first, Object press) {
        firstClick = null;
        try {
            oDc document = (oDc) (Object) grid.lq_();
            dfI history = dfI.cfM((AVL) document);
            if (history != null && history.ejd().eLY() == first.step) {
                grid.lq_().Mb2(); // Bitwig's Undo; otherwise the point stays: it is no longer the last edit
            }
            pwQ line = grid.MRY(at, false);
            if (line != null && RsX.cfM(line.E3()) && line.tp2() && lineCanReset(line)) {
                runAsOneStep(grid, RsX.cfM(line, grid), press);
            }
        } catch (Throwable t) {
            PitchExpressionPointer.contain("double-click", t);
        }
    }

    /** A line between two points. Outside its first and last point a note's pitch has no curve to reset. */
    private static boolean lineCanReset(pwQ line) {
        return line.TtY() != null && !line.fJR() && !line.iHb() && RsX.FzB(line.wGc());
    }

    private static doW asOneStep(kig grid, doW action) {
        if (action == null) {
            return null;
        }
        return new doW(action.cfM(), press -> {
            try {
                runAsOneStep(grid, action, press);
            } catch (Throwable t) {
                PitchExpressionPointer.contain("double-click", t);
            }
        });
    }

    private static void runAsOneStep(kig grid, doW action, Object press) {
        IOJ change = new IOJ();
        boolean opened = change.cfM((oDc) (Object) grid.lq_(), action.cfM());
        try {
            action.cfM(press);
        } finally {
            if (opened) {
                change.MRY(); // finish
            }
        }
    }

    private static String resetText() {
        return dpN.hbP("mouse.automation.reset_curvature.desc");
    }
}
