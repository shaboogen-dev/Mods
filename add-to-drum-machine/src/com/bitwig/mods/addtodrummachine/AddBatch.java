package com.bitwig.mods.addtodrummachine;

import static com.bitwig.mods.addtodrummachine.AddToDrumMachine.MOD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;

import bitwig.obf.CH; // a browser result
import bitwig.obf.KcS; // a drum pad
import bitwig.obf.XPA; // loads a device file without changing the project
import bitwig.obf.liP; // a Drum Machine's pads
import com.bitwig.base.async.lBQ; // a completion listener
import com.bitwig.base.async.oRV; // a result listener
import com.bitwig.flt.document.core.master.IG1; // what a load produced
import com.bitwig.flt.document.core.master.IOJ; // an undoable change
import com.bitwig.flt.document.core.master.device.AxS; // a Bitwig device
import com.bitwig.flt.document.core.master.device.ISf; // a device chain
import com.bitwig.flt.document.core.master.device.xlG; // a device
import com.bitwig.flt.document.core.master.device.zTz; // a drum pad's chain
import com.bitwig.flt.document.core.master.device.zZo; // a device's list of chains

/**
 * One use of the row. Everything is loaded first, with the project untouched, because a load finishes
 * later and an Undo step can't stay open across it. Then the project changes once, in one go.
 */
final class AddBatch {
    private static final String UNDO_LABEL = "Add to Drum Machine";
    private static final int C1 = 36;
    private static final int C_MINUS_2 = 0;
    private static final int PADS_FROM_C1 = 92; // notes 36 to 127
    private static final int PADS = 128;

    private final ISf chain;
    private final List<CH> rows;
    private final xlG[] devices;
    private final boolean[] answered;
    private int waiting;

    AddBatch(ISf chain, List<CH> rows) {
        this.chain = chain;
        this.rows = rows;
        this.devices = new xlG[rows.size()];
        this.answered = new boolean[rows.size()];
        this.waiting = rows.size();
    }

    void start() {
        if (!enoughPads(rows.size())) return; // nothing loaded, nothing changed
        for (int i = 0; i < rows.size(); i++) load(i);
    }

    /**
     * Which pads the content goes on, or null if the whole of it doesn't fit: the empty pads in order, from
     * C1 for up to 92 selected results, from C-2 for more. When it doesn't fit, nothing is added at all.
     */
    private static int[] choosePads(int selected, int needed, IntPredicate empty) {
        int first = selected > PADS_FROM_C1 ? C_MINUS_2 : C1;
        int[] pads = new int[needed];
        int found = 0;
        for (int note = first; note < PADS && found < needed; note++) {
            if (empty.test(note)) pads[found++] = note;
        }
        return found == needed ? pads : null;
    }

    private boolean enoughPads(int needed) {
        AxS drum = firstDrumMachine();
        IntPredicate empty = drum == null ? note -> true : emptyIn(drum);
        return choosePads(rows.size(), needed, empty) != null; // when it doesn't fit, nothing is added
    }

    // ---------- Loading ----------

    private void load(int index) {
        CH row = rows.get(index);
        try {
            if (Rows.isSample(row)) {
                loaded(index, DrumMachines.sampler(row.tp2())); // a Sampler holding the sample, as Bitwig's Alt-drag makes it
            } else {
                chain.cc_().cfM(row.tp2(), new Load(index)); // cc_(): the chain's project
            }
        } catch (Throwable t) {
            MOD.log("Load failed: " + row.tp2(), t);
            loaded(index, null);
        }
    }

    /** Runs when Bitwig has loaded one file. Must never throw: Bitwig's loader doesn't expect it to. */
    private final class Load extends XPA {
        private final int index;

        Load(int index) {
            super(chain.cc_(), false, new Listener("load " + rows.get(index).tp2(), () -> loaded(index, null)));
            this.index = index;
        }

        @Override
        public void cfM(IG1 result, oRV done) {
            try {
                loaded(index, result.MRY() ? null : (xlG) result.cfM(0).MRY()); // MRY(): empty; cfM(0).MRY(): the first device
            } catch (Throwable t) {
                MOD.log("Reading what was loaded failed: " + rows.get(index).tp2(), t);
                loaded(index, null);
            }
        }
    }

