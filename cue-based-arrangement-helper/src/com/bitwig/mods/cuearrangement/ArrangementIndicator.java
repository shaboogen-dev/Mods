package com.bitwig.mods.cuearrangement;

import bitwig.obf.dve; // a widget
import bitwig.obf.eeY; // a theme colour
import bitwig.obf.iok; // a paint colour
import bml.api.Bml;
import com.bitwig.graphics.SYo; // a widget's graphics

/** The insertion markers: a horizontal line in the Project Panel's cue list, a vertical one in the arranger overview. */
final class ArrangementIndicator {
    private static final Bml.Mod MOD = Bml.mod("cue-based-arrangement-helper");
    private static final double SHOULDER_GREY = 118.0 / 255.0;
    private static final iok SHOULDER = iok.cfM(SHOULDER_GREY, SHOULDER_GREY, SHOULDER_GREY, 1.0); // from RGBA
    private static final iok WHITE = iok.cfM(1.0, 1.0, 1.0, 1.0);
    private static volatile boolean loggedError;

    private ArrangementIndicator() {}

    /** At boundaryY in the cue list: 1 px grey, 2 px drop indicator colour (white), 1 px grey. */
    static void paintList(dve list, SYo g, double boundaryY) {
        try {
            double width = list.Sxo();
            double height = list.yiX();
            if (!(width > 0.0) || !(height > 0.0)) return;
            // Keep the whole 4 px band visible at the list's top and bottom.
            double y = boundaryY;
            if (height >= 4.0) y = Math.max(2.0, Math.min(height - 2.0, y));
            g.cfM(0.0, y - 2.0, width, 4.0, SHOULDER); // fill rectangle
            g.cfM(0.0, y - 1.0, width, 2.0, core(list));
        } catch (Throwable t) {
            logOnce(t);
        }
    }

    /** At boundaryX in the overview, twice as wide so it shows against coloured sections: 2 px grey, 4 px white, 2 px grey. */
    static void paintOverview(dve overview, SYo g, double boundaryX) {
        try {
            double width = overview.Sxo();
            double height = overview.yiX();
            if (!(width > 0.0) || !(height > 0.0)) return;
            double x = boundaryX;
            if (width >= 8.0) x = Math.max(4.0, Math.min(width - 4.0, x));
            g.cfM(x - 4.0, 0.0, 8.0, height, SHOULDER);
            g.cfM(x - 2.0, 0.0, 4.0, height, core(overview));
        } catch (Throwable t) {
            logOnce(t);
        }
    }

    /** The marker's core: the theme's drop indicator colour (white in Bitwig's theme). */
    private static iok core(dve widget) {
        try {
            eeY colour = widget.jl_().BAa().cfM("Drop Indicator"); // the theme's colours, by name
            iok c = colour != null ? colour.cfM(widget) : null;
            return c != null ? c : WHITE;
        } catch (Throwable t) {
            return WHITE;
        }
    }

    private static void logOnce(Throwable t) {
        if (loggedError) return;
        loggedError = true;
        MOD.log("Painting the insertion marker failed", t);
    }
}
