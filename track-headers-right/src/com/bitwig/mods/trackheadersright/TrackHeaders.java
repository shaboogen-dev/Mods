package com.bitwig.mods.trackheadersright;

import bitwig.obf.aFm; // the timeline column
import bitwig.obf.aYj; // the base of the panels that paint a track insertion marker
import bitwig.obf.aYp; // the arranger's content widget; MRY(...) builds its top-level row once
import bitwig.obf.bAB; // the track automation chooser, whose arrow Bitwig sets at build time
import bitwig.obf.bsO; // the coloured bar of a track header, which paints the automation arrow
import bitwig.obf.dBX; // a chooser
import bitwig.obf.dSG; // a vertical scrollbar
import bitwig.obf.dVx; // which side of a splitter keeps its size
import bitwig.obf.dVz; // a splitter
import bitwig.obf.dWb; // a row or column of child widgets
import bitwig.obf.dXD; // an overlay: children on top of each other
import bitwig.obf.dXe; // which edge of a resizable widget has the handle: MRY left, Hnt right
import bitwig.obf.dXk; // a resizable widget; here the wrapper around the header column
import bitwig.obf.dXx; // a widget that slides in and out
import bitwig.obf.dve; // a widget
import bitwig.obf.dyC; // what a widget accepts as its content
import bml.api.Bml;
import com.bitwig.base.geom.Dgc; // alignment inside an overlay
import com.bitwig.base.geom.DlD; // a rectangle: x FzB(), y IwV(), width UNj(), height hUR()
import com.bitwig.base.geom.IBe; // an orientation
import com.bitwig.base.geom.Jkg; // a direction: cfM left, MRY right
import com.bitwig.flt.document.core.iface.clipboard.track.TrackInsertionPoint;
import com.bitwig.graphics.SYo; // a canvas
import com.bitwig.ramona.core.lBQ; // a boolean property

import java.util.List;

/**
 * What the hooks do. The hooks themselves are in TrackHeadersRight (a mod's hooks all live in its
 * helper class) and only call in here.
 *
 * aYp.MRY builds the arranger once per widget, so the layout hooks act at build time. Bitwig builds:
 *
 *   [dXk headers] [overlay: splitter(launcher | [column] [timeline]), scrollbar] [gap] [tool strip]
 *
 * This mod builds the same widgets as:
 *
 *   [overlay: splitter([overlay: timeline, scrollbar] [column] | launcher), scrollbar] [gap] [tool strip] [dXk headers]
 *
 * Bitwig's scrollbar stays at the splitter's right edge and shows only when the timeline is hidden,
 * as it does with the launcher alone in Bitwig's layout; a second one, on the same scroller, sits at
 * the timeline's edge. The splitter's saved position stays the launcher's width (dVx). A failure in
 * a hook leaves that step as Bitwig has it.
 */
final class TrackHeaders {
    static final Bml.Mod MOD = Bml.mod("track-headers-right");
    private static final String CHOOSER_ARROW_LEFT = "stepper_arrow_left.svg"; // Bitwig's own icon

    /** Whether the arrow being painted points left. Painting is on one thread, and it is set just before use. */
    private static boolean flipArrow;
    /** Whether this build put a scrollbar at the timeline's edge. Building is on one thread. */
    private static boolean timelineHasScrollbar;

    private TrackHeaders() {
    }

    // ---------- The row: the headers last, their handle on their left edge ----------

    static void setContent(aYp arranger, dyC content) {
        try {
            if (content instanceof dWb row) {
                moveHeaders(row);
            } else {
                MOD.log("Headers left in place: the content is not a row (" + name(content) + ")");
            }
        } catch (Throwable failure) {
            MOD.log("Headers left in place: could not read the row", failure);
        }
        arranger.cfM(content);
    }

    private static void moveHeaders(dWb row) {
        List<?> children = row.jm_();
        Object first = children.isEmpty() ? null : children.get(0);
        if (!(first instanceof dXk headers)) {
            MOD.log("Headers left in place: the first child is " + name(first));
            return;
        }
        dXe handle = headers.AU3();
        try {
            row.MJ(headers);
            row.HL(headers);
            headers.Hnt(dXe.MRY);
        } catch (Throwable failure) {
            MOD.log("Could not move the headers; restoring Bitwig's order", failure);
            restore(row, headers, handle);
        }
    }

    private static void restore(dWb row, dXk headers, dXe handle) {
        try {
            if (row.jm_().contains(headers)) row.MJ(headers);
            row.IwV(headers, 0);
            headers.Hnt(handle);
        } catch (Throwable failure) {
            MOD.log("Could not restore Bitwig's order", failure);
        }
    }

    // ---------- The splitter: the launcher second, keeping its saved width ----------

    static dVz splitterSides(IBe orientation, dve launcher, dve timeline) {
        return dVz.cfM(orientation, timeline, launcher);
    }

