package com.bitwig.mods.addtodrummachine;

import java.util.List;

import bitwig.obf.CH; // a browser result
import bitwig.obf.cFV; // the docked side browser
import bitwig.obf.dve; // a widget
import bitwig.obf.fP; // a track's type
import bitwig.obf.iKU; // a track
import bml.actions.ActionContext;
import bml.api.Bml;
import com.bitwig.flt.document.core.iface.RYd; // a channel: a track, group, effect track or master
import com.bitwig.flt.document.core.iface.kWY; // a group
import com.bitwig.flt.document.core.iface.pHd; // a project
import com.bitwig.flt.document.core.master.device.ISf; // a device chain

/** Decides whether the row is offered, and starts an {@link AddBatch} when it is chosen. */
final class AddToDrumMachine {
    static final Bml.Mod MOD = Bml.mod("add-to-drum-machine");

    private AddToDrumMachine() {
    }

    static boolean offered(ActionContext context) {
        cFV browser = sideBrowser(context);
        return browser != null && !Rows.eligible(context).isEmpty() && instrumentChain(browser) != null;
    }

    static void run(ActionContext context) {
        if (context.menu() == null) {
            runFromShortcut(context);
            return;
        }
        cFV browser = sideBrowser(context);
        if (browser == null) return; // not run from the side browser
        add(browser, Rows.eligible(context));
    }

    /** A shortcut or the Commander: what's selected in the side browser of the window it ran in. */
    private static void runFromShortcut(ActionContext context) {
        cFV browser = shownSideBrowser(context);
        if (browser == null) return; // the side browser isn't open in this window
        List<CH> rows = Rows.eligible(selected(browser));
        if (rows.isEmpty()) return; // nothing suitable is selected
        add(browser, rows);
    }

    private static void add(cFV browser, List<CH> rows) {
        ISf chain = instrumentChain(browser);
        if (rows.isEmpty() || chain == null) return; // no suitable rows, or no instrument or hybrid track selected
        new AddBatch(chain, rows).start();
    }

    /** The side browser the menu was opened in; null in the pop-up and file browsers. */
    private static cFV sideBrowser(ActionContext context) {
        if (!(context.widget() instanceof dve widget)) return null;
        for (dve w = widget; w != null; w = w.RbC()) { // RbC(): the parent widget
            if (w instanceof cFV browser) return browser;
        }
        return null;
    }

    /** The side browser shown in a project window; null when it's closed. */
    private static cFV shownSideBrowser(ActionContext context) {
        if (!(context.widget() instanceof dve window)) return null;
        List<?> shown = window.MRY(cFV.class, true); // the widgets of that class under this one; true: only those shown
        return shown.isEmpty() ? null : (cFV) shown.get(0);
    }

    /** The results selected in a browser: the list its result view selects in, which its right-click menu also gets. */
    private static List<?> selected(cFV browser) {
        return browser.EUG().HL().getValue() instanceof List<?> rows ? rows : List.of(); // EUG(): its session; HL(): the selected results
    }

    /** The device chain of the selected track, if it's an instrument or hybrid track that can change. */
    private static ISf instrumentChain(cFV browser) {
        pHd project = browser.gfw(); // the browser's project
        if (project == null) return null;
        Object selected = project.wIw().E3().Fsx().getValue(); // the display settings' track selection: the current track
        if (!(selected instanceof RYd channel) || channel instanceof kWY) return null;
        if (!(channel instanceof iKU track) || !track.d3()) return null; // d3(): a track, not an effect track or the master
        if (track.BNq() != fP.cfM && track.BNq() != fP.Hnt) return null; // BNq(): its type: instrument or hybrid
        if (!(channel.Ucl() instanceof ISf chain) || chain.qZj()) return null; // qZj(): frozen or locked
        return chain;
    }
}
