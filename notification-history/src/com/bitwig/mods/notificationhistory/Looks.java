package com.bitwig.mods.notificationhistory;

import bitwig.obf.dve; // a widget
import bitwig.obf.ecp; // a wrapping text

/** Texts in the looks Bitwig's own notification rows use. UI thread. */
final class Looks {
    private Looks() { }

    /** Bold, the notification text colour, as a row's title. {@code theme}: a widget on screen. */
    static ecp title(dve theme, String text) {
        ecp t = plain(text);
        t.cfM(theme.jl_().Ai().wGc());                     // the theme's bold font
        t.cfM(theme.PEn().cfM("Notification Normal"));     // the notification text colour
        return t;
    }

    /** The secondary text colour, default font, as a row's description. */
    static ecp secondary(dve theme, String text) {
        ecp t = plain(text);
        t.cfM(theme.PEn().d3());                           // the secondary text colour
        return t;
    }

    private static ecp plain(String text) {
        ecp t = ecp.Hnt(text == null ? "" : text); // a wrapping text with that text
        t.MJ(0);  // no margin, as Bitwig's task title
        t.UZr(0); // the same, the other side
        return t;
    }
}
