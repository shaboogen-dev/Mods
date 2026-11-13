package com.bitwig.mods.audioanalysis;

import bitwig.obf.dWb; // a layout box: a row or a column
import bitwig.obf.dtX; // Bitwig's modal dialog
import bitwig.obf.duY; // a label
import bitwig.obf.dve; // a widget

import java.util.List;

/**
 * A native confirmation: Bitwig's own modal dialog with a title, lines of text and Bitwig's
 * standard OK and Cancel buttons. OK runs {@code onOk}; Cancel and closing do nothing.
 * Must be shown on Bitwig's UI thread.
 */
public final class ConfirmDialog extends dtX {
    private final List<String> lines;
    private final Runnable onOk;
    private dWb buttons;
    private boolean done;

    private ConfirmDialog(String title, List<String> lines, Runnable onOk) {
        super();
        this.lines = List.copyOf(lines);
        this.onOk = onOk;
        zqN(); // set the dialog up
        H_();
        MJ(title); // its title
        BDm(true);
        jFL(true);
    }

    /** Shows the dialog over {@code parent} (a widget of the window, e.g. the clicked one). */
    public static void show(Object parent, String title, List<String> lines, Runnable onOk) {
        if (!(parent instanceof dve widget)) {
            AudioAnalysis.MOD.log("no window to show \"" + title + "\" over");
            Ui.notify(title + ": open it from a right-click menu or the Commander");
            return;
        }
        new ConfirmDialog(title, lines, onOk).FzB(widget); // show it over the window
    }

    /** The text, one label per line, with a gap for each empty line. */
    @Override
    public void Hnt(dWb root) {
        dWb column = dWb.UNj(0); // a column
        for (String line : lines) {
            if (line.isEmpty()) column.Hnt(8); // a gap
            else column.HL(duY.MIO(line)); // a label
        }
        column.Hnt(12);
        buttons = dWb.IwV(8); // a row
        column.HL(buttons);
        root.HL(column);
    }

    /** Bitwig's standard dialog buttons, right-aligned (OK, Cancel). */
    @Override
    public void cfM(dWb root) {
        dWb row = buttons != null ? buttons : root;
        row.JJY(); // stretchable space
        E3(row);
        lYc(row);
        hUR(row);
        TMF(row);
        UNj(row);
        IwV(row);
        wGc(row);
        FzB(row);
    }

    /** OK. */
    @Override
    public void ke_() {
        if (done) return;
        done = true;
        try {
            onOk.run();
        } catch (Throwable t) {
            AudioAnalysis.MOD.log("confirmation action failed", t);
        }
        super.ke_(); // close
    }
}