    private void loaded(int index, xlG device) {
        if (answered[index]) return; // a load can report more than once
        answered[index] = true;
        devices[index] = device;
        if (--waiting > 0) return;
        try {
            addAll();
        } catch (Throwable t) {
            MOD.log("Adding failed", t);
        }
    }

    // ---------- Changing the project ----------

    private void addAll() throws Exception {
        List<xlG> content = new ArrayList<>();
        for (xlG device : devices) if (device != null) content.add(device);
        if (content.isEmpty()) return; // no row loaded (each failure is logged)
        if (chain.cc_() == null || chain.qZj()) return; // gone from the project, or frozen, while loading
        if (!enoughPads(content.size())) return; // pads may have filled while loading
        AxS drum = firstDrumMachine();
        if (drum == null) createDrumMachine(content);
        else addToPads(drum, content);
    }

    /** Puts each device on its own pad, inside one Undo step. Nothing here waits, so all of it joins the step. */
    private void addToPads(AxS drum, List<xlG> content) {
        liP pads = (liP) DrumMachines.pads(drum); // the Drum Machine's pads
        int[] notes = choosePads(rows.size(), content.size(), emptyIn(drum));
        IOJ change = new IOJ();
        if (!change.cfM(drum, UNDO_LABEL)) { // begin
            MOD.log("Nothing added: Bitwig refused to start the Undo step");
            return;
        }
        try {
            for (int i = 0; i < notes.length; i++) {
                addToPad(pads.TtY(notes[i]), notes[i], content.get(i));
            }
        } finally {
            change.MRY(); // finish
        }
    }

    private static void addToPad(KcS pad, int note, xlG device) {
        try {
            // Bitwig's own step for a device on a pad. The device isn't in the project yet, so it runs now.
            pad.MRY(device, false, new Listener("pad " + note, () -> { }));
            if (isEmpty(pad)) MOD.log("Pad " + note + ": still empty after adding");
        } catch (Throwable t) {
            MOD.log("Pad " + note + " failed", t);
        }
    }

    /**
     * Builds a Drum Machine holding everything before it's in the project, as Bitwig's Alt-drag does for
     * samples, then inserts that one device. Bitwig's insert makes the one Undo step, and replaces another
     * instrument as an insert from the browser does.
     */
    private void createDrumMachine(List<xlG> content) throws Exception {
        AxS drum = DrumMachines.create(); // a new Drum Machine, not in the project
        zZo pads = DrumMachines.pads(drum);
        int[] notes = choosePads(rows.size(), content.size(), note -> true);
        for (int i = 0; i < notes.length; i++) {
            zTz padChain = zTz.ugU(); // a new pad chain
            padChain.KAS().MRY(content.get(i)); // KAS(): its devices
            padChain.cfM(notes[i]); // its note
            pads.lYc(padChain);
        }
        chain.MRY(Collections.singletonList(drum), false, false, new Listener("insert Drum Machine", () -> { })); // not a copy, not layered
    }

    // ---------- Looking ----------

    private AxS firstDrumMachine() {
        for (Object device : chain.HL()) { // HL(): the chain's devices, in order
            if (device instanceof AxS bitwigDevice && DrumMachines.is(bitwigDevice)) return bitwigDevice;
        }
        return null;
    }

    private static IntPredicate emptyIn(AxS drum) {
        liP pads = (liP) DrumMachines.pads(drum);
        return note -> isEmpty(pads.TtY(note));
    }

    /** No chain, or a chain with no devices. */
    private static boolean isEmpty(KcS pad) {
        zTz padChain = pad.dEI();
        return padChain == null || padChain.KAS().rGR() == 0;
    }

    /** Logs a task's failure. */
    private static final class Listener implements lBQ {
        private final String task;
        private final Runnable onFailure;

        Listener(String task, Runnable onFailure) {
            this.task = task;
            this.onFailure = onFailure;
        }

        @Override
        public void taskCompleted() {
        }

        @Override
        public void cfM(Exception failure) {
            MOD.log("Failed: " + task, failure);
            onFailure.run();
        }
    }
}
