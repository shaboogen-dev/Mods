package com.bitwig.mods.notificationhistory;

import bitwig.obf.AQw; // a boolean value that can be watched and set
import bitwig.obf.dJH; // a press
import bitwig.obf.dKW; // a popup menu
import bitwig.obf.dOZ; // an edge of a popup's anchor
import bitwig.obf.dPi; // where a popup goes: next to its anchor
import bitwig.obf.dpN; // Bitwig's application
import bitwig.obf.drX; // a widget's press handler
import bitwig.obf.dve; // a widget
import bitwig.obf.yqe; // the notification centre's view

/**
 * The dot's right-button handler: the press opens the dot's
 * menu, Bitwig's own popup menu placed as Bitwig places a button's menu, with one checkable item,
 * "Show notifications", bound to the overlay's shown flag (ticked while it is on; choosing it toggles
 * it, which is what the dot's own click did). If the menu can't open, the press does what Bitwig's
 * click did: show or hide the overlay. UI thread.
 */
final class DotMenu extends drX {
    @Override
    public void cfM(dJH press) { // pressed
        try {
            super.cfM(press);
        } catch (Throwable t) {
            Log.MOD.log("the dot's press failed", t);
        }
        try {
            if (open(this.jQ_())) return; // the widget pressed: the dot
        } catch (Throwable t) {
            Log.MOD.log("the dot's menu failed; the dot's own click instead", t);
        }
        DotClick.stockClick();
    }

    private static boolean open(dve dot) {
        if (dot == null) return false;
        yqe view = dpN.TVZ().YNG(); // the centre's view
        if (view == null) return false;
        dKW menu = dKW.FzB(dot); // Bitwig's popup menu for that widget
        menu.cfM("Show notifications", (AQw) view.cfM()); // a checkable item bound to the overlay's shown flag
        menu.cfM(new dPi(dot, dOZ.MRY, dOZ.IwV), true); // shown next to the dot, as Bitwig shows a button's menu
        return true;
    }
}
