package com.bitwig.mods.pitchexpressionpointer;

import bitwig.obf.TY; // the piano roll's mode settings, as the note grid reads them
import bitwig.obf.bie; // the note grid's selection
import bitwig.obf.brw; // the painter the note grid's hit tests use
import bitwig.obf.dJH; // a mouse press
import bitwig.obf.dJI; // a mouse release
import bitwig.obf.dJK; // a mouse press, drag or release
import bitwig.obf.dnf; // a window's widget root; holds the Alt, Ctrl and Shift state
import bitwig.obf.doW; // a double-click action
import bitwig.obf.dpl; // a window's mouse and key event dispatcher
import bitwig.obf.drX; // a mouse gesture
import bitwig.obf.eoJ; // what kind of double-click
import bitwig.obf.eoV; // a set of modifier keys
import bitwig.obf.eoW; // a mouse button
import bitwig.obf.gTM; // "no time offset", as the grid's own note walk passes it
import bitwig.obf.hJh; // the expressions the piano roll can edit
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.This;
import com.bitwig.base.geom.DlD; // a rectangle in whole pixels
import com.bitwig.base.geom.DoublePoint; // a mouse position
import com.bitwig.flt.widget.core.timeline.RsX; // shared curve-editing helpers
import com.bitwig.flt.widget.core.timeline.interaction.AbstractMoveValueEventDragAction; // base of point drags
import com.bitwig.flt.widget.core.timeline.interaction.EventCurvatureDragAction; // bends both sides of a point
import com.bitwig.flt.widget.core.timeline.interaction.SegmentCurvatureDragAction; // bends a line
import com.bitwig.flt.widget.core.timeline.interaction.Swj; // hit test: which note is under the mouse
import com.bitwig.flt.widget.core.timeline.interaction.pwQ; // a hit: a location on a curve
import com.bitwig.flt.widget.core.timeline.kig; // the piano roll's note grid
import com.bitwig.graphics.SYo; // a paint context
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Piano Roll: Pitch Expression Pointer.
 *
 * While Alt is held with the pointer tool and the expression mode is off, the note grid edits pitch
 * with Bitwig's own gestures. The first two reads make the grid behave as in the mode, and only they
 * read the tool: every other hook runs behind them, or belongs to a gesture they let start, which
 * carries on if the tool changes during it, as it does if Alt is let go. {@link #chooseGesture} picks
 * which of Bitwig's gestures a press starts: on a point it moves it, around a point it makes an
 * S-curve, close to a line it adds a point, further out it bends the line. A bend gesture that is
 * released without moving has not edited anything, so that click adds a point, the way Bitwig's
 * add-point gesture handles its own. The same release around a point selects that point.
 * {@link DoubleClick} does the rest of Bitwig's double-click.
 *
 * The saved settings are only read, never written.
 */
public final class PitchExpressionPointer {
    /**
     * Scaled pixels from the line, vertically, inside which a drag adds a point. Beyond it a drag
     * bends the line, out to the edge of Bitwig's own far zone, about 24 scaled pixels, where the
     * curve stops being found. This value is Bitwig's own near zone.
     */
    private static final double ADD_POINT_BAND = 6.0;

    /** Scaled pixels from a point's centre, in a straight line, inside which a drag moves the point. */
    static final double MOVE_POINT_RADIUS = 6.0;

    /** Scaled pixels from a point's centre: from MOVE_POINT_RADIUS out to here, a drag makes an S-curve. */
    static final double S_CURVE_RADIUS = 12.0;

    /** Half the side of the square Bitwig tests for a point: 7 for the handle plus 4 of padding. */
    private static final double BITWIG_POINT_HIT_HALF = 5.5;

    private static final Bml.Mod MOD = Bml.mod("pitch-expression-pointer");

    private static volatile boolean altHeld;
    private static volatile boolean failureLogged;

    /** The first-point gestures this mod started, with their grids (weak: a gesture lives for one press). */
    private static final Map<drX, kig> FIRST_POINTS = Collections.synchronizedMap(new WeakHashMap<>());

    private PitchExpressionPointer() {
    }

    /** Bitwig stores whether Alt is held, for any window. */
    @Hook(target = dnf.class, method = "FzB(Z)V", at = At.ENTRY, id = "alt-state")
    public static void noteAlt(@Arg(0) boolean held) {
        try {
            altHeld = held;
        } catch (Throwable t) {
            contain("alt-state", t);
        }
    }

    /** The note grid's "is the expression mode on": yes while Alt is held with the pointer tool. */
    @Hook(target = kig.class, method = "ELS()Z", at = At.REPLACE_CALL, id = "editing-flag",
          callOwner = TY.class, call = "iCS", occurrence = 1)
    public static boolean editingFlag(TY settings) {
        boolean original = settings.iCS(); // is_editing_expressions_on_piano_roll
        try {
            return (altHeld && pointerTool(settings)) || original;
        } catch (Throwable t) {
            contain("editing-flag", t);
            return original;
        }
    }

    /** The note grid's "which expression": pitch while Alt is held with the pointer tool. */
    @Hook(target = kig.class, method = "kTh()Lucd;", at = At.REPLACE_CALL, id = "selected-expression",
          callOwner = TY.class, call = "hDc", occurrence = 1)
    public static hJh selectedExpression(TY settings) {
        hJh original = settings.hDc(); // selected_piano_roll_expression
        try {
            return altHeld && pointerTool(settings) ? hJh.IwV : original; // the pitch expression (TRANSPOSE)
        } catch (Throwable t) {
            contain("selected-expression", t);
            return original;
        }
    }

    /**
     * The pointer's expression gesture for a mouse position, asked on every mouse move and press.
     * With the mode really on, Bitwig decides. With it off, there is a gesture only for Alt, and the
     * grid only asks when {@link #editingFlag} said yes: Alt with the pointer tool.
     */
    @Hook(target = kig.class,
          method = "Hnt(LeoW;Lcom/bitwig/base/geom/DoublePoint;LeoV;DDLcom/bitwig/graphics/SYo;Lcom/bitwig/base/geom/DlD;)LdrX;",
          at = At.REPLACE_CALL, id = "choose-gesture", callOwner = kig.class,
          call = "cfM(LeoW;Lcom/bitwig/base/geom/DoublePoint;LeoV;DDLcom/bitwig/graphics/SYo;Lcom/bitwig/base/geom/DlD;ZZZ)LdrX;",
          occurrence = 1)
    public static drX chooseGesture(kig grid, eoW button, DoublePoint at, eoV mods, double x, double y,
                                    SYo paint, DlD bounds, boolean points, boolean lines, boolean far) {
        boolean ours;
        try {
            ours = !modeReallyOn(grid) && button.equals(eoW.cfM); // the left button
            if (ours && !mods.IwV()) { // Alt isn't part of this ask: the grid is as with the mode off
                return null;
            }
        } catch (Throwable t) {
            contain("choose-gesture", t);
            ours = false;
        }
        if (ours) {
            try {
                return altGesture(grid, button, at, mods, x, y, paint, bounds);
            } catch (Throwable t) {
                contain("choose-gesture", t);
            }
        }
        return grid.cfM(button, at, mods, x, y, paint, bounds, points, lines, far);
    }

    /** Bitwig's point hit test reaches the S-curve zone with this much padding, in scaled pixels. */
    static double pointPadding() {
        return S_CURVE_RADIUS - BITWIG_POINT_HIT_HALF;
    }

    private static drX altGesture(kig grid, eoW button, DoublePoint at, eoV mods, double x, double y,
                                  SYo paint, DlD bounds) {
        eoV plain = withoutAlt(mods);

        double scale = grid.mvz();

        // Bitwig's own point hit test, widened by the padding it takes as its last argument.
        long padding = (long) Math.ceil(pointPadding() * scale);
        pwQ point = grid.cfM(at, true, true, plain, x, y, paint, bounds, padding);
        if (point != null && point.MIO()) { // a hold point's other half: Bitwig answers this itself
            drX hold = grid.cfM(button, at, plain, x, y, paint, bounds, true, false, false);
            if (hold != null) {
                return chosen(hold, "point", Double.NaN);
            }
        } else if (point != null) {
            double fromPoint = Math.hypot(at.Hnt() - grid.r_(point.ODA()),
                                          at.FzB() - grid.cfM(point.nVd(), point.E3())) / scale;
            if (fromPoint <= MOVE_POINT_RADIUS) {
                // Without Alt: a drag moves the point, a click selects it.
                return chosen(grid.cfM(point, button, at, plain, paint, bounds), "point", fromPoint);
            }
            if (fromPoint <= S_CURVE_RADIUS) {
                // With Alt: Bitwig's own S-curve around the point.
                return chosen(grid.cfM(point, button, at, mods, paint, bounds), "S-curve", fromPoint);
            }
        }

        // The curve wherever it is on screen, as in the real mode: this walks the curves, not the
        // notes' rectangles, so a line far from its note is found. Its reach is Bitwig's far zone.
        pwQ hit = grid.MRY(at, false);
        if (hit == null || !RsX.cfM(hit.E3()) || !hit.tp2()) {
            return chosen(null, "out of reach", Double.NaN); // Bitwig's own note handling, or its lasso
        }
        double distance = Math.abs(at.FzB() - grid.cfM(hit.nVd(), hit.E3())) / scale; // scaled pixels, vertically

        if (hit.TtY() == null) { // no points yet: there is no line to reach, only the note itself
            if (!onNote(grid, at, plain, x, y, paint, bounds)) {
                return chosen(null, "off note", distance); // Bitwig's own lasso
            }
            // Bitwig's own first-point gesture, built as the grid builds it (XzI.cfM(DoublePoint, eoV, pwQ)):
            // for the note grid, with Alt left out, the same arguments the real mode passes.
            drX first = grid.cfM(at, plain, hit);
            if (first != null) FIRST_POINTS.put(first, grid);
            return chosen(first, "first point", distance);
        }
        if (distance > ADD_POINT_BAND && hit.wGc().Z1C()) { // can bend: Bitwig's own answer for Alt on a line
            drX bend = grid.cfM(button, at, mods, x, y, paint, bounds, false, true, false);
            if (bend != null) {
                return chosen(bend, "bend", distance);
            }
        }
        return chosen(new AddPointGesture(grid, hit, at, plain), "add point", distance);
    }

    /** The gesture chosen for this mouse position. {@code kind} and {@code distance} say why, for reading. */
    private static drX chosen(drX gesture, String kind, double distance) {
        return gesture;
    }

    /** Every press: the click before it can no longer be the first of a double-click. */
    @Hook(target = dpl.class, method = "cfM(LeoY;)V", at = At.REPLACE_CALL, id = "press",
          callOwner = drX.class, call = "cfM(LdJH;)V", occurrence = 1)
    public static void press(drX gesture, dJH press) {
        try {
            DoubleClick.notePress();
        } catch (Throwable t) {
            contain("press", t);
        }
        gesture.cfM(press);
    }

    /**
     * Every release. A first-point gesture this mod started commits its point on release: that runs as
     * one Undo step that a double-click can take back, as the click that adds a point on a line does.
     */
    @Hook(target = dpl.class, method = "cfM(LeoZ;)V", at = At.REPLACE_CALL, id = "release",
          callOwner = drX.class, call = "cfM(LdJI;)V", occurrence = 1)
    public static void release(drX gesture, dJI release) {
        kig grid = null;
        try {
            grid = FIRST_POINTS.remove(gesture);
        } catch (Throwable t) {
            contain("release", t);
        }
        if (grid != null) {
            DoubleClick.addingPoint(grid, release, () -> gesture.cfM(release));
        } else {
            gesture.cfM(release);
        }
    }

    /**
     * The grid's double-click, once Bitwig has counted two clicks. In the note grid its own answer with
     * Alt resets the curve on a point, and is nothing away from the first note it finds at that time,
     * so the grid goes on to delete or create a note.
     */
    @Hook(target = kig.class, method = "MRY(DDLeoV;LeoJ;)LdoW;", at = At.REPLACE_CALL, id = "double-click",
          callOwner = kig.class, call = "IwV(DDLeoV;LeoJ;)LdoW;", occurrence = 1)
    public static doW doubleClick(kig grid, double x, double y, eoV mods, eoJ kind) {
        try {
            if (kind == eoJ.cfM && mods.IwV() && !modeReallyOn(grid)) { // a mouse double-click with Alt
                doW ours = DoubleClick.choose(grid, x, y, mods);
                if (ours != null) {
                    return ours;
                }
            }
        } catch (Throwable t) {
            contain("double-click", t);
        }
        return grid.IwV(x, y, mods, kind);
    }

    /** The grid's own note hit test, as it runs it for the pointer: two passes over the notes. */
    static boolean onNote(kig grid, DoublePoint at, eoV plain, double x, double y, SYo paint, DlD bounds) {
        brw painter = new brw(paint, grid, grid, grid.xlY(), grid.Hnt(), bounds);
        for (boolean pass : new boolean[] {false, true}) {
            painter.ucQ(pass);
            Swj test = new Swj(painter, at, plain);
            grid.cfM(test, x, y, gTM.cfM(), false, false); // walk the notes
            if (test.LaE() != null) { // the note it found
                return true;
            }
        }
        return false;
    }

    /** A bend released without moving: the click adds a point, as Bitwig's add-point gesture does. */
    @Hook(target = SegmentCurvatureDragAction.class, method = "mouseClicked", at = At.OVERRIDE_AFTER_SUPER,
          id = "click-on-line")
    public static void clickOnLine(@This SegmentCurvatureDragAction action, @Arg(0) dJI release) {
        try {
            if (action.getWidget() instanceof kig grid && !modeReallyOn(grid)) {
                eoV plain = withoutAlt(release.E3());
                new AddPointGesture(grid, action.getCurveLocation(), release.tp2(), plain).cfM(release);
            }
        } catch (Throwable t) {
            contain("click-on-line", t);
        }
    }

    /** An S-curve released without moving: the click selects the point, as a click on the point does. */
    @Hook(target = EventCurvatureDragAction.class, method = "mouseClicked", at = At.OVERRIDE_AFTER_SUPER,
          id = "click-near-point")
    public static void clickNearPoint(@This EventCurvatureDragAction action, @Arg(0) dJI release) {
        try {
            if (action.getWidget() instanceof kig grid && !modeReallyOn(grid)) {
                bie selection = grid.Hnt();
                if (!release.fJR() && !release.nVd()) { // not an add-to-selection click, as Bitwig tests it
                    selection.PD3(); // clear the selection
                }
                selection.h_(action.getCurveLocation().MRY(grid.mC_().mR_())); // the point, as Bitwig names it
                grid.jbi(); // repaint
            }
        } catch (Throwable t) {
            contain("click-near-point", t);
        }
    }

    /** Alt's slow movement during a drag: not for this mod's gestures. */
    @Hook(target = AbstractMoveValueEventDragAction.class, method = "calculateScaleFactor", at = At.REPLACE_CALL,
          id = "normal-speed", callOwner = dJK.class, call = "E3", occurrence = 1)
    public static eoV modsForSpeed(dJK event) {
        eoV original = event.E3();
        try {
            if (isOurDrag(event, original)) return withoutAlt(original);
        } catch (Throwable t) {
            contain("normal-speed", t);
        }
        return original;
    }

    /** Alt's "no pitch snap" during a drag: not for this mod's gestures. */
    @Hook(target = RsX.class, method = "cfM(DLdJK;Lvcm;DLQkI;DLOjS;Lcom/bitwig/flt/widget/core/timeline/XzI;)D",
          at = At.REPLACE_CALL, id = "normal-snap", callOwner = dJK.class, call = "E3", occurrence = 1)
    public static eoV modsForSnap(dJK event) {
        eoV original = event.E3();
        try {
            if (isOurDrag(event, original)) {
                if (original.wGc()) return original; // Ctrl or Cmd: leave Alt in, so Bitwig skips its own pitch snap
                return withoutAlt(original);
            }
        } catch (Throwable t) {
            contain("normal-snap", t);
        }
        return original;
    }

    /** With the mode off, only this mod starts value drags in the note grid. */
    private static boolean isOurDrag(dJK event, eoV mods) {
        return mods.IwV() && event.FzB() instanceof kig grid && !modeReallyOn(grid);
    }

    /** The tool in use, held down or not: Bitwig has one tool value, and holding a tool's key sets it. */
    private static boolean pointerTool(TY settings) {
        return settings.FzB().iHb(); // FzB: the tool, iHb: pointer (object_selection)
    }

    private static boolean modeReallyOn(kig grid) {
        return grid.LFv().iCS(); // the grid's settings, read directly: no hook sits on this read
    }

    static eoV withoutAlt(eoV mods) {
        return mods.IwV(eoV.hUR); // the same modifiers less the Alt bit
    }

    /** Logs the first failure only: these hooks run during paint and on every mouse move. */
    static void contain(String hook, Throwable t) {
        if (!failureLogged) {
            failureLogged = true;
            MOD.log(hook + ": failed, using Bitwig's own value", t);
        }
    }
}
