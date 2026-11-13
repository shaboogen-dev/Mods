package com.bitwig.mods.notificationhistory;

import bitwig.obf.VGL; // a value change: the new value and the old
import bitwig.obf.dAa; // a push button
import bitwig.obf.dJV; // makes a widget for a model object
import bitwig.obf.dKK; // a popup
import bitwig.obf.dMP; // the notification centre's list
import bitwig.obf.dOZ; // an edge of a popup's anchor
import bitwig.obf.dPi; // where a popup goes: next to its anchor
import bitwig.obf.dTA; // a scroll view
import bitwig.obf.dWb; // a vertical or horizontal group of widgets
import bitwig.obf.dWo; // a list of widgets made from a model
import bitwig.obf.dnF; // margins (and border, background) around one widget
import bitwig.obf.dpN; // Bitwig's application
import bitwig.obf.duY; // a one-line text label
import bitwig.obf.dve; // a widget
import bitwig.obf.foz; // a listener
import bitwig.obf.kn; // the size a widget's content asks for
import bitwig.obf.ruA; // a widget's content sizes
import bitwig.obf.sO3; // literal (untranslated) text
import bitwig.obf.svm; // a widget's size policy
import bitwig.obf.Sxr; // a fixed size
import bitwig.obf.w3; // Bitwig's standard sizes
import bitwig.obf.yqe; // the notification centre's view


/**
 * The notification panel a left click on the dot opens or closes (the overlay's shown flag and the
 * dot's icon are left as they are): a popup anchored to the dot, as Bitwig's own popup of hidden
 * tasks is made. A header ("Notifications" and Clear), the live entries (Bitwig's own list on the
 * centre's view, so tasks keep their progress and Cancel), then the history, newest first, all in a
 * scroll view. Built from Bitwig's widgets only. UI thread.
 */
final class Panel {
    /** Bitwig's own task popup is this wide. */
    private static final long WIDTH = 340;
    /** The panel's height at most; it scrolls beyond. */
    private static final long MAX_HEIGHT = 480;
    /** A press that closed the panel (a click away, on the dot) is followed by the dot's click: not a reopen. */
    private static final long REOPEN_GUARD_MS = 300;

    private static dKK popup;
    private static long closedAt;
    private static boolean closing;
    private static Runnable watcher; // keeps the empty-history line up to date while the panel is open

    private Panel() { }

    /** The dot was clicked. False when the panel couldn't open: the caller then does Bitwig's own click. */
    static boolean toggle(dve dot) {
        if (popup != null) {
            close();
            return true;
        }
        if (System.currentTimeMillis() - closedAt < REOPEN_GUARD_MS) return true; // the press on the dot just closed it
        return open(dot);
    }

    private static boolean open(dve dot) {
        if (dot == null) return false;
        yqe view = dpN.TVZ().YNG(); // the centre's view
        if (view == null) return false;
        dve content = null;
        try {
            content = content(view, dot);
            content.a_(new Sxr(WIDTH)); // its width
            dKK p = dKK.MRY(dot);       // a popup anchored to the dot
            p.hUR(content);             // its content
            p.cfM(new dPi(dot, dOZ.MRY, dOZ.IwV), true); // shown next to the dot, as Bitwig places its task popup
            if (!p.Pfd()) {             // not shown
                unwatch();
                return false;
            }
            popup = p;
            p.lYc(new Closed(p));       // told when it closes; the overlay's shown flag is left alone
            return true;
        } catch (Throwable t) {
            unwatch();
            Log.MOD.log("the notification panel failed; the dot's own click instead", t);
            return false;
        }
    }

    /** Closes the panel, if it is open. */
    static void close() {
        dKK p = popup;
        if (p == null) return;
        closed(p);
    }

    private static void closed(dKK p) {
        if (popup != p) return;
        popup = null;
        closedAt = System.currentTimeMillis();
        unwatch();
        if (closing) return;
        closing = true;
        try {
            p.pYg(); // closes the popup, as Bitwig's task popup does
        } catch (Throwable t) {
            Log.MOD.log("could not close the notification panel", t);
        } finally {
            closing = false;
        }
    }

