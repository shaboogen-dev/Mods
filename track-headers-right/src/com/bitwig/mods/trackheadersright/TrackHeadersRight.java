package com.bitwig.mods.trackheadersright;

import bitwig.obf.aYj; // the base of the panels that paint a track insertion marker
import bitwig.obf.aYp; // the arranger's content widget
import bitwig.obf.bsO; // the coloured bar of a track header
import bitwig.obf.dBX; // a chooser
import bitwig.obf.dVx; // which side of a splitter keeps its size
import bitwig.obf.dVz; // a splitter
import bitwig.obf.dWb; // a row or column of widgets
import bitwig.obf.dXD; // an overlay
import bitwig.obf.dXx; // a widget that slides in and out
import bitwig.obf.dve; // a widget
import bitwig.obf.dyC; // what a widget accepts as its content
import bml.api.At;
import bml.api.Hook;
import com.bitwig.base.geom.DlD; // a rectangle
import com.bitwig.base.geom.IBe; // an orientation
import com.bitwig.base.geom.Jkg; // a direction
import com.bitwig.flt.document.core.iface.clipboard.track.TrackInsertionPoint;
import com.bitwig.graphics.SYo; // a graphics context
import com.bitwig.ramona.core.lBQ; // a boolean value

/**
 * Puts the Arranger's track headers on the right. Every hook here acts when the arranger is built,
 * except the arrow on a header's coloured bar, which is painted; the logic is in {@link TrackHeaders}.
 */
public final class TrackHeadersRight {
    private static final String ARRANGER_BUILD = "MRY(Lcom/bitwig/flt/document/core/iface/kWY;ZZZLaYw;)V";
    private static final String BAR_PAINT = "Hnt(Lcom/bitwig/graphics/SYo;)V";

    private TrackHeadersRight() {
    }

    @Hook(id = "headers-to-row-end", target = aYp.class, method = ARRANGER_BUILD, at = At.REPLACE_CALL,
          callOwner = aYp.class, call = "cfM(LdyC;)V", occurrence = 1)
    public static void arrangerContent(final aYp arranger, final dyC content) {
        TrackHeaders.setContent(arranger, content);
    }

    @Hook(id = "splitter-sides", target = aYp.class, method = ARRANGER_BUILD, at = At.REPLACE_CALL,
          callOwner = dVz.class, call = "cfM(Lcom/bitwig/base/geom/IBe;Ldve;Ldve;)LdVz;", occurrence = 1)
    public static dVz splitterSides(final IBe orientation, final dve launcher, final dve timeline) {
        return TrackHeaders.splitterSides(orientation, launcher, timeline);
    }

    /** The same call on the path where the two sides aren't wrapped to slide. */
    @Hook(id = "splitter-sides-plain", target = aYp.class, method = ARRANGER_BUILD, at = At.REPLACE_CALL,
          callOwner = dVz.class, call = "cfM(Lcom/bitwig/base/geom/IBe;Ldve;Ldve;)LdVz;", occurrence = 2)
    public static dVz splitterSidesPlain(final IBe orientation, final dve launcher, final dve timeline) {
        return TrackHeaders.splitterSides(orientation, launcher, timeline);
    }

    @Hook(id = "splitter-sizing", target = aYp.class, method = ARRANGER_BUILD, at = At.REPLACE_CALL,
          callOwner = dVz.class, call = "cfM(LdVx;)V", occurrence = 1)
    public static void splitterSizing(final dVz splitter, final dVx sizing) {
        TrackHeaders.splitterSizing(splitter, sizing);
    }

    @Hook(id = "launcher-hidden", target = aYp.class, method = ARRANGER_BUILD, at = At.REPLACE_CALL,
          callOwner = dVz.class, call = "JqA()Lcom/bitwig/ramona/core/lBQ;", occurrence = 1)
    public static lBQ launcherHidden(final dVz splitter) {
        return TrackHeaders.launcherHidden(splitter);
    }

    @Hook(id = "timeline-hidden", target = aYp.class, method = ARRANGER_BUILD, at = At.REPLACE_CALL,
          callOwner = dVz.class, call = "cCk()Lcom/bitwig/ramona/core/lBQ;", occurrence = 1)
    public static lBQ timelineHidden(final dVz splitter) {
        return TrackHeaders.timelineHidden(splitter);
    }