    /** The saved position is the launcher's width: the first side's in Bitwig's layout, the second side's here. */
    static void splitterSizing(dVz splitter, dVx sizing) {
        splitter.cfM(sizing == dVx.cfM ? dVx.MRY : sizing);
    }

    /** Bitwig binds "first side hidden" to the launcher's show setting; the launcher is the second side here. */
    static lBQ launcherHidden(dVz splitter) {
        return splitter.cCk();
    }

    /** Bitwig binds "second side hidden" to the timeline's show setting; the timeline is the first side here. */
    static lBQ timelineHidden(dVz splitter) {
        return splitter.JqA();
    }

    // ---------- The slide directions ----------

    static dXx slide(dve widget, Jkg direction) {
        return dXx.cfM(widget, mirrored(direction));
    }

    private static Jkg mirrored(Jkg direction) {
        if (direction == Jkg.cfM) return Jkg.MRY;
        if (direction == Jkg.MRY) return Jkg.cfM;
        return direction;
    }

    // ---------- The timeline side: the timeline, its scrollbar, then the column ----------

    /** Bitwig builds [column] [timeline]. Here [timeline with its scrollbar] [column]. */
    static dWb timelineSide(int spacing, dve[] children) {
        timelineHasScrollbar = false;
        try {
            if (children.length == 2 && children[1] instanceof aFm timeline) {
                return dWb.cfM(spacing, new dve[] {timelineWithScrollbar(timeline), children[0]});
            }
            MOD.log("Timeline side left as is: it isn't laid out as expected");
        } catch (Throwable failure) {
            MOD.log("Timeline side left as is", failure);
            timelineHasScrollbar = false;
        }
        return dWb.cfM(spacing, children);
    }

    /** The scrollbar as Bitwig configures its own, on the same scroller. The profile without a scrollbar gets none. */
    private static dve timelineWithScrollbar(aFm timeline) {
        aYp arranger = timeline.BDm();
        if (arranger.AVR()) return timeline;
        dSG scrollbar = dSG.cfM(IBe.MRY);
        scrollbar.lYc(true);
        scrollbar.cfM(arranger.ztx());
        scrollbar.cfM(Dgc.MRY);
        dXD overlay = dXD.cfM(new dve[] {timeline, scrollbar});
        timelineHasScrollbar = true;
        return overlay;
    }

    /** Bitwig's own scrollbar then only serves the launcher alone: shown while the timeline side is hidden. */
    static dXD outerScrollbar(dve[] children) {
        if (timelineHasScrollbar) {
            try {
                if (children.length == 2 && children[0] instanceof dVz splitter && children[1] instanceof dSG scrollbar) {
                    aYp.cfM(scrollbar.DQO(), splitter.JqA()); // JqA(): first side hidden, the timeline here
                }
            } catch (Throwable failure) {
                MOD.log("Bitwig's scrollbar stays shown", failure);
            }
        }
        timelineHasScrollbar = false;
        return dXD.cfM(children);
    }

    // ---------- The track insertion marker: across the whole row ----------

    /**
     * Bitwig's marker runs from the track's x to the splitter's right edge, so with the headers on
     * the right its width comes out negative and nothing shows. Then: the arranger's full width.
     */
    static DlD insertionMarker(aYp arranger, TrackInsertionPoint point) {
        return acrossRow(arranger.FzB(point), arranger);
    }

    /** The clip around the marker has the same width problem. The other panels with a marker aren't aYp. */
    static DlD insertionMarkerClip(aYj panel) {
        DlD clip = panel.ifN();
        return panel instanceof aYp arranger ? acrossRow(clip, arranger) : clip;
    }

    private static DlD acrossRow(DlD rectangle, aYp arranger) {
        if (rectangle == null || rectangle.UNj() >= 0) return rectangle;
        return new DlD(0, rectangle.IwV(), arranger.Sxo(), rectangle.hUR());
    }

    // ---------- The arrows ----------

    /**
     * The arrow on the coloured bar is drawn while the pointer is over the bar: a path at the bar's
     * foot, rotated by a quarter turn when the automation is open, which makes it point down. So
     * only the closed arrow is mirrored. This call sits just before the rotation and the path.
     */
    static boolean barArrowState(bsO bar) {
        boolean open = bar.Cuu();
        flipArrow = !open;
        return open;
    }

    static void barArrowStart(SYo canvas, double x, double y) {
        canvas.MRY(flipArrow ? -x : x, y);
    }

    static void barArrowLine(SYo canvas, double x, double y) {
        canvas.cfM(flipArrow ? -x : x, y);
    }

    /** The automation chooser in a track header: Bitwig's own left arrow, chosen once when the chooser is built. */
    static String chooserArrow(dBX chooser) {
        return chooser instanceof bAB ? CHOOSER_ARROW_LEFT : chooser.lT_();
    }

    private static String name(Object o) {
        return o == null ? "null" : o.getClass().getName();
    }
}