    private static void unwatch() {
        Runnable w = watcher;
        watcher = null;
        if (w != null) Recorder.history().unlisten(w);
    }

    /** The panel's content. {@code theme} gives the colours and fonts (the dot, already on screen). */
    private static dve content(yqe view, dve theme) {
        dWb column = dWb.UNj(4); // vertical

        // Header: "Notifications", a stretch, Clear.
        dWb line = dWb.ELS(); // horizontal
        line.UNj(0.5F);       // centred vertically, as a message row's title line
        line.HL(Looks.title(theme, "Notifications")); // bold, the notification text colour
        line.JJY(); // stretch
        dAa clear = dAa.nVd(new sO3("Clear")); // a push button, as a message's action button
        clear.tp2("Notification");             // the notification button style (a task's Cancel)
        clear.a_(kn.cfM());
        clear.MRY(w3.Hnt);
        clear.Hnt(() -> {
            try {
                Recorder.history().clear();
            } catch (Throwable t) {
                Log.MOD.log("could not clear the notification history", t);
            }
        });
        line.HL(clear);
        column.HL(card(line, theme));

        // The live entries: Bitwig's own list on the centre's view (empty takes no room).
        column.HL(dMP.cfM(view));

        // The history, newest first, rows made as Bitwig makes message rows.
        dWo past = dWo.iAj(); // a vertical list
        past.TMF(7);          // spacing, as the centre's list
        past.cfM(new PastRows());
        past.ucQ(Recorder.list()); // bound to the history: updates while the panel is open
        column.HL(past);

        // When there is no history yet.
        dWb emptyLine = dWb.ELS();
        emptyLine.HL(Looks.secondary(theme, "No past notifications")); // the description's colour
        dve emptyCard = card(emptyLine, theme);
        emptyCard.t_(Recorder.history().isEmpty());
        Runnable w = () -> {
            try {
                emptyCard.t_(Recorder.history().isEmpty());
            } catch (Throwable t) {
                Log.MOD.log("could not update the notification panel", t);
            }
        };
        watcher = w;
        Recorder.history().listen(w);
        column.HL(emptyCard);

        dTA scroll = dTA.FzB(dnF.cfM(column, 0, 2, 2, 0)); // as the centre's overlay: small margins, scrolling
        scroll.eLY(false); // no horizontal scrolling
        scroll.iHb(true);  // vertical scrolling
        scroll.MRY(new MaxHeight(MAX_HEIGHT)); // its height
        return scroll;
    }

    /** A line on the notification background, with a message row's margins. */
    private static dve card(dve inner, dve theme) {
        dnF card = dnF.cfM(inner, 15, 7, 11, 7);
        card.UNj(theme.PEn().cfM("Notification Background")); // the notification background colour
        return card;
    }

    /** Rows of the history list. Never null, never throws. */
    private static final class PastRows implements dJV {
        @Override
        public dve createWidget(Object item) {
            try {
                if (item instanceof Past past) return (dve) Recorder.row(past);
            } catch (Throwable t) {
                Log.MOD.log("a history row failed", t);
            }
            return duY.wIw(); // an empty label: the list stays intact
        }
    }

    /** The popup's visibility: when it goes, the panel is closed. */
    private static final class Closed implements foz {
        private final dKK p;

        Closed(dKK p) {
            this.p = p;
        }

        @Override
        public void eventOccurred(Object event) {
            try {
                if (event instanceof VGL change && !Boolean.TRUE.equals(change.MRY())) closed(p); // the new value: hidden
            } catch (Throwable t) {
                Log.MOD.log("the notification panel's close failed", t);
            }
        }
    }

    /** The content's height up to {@code max}, then it scrolls. */
    private static final class MaxHeight implements svm {
        private final long max;

        MaxHeight(long max) {
            this.max = max;
        }

        @Override
        public long MRY(ruA content) { // the minimum
            return Math.min(content.cfM(), cfM(content));
        }

        @Override
        public long cfM(ruA content) { // the preferred size
            return Math.min(content.MRY(), content.cfM(max)); // the content's preferred height, or max (in layout units)
        }

        @Override
        public long Hnt(ruA content) { // the maximum
            return cfM(content);
        }
    }
}
