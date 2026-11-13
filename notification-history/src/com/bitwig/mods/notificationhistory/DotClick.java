package com.bitwig.mods.notificationhistory;

import bitwig.obf.dJI; // a click
import bitwig.obf.dpN; // Bitwig's application
import bitwig.obf.drX; // a widget's press handler: click and tooltip
import bitwig.obf.eoV; // the keyboard modifiers of a press
import bitwig.obf.yqe; // the notification centre's view

/**
 * The dot's left-button handler: a click opens or closes the
 * notification panel, and never changes the overlay's shown flag (that is the right-click menu's
 * "Show notifications"). Press and hover look are Bitwig's (the handler it replaces adds only the click
 * and the tooltip). If the panel can't open, the click does what Bitwig's does: show or hide the
 * centre's overlay. UI thread.
 */
final class DotClick extends drX {
    @Override
    public void cfM(dJI click) { // clicked
        try {
            super.cfM(click); // Bitwig's own end of a click
        } catch (Throwable t) {
            Log.MOD.log("the dot's click failed", t);
        }
        try {
            if (Panel.toggle(this.jQ_())) return; // the widget pressed: the dot
        } catch (Throwable t) {
            Log.MOD.log("the notification panel failed; the dot's own click instead", t);
        }
        stockClick();
    }

    @Override
    public String cfM(double x, double y, eoV button) { // the tooltip
        return "Notifications and history";
    }

    /** What Bitwig's own click does: show or hide the centre's overlay. */
    static void stockClick() {
        try {
            yqe view = dpN.TVZ().YNG(); // the centre's view
            if (view == null) return;
            boolean shown = view.cfM().getBoolValue(); // the overlay's shown flag
            view.cfM().setBoolValue(!shown);
        } catch (Throwable t) {
            Log.MOD.log("the dot's own click failed too", t);
        }
    }
}