    @Hook(id = "column-slide", target = aYp.class, method = ARRANGER_BUILD, at = At.REPLACE_CALL,
          callOwner = dXx.class, call = "cfM(Ldve;Lcom/bitwig/base/geom/Jkg;)LdXx;", occurrence = 1)
    public static dXx columnSlide(final dve widget, final Jkg direction) {
        return TrackHeaders.slide(widget, direction);
    }

    @Hook(id = "launcher-slide", target = aYp.class, method = ARRANGER_BUILD, at = At.REPLACE_CALL,
          callOwner = dXx.class, call = "cfM(Ldve;Lcom/bitwig/base/geom/Jkg;)LdXx;", occurrence = 2)
    public static dXx launcherSlide(final dve widget, final Jkg direction) {
        return TrackHeaders.slide(widget, direction);
    }

    @Hook(id = "timeline-slide", target = aYp.class, method = ARRANGER_BUILD, at = At.REPLACE_CALL,
          callOwner = dXx.class, call = "cfM(Ldve;Lcom/bitwig/base/geom/Jkg;)LdXx;", occurrence = 3)
    public static dXx timelineSlide(final dve widget, final Jkg direction) {
        return TrackHeaders.slide(widget, direction);
    }

    @Hook(id = "timeline-side", target = aYp.class, method = ARRANGER_BUILD, at = At.REPLACE_CALL,
          callOwner = dWb.class, call = "cfM(I[Ldve;)LdWb;", occurrence = 1)
    public static dWb timelineSide(final int spacing, final dve[] children) {
        return TrackHeaders.timelineSide(spacing, children);
    }

    @Hook(id = "outer-scrollbar", target = aYp.class, method = ARRANGER_BUILD, at = At.REPLACE_CALL,
          callOwner = dXD.class, call = "cfM([Ldve;)LdXD;", occurrence = 1)
    public static dXD outerScrollbar(final dve[] children) {
        return TrackHeaders.outerScrollbar(children);
    }

    @Hook(id = "insertion-marker", target = aYp.class, method = "cfM(LuGG;)Lcom/bitwig/base/geom/DlD;", at = At.REPLACE_CALL,
          callOwner = aYp.class,
          call = "FzB(Lcom/bitwig/flt/document/core/iface/clipboard/track/TrackInsertionPoint;)Lcom/bitwig/base/geom/DlD;",
          occurrence = 1)
    public static DlD insertionMarker(final aYp arranger, final TrackInsertionPoint point) {
        return TrackHeaders.insertionMarker(arranger, point);
    }

    @Hook(id = "insertion-marker-clip", target = aYj.class, method = "cfM(Lcom/bitwig/graphics/SYo;LuGG;)V", at = At.REPLACE_CALL,
          callOwner = aYj.class, call = "ifN()Lcom/bitwig/base/geom/DlD;", occurrence = 1)
    public static DlD insertionMarkerClip(final aYj panel) {
        return TrackHeaders.insertionMarkerClip(panel);
    }

    @Hook(id = "bar-arrow-state", target = bsO.class, method = BAR_PAINT, at = At.REPLACE_CALL,
          callOwner = bsO.class, call = "Cuu()Z", occurrence = 3)
    public static boolean barArrowState(final bsO bar) {
        return TrackHeaders.barArrowState(bar);
    }

    @Hook(id = "bar-arrow-start", target = bsO.class, method = BAR_PAINT, at = At.REPLACE_CALL,
          callOwner = SYo.class, call = "MRY(DD)V", occurrence = 1)
    public static void barArrowStart(final SYo canvas, final double x, final double y) {
        TrackHeaders.barArrowStart(canvas, x, y);
    }

    @Hook(id = "bar-arrow-tip", target = bsO.class, method = BAR_PAINT, at = At.REPLACE_CALL,
          callOwner = SYo.class, call = "cfM(DD)V", occurrence = 1)
    public static void barArrowTip(final SYo canvas, final double x, final double y) {
        TrackHeaders.barArrowLine(canvas, x, y);
    }

    @Hook(id = "bar-arrow-end", target = bsO.class, method = BAR_PAINT, at = At.REPLACE_CALL,
          callOwner = SYo.class, call = "cfM(DD)V", occurrence = 2)
    public static void barArrowEnd(final SYo canvas, final double x, final double y) {
        TrackHeaders.barArrowLine(canvas, x, y);
    }

    @Hook(id = "chooser-arrow", target = dBX.class, method = "xcy()V", at = At.REPLACE_CALL,
          callOwner = dBX.class, call = "lT_()Ljava/lang/String;", occurrence = 1)
    public static String chooserArrow(final dBX chooser) {
        return TrackHeaders.chooserArrow(chooser);
    }
}
