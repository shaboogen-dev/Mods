package com.bitwig.mods.linewaveforms;

import bitwig.obf.aGA; // the audio port scope widgets
import bitwig.obf.aTj; // the display scope widgets
import bitwig.obf.amO; // an audio file's peak and sample data
import bitwig.obf.bsa; // Bitwig's audio waveform painter
import bitwig.obf.dve; // a widget
import bitwig.obf.eEN; // gives the time of each drawn step
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.This;
import com.bitwig.graphics.SYo; // a graphics context

/**
 * The mod's hooks, all in one class because the loader calls one helper class.
 *
 * All audio waveforms in the Arranger, the launcher, the Detail Editor and the Sampler pass through one read
 * ({@link #noteRead}, {@link #readSamples}) and one draw ({@link #prepareDraw}, {@link #draw}); the rest of
 * the work is in {@link LanePainter}. With the option off each hook is one field read and Bitwig's own call.
 */
public final class LineWaveforms {
    private static final Bml.Mod MOD = Bml.mod("line-waveforms");

    /** Set after any failure: Bitwig's own drawing for the rest of the session. */
    private static volatile boolean failed;

    private LineWaveforms() {
    }

    /** Runs before each channel of each clip is read: how many samples fall on one step of the drawing. */
    @Hook(target = amO.class, method = "cfM(Lcom/bitwig/samplefile/WzZ;ILeEN;IDDLqc1;)[F", at = At.ENTRY, id = "note-read")
    public static void noteRead(@This final amO file, @Arg(1) final int steps, @Arg(4) final double start, @Arg(5) final double end) {
        if (!Options.lineWaveforms() || failed) {
            return;
        }
        LanePainter.current().noteRead(Math.abs(end - start) * file.hUR() / steps); // the file's sample rate
    }

    /** Replaces Bitwig's read of raw samples, which has zero as part of every step's range. */
    @Hook(target = amO.class, method = "cfM(Lcom/bitwig/samplefile/WzZ;ILeEN;IDDLqc1;)[F", at = At.REPLACE_CALL,
          callOwner = amO.class, call = "cfM(IDLeEN;[FIIID[FI)V", occurrence = 1, id = "read-samples")
    public static void readSamples(final int channel, final double start, final eEN offsets, final float[] frames,
                                   final int first, final int length, final int channels, final double rate,
                                   final float[] out, final int steps) {
        if (!Options.lineWaveforms() || failed) {
            amO.cfM(channel, start, offsets, frames, first, length, channels, rate, out, steps); // Bitwig's own read
            return;
        }
        SampleReader.read(channel, start, offsets, frames, first, length, channels, rate, out, steps);
        LanePainter.current().noteRawRead();
    }

    /**
     * Runs before Bitwig's painter touches the values. Its square root and level brightness then run on
     * values that have zero in their range, as they always do, whatever is drawn afterwards.
     */
    @Hook(target = bsa.class, method = "cfM(Ldve;Lcom/bitwig/graphics/SYo;[Z[FI[IJJJIDZZZ)V", at = At.ENTRY, id = "prepare-draw")
    public static void prepareDraw(@Arg(0) final dve widget, @Arg(3) final float[] values, @Arg(5) final int[] colours,
                                   @Arg(10) final double gain, @Arg(12) final boolean perceptual) {
        if (!Options.lineWaveforms() || failed || widget instanceof aGA || widget instanceof aTj) {
            return; // scopes stay as they are
        }
        try {
            LanePainter.current().prepare(values, colours, gain, perceptual);
        } catch (final Throwable t) {
            fail(t);
        }
    }

    /** Replaces Bitwig's one native waveform call. */
    @Hook(target = bsa.class, method = "cfM(Ldve;Lcom/bitwig/graphics/SYo;[Z[FI[IJJJIDZZZ)V", at = At.REPLACE_CALL,
          callOwner = SYo.class, call = "cfM(JJII[Z[F[IIZ)V", occurrence = 1, id = "draw")
    public static void draw(final SYo graphics, final long x, final long y, final int columns, final int height,
                            final boolean[] shown, final float[] values, final int[] colours, final int steps, final boolean fromZero) {
        if (!Options.lineWaveforms() || failed) {
            graphics.cfM(x, y, columns, height, shown, values, colours, steps, fromZero); // Bitwig's own drawing
            return;
        }
        try {
            LanePainter.current().draw(graphics, x, y, columns, height, shown, values, colours, steps, fromZero);
        } catch (final Throwable t) {
            fail(t);
        }
    }

    private static void fail(final Throwable t) {
        failed = true;
        MOD.log("drawing failed, using Bitwig's own waveforms until Bitwig restarts", t);
    }
}
