package com.bitwig.mods.stemexportbusfx;

import bitwig.obf.kd; // the Export Audio task

import java.util.List;

/**
 * One pass of a stem queue: Bitwig's own Export Audio task with one render target, the
 * master, written to this stem's file. Everything else (formats, dither, real-time, the
 * progress window, cancelling, error messages) is Bitwig's.
 */
public final class StemPassTask extends kd {
    private final StemQueue queue;
    private final int pass;
    private boolean completed;

    StemPassTask(StemQueue queue, int pass) {
        super(queue.document, List.of(queue.master), new String[] {queue.paths[pass]}, queue.wave, queue.from, queue.to);
        this.queue = queue;
        this.pass = pass;
        if (!queue.isLast(pass)) {
            ((kd) this).cfM = null; // the folder Bitwig's task opens when it's done: only after the last stem
        }
    }

    /** The progress window's text. */
    @Override
    public String cfM() {
        return "Exporting stem " + (pass + 1) + " of " + queue.count();
    }


    /** Bitwig's warning about missing or deactivated plug-ins: before the first stem only. */
    @Override
    public boolean eLY() {
        return pass == 0 && super.eLY();
    }

    /** The user cancelled: no further stems. */
    @Override
    public void fJR() {
        queue.cancel();
        super.fJR();
    }

    /** The pass ended (success or not): Bitwig's completion, then the next stem or the restore. */
    @Override
    public void cfM(boolean success, String message) {
        if (completed) {
            return;
        }
        completed = true;
        boolean next = false;
        try {
            super.cfM(success, message);
            next = queue.passDone(pass, success);
        } catch (Throwable t) {
            StemExportBusFx.MOD.log("stem " + (pass + 1) + ": completion failed", t);
            next = false;
        } finally {
            if (!next) {
                queue.restore();
            }
        }
        if (next) {
            queue.scheduleNext();
        }
    }
}
