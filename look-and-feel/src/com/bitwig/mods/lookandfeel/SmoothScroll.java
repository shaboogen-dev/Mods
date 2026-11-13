package com.bitwig.mods.lookandfeel;

import bitwig.obf.dJN; // a wheel event for a widget
import bitwig.obf.dSX; // a scroller's offset: whole pixels
import bitwig.obf.dTE; // the wheel handler of every normal scrolling view
import bitwig.obf.dmO; // called once per frame, before layout and painting
import bitwig.obf.doD; // the usual rules for a scroller's step size and limits
import bitwig.obf.dpM; // one frame's time
import bitwig.obf.drd; // Bitwig's own frame clock, as its keyboard scroll-into-view uses it
import bitwig.obf.dtA; // the rules of a scroller
import bitwig.obf.dve; // a widget
import bml.api.Bml;

/**
 * Smooth wheel scrolling. Bitwig's wheel handler moves a scroller's offset in one jump. The two hooks on those
 * moves, in {@link LookAndFeel}, add to a target instead, and each frame writes the real offset a step closer to it, in the same place
 * and the same way as Bitwig's own scroll-into-view. Clicks, linked columns and everything else that reads the
 * offset see a real value on every frame.
 *
 * All of this runs on Bitwig's event thread. A running glide is the only state that lasts beyond one event;
 * the clock is registered only while one runs. Nothing here allocates per frame.
 */
public final class SmoothScroll implements dmO {
    private static final Bml.Mod MOD = Bml.mod("look-and-feel");
    private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

    /** One scroller glides at a time per axis, normally. Beyond this many, input goes straight to the offset. */
    private static final int MOST_AT_ONCE = 4;

    private static final SmoothScroll FRAMES = new SmoothScroll();

    // The running glides, the first `running` of each array.
    private static final dSX[] MODELS = new dSX[MOST_AT_ONCE];
    private static final dve[] WIDGETS = new dve[MOST_AT_ONCE];
    private static final int[] AXES = new int[MOST_AT_ONCE];
    private static final Glide[] GLIDES = {new Glide(), new Glide(), new Glide(), new Glide()};
    private static int running;
    private static drd clock;

    // Fractions of a pixel kept for the next input, per axis, while the same scroller gets it.
    private static final dSX[] CARRY_MODELS = new dSX[2];
    private static final double[] CARRY = new double[2];

    // The wheel event being handled.
    private static boolean smoothing;
    private static dJN event;
    private static dve widget;

    /** Set after any failure: Bitwig's own wheel for the rest of the session. */
    private static boolean failed;

    private SmoothScroll() {
    }

    /** Runs first for each wheel event in a scrolling view. Notes the event, as the two calls below don't get it. */
    static void wheelInput(final dTE handler, final dJN input) {
        smoothing = Options.smoothScrolling() && !failed;
        if (!smoothing) {
            return;
        }
        event = input;
        widget = handler.jQ_();
    }

    /** Instead of the jump for wheel input counted in notches. */
    static void wheelNotches(final dSX model, final int steps, final boolean fine, final double scale) {
        if (!smoothing || !model.IwV()) { // IwV: laid out and holding an offset
            forgetEvent();
            model.cfM(steps, fine, scale); // Bitwig's own jump
            return;
        }
        try {
            moveByNotches(model, steps, fine, scale);
        } catch (final Throwable t) {
            fail(t);
            model.cfM(steps, fine, scale);
        }
        forgetEvent();
    }

    /** Instead of the jump for wheel input counted in pixels. */
    static void wheelPixels(final dSX model, final long pixels) {
        if (!smoothing || !model.IwV()) {
            forgetEvent();
            model.IwV(pixels); // Bitwig's own jump
            return;
        }
        try {
            moveByPixels(model, pixels);
        } catch (final Throwable t) {
            fail(t);
            model.IwV(pixels);
        }
        forgetEvent();
    }

    /** So a closed panel isn't held until the next wheel event. */
    private static void forgetEvent() {
        event = null;
        widget = null;
    }

    private static void moveByNotches(final dSX model, final int steps, final boolean fine, final double scale) {
        final int slot = runningSlot(model);
        final double from = from(model, slot);
        final long whole = Math.round(from);
        // Bitwig's own rules give the step and the limit, as the jump would. Lists step by rows here.
        final long to = model.iHb().cfM(whole, model.PD3(), model.Esj(), steps, fine, scale);
        if (to != whole) {
            glideTo(model, slot, from, to + (from - whole));
        }
    }

    private static void moveByPixels(final dSX model, final long pixels) {
        final boolean fine = event.E3().cfM();
        final long notch = notchPixels(model.iHb(), fine, widget.mvz());
        final double distance = pixelDistance(pixels, notch, fine);
        final int slot = runningSlot(model);
        final double from = from(model, slot);
        final double to = withinLimits(model, from + distance);
        if (to == from) {
            return;
        }
        if (Math.abs(distance) >= Glide.SMALL_INPUT_NOTCHES * notch) {
            glideTo(model, slot, from, to);
        } else if (slot >= 0) {
            GLIDES[slot].shift(to - from);
        } else {
            final long whole = Math.round(to);
            model.MRY(whole);
            keepCarry(model, axis(), to - whole);
        }
    }

    private static double pixelDistance(final long pixels, final long notch, final boolean fine) {
        if (WINDOWS) {
            return notch > 0L ? Glide.windowsDistance(event.cfM(), notch) : pixels;
        }
        final double exact = event.kFU().cfM() ? event.vpw() : event.ERO(); // the event's exact horizontal or vertical move
        return Glide.exactDistance(exact, pixels, widget.mvz(), fine);
    }

    /** A notch's distance under this scroller's rules, or 0 under rules of some other kind. */
    private static long notchPixels(final dtA rules, final boolean fine, final double scale) {
        return rules instanceof final doD usual ? Math.round(usual.cfM(fine) * usual.FzB(scale)) : 0L;
    }

    private static double withinLimits(final dSX model, final double offset) {
        final long whole = Math.round(offset);
        final long limited = model.iHb().a_(whole, model.PD3(), model.Esj());
        return limited == whole ? offset : limited;
    }

    /** The slot of this scroller's glide, or -1. If something else has moved the view, that glide ends here. */
    private static int runningSlot(final dSX model) {
        for (int i = 0; i < running; i++) {
            if (MODELS[i] == model) {
                if (GLIDES[i].movedElsewhere(model.dvk())) {
                    stop(i);
                    return -1;
                }
                return i;
            }
        }
        return -1;
    }

    /** Where new input counts from: the running glide's target, so spinning the wheel adds up. */
    private static double from(final dSX model, final int slot) {
        if (slot >= 0) {
            return GLIDES[slot].target();
        }
        final int axis = axis();
        return CARRY_MODELS[axis] == model ? model.dvk() + CARRY[axis] : model.dvk();
    }

    private static void glideTo(final dSX model, final int runningSlot, final double from, final double target) {
        int slot = runningSlot;
        if (slot < 0) {
            if (running == MOST_AT_ONCE) {
                model.MRY(Math.round(target));
                return;
            }
            slot = running++;
            MODELS[slot] = model;
            WIDGETS[slot] = widget;
            AXES[slot] = axis();
            GLIDES[slot].begin(model.dvk(), from);
            keepCarry(model, AXES[slot], 0.0);
            if (clock == null) {
                clock = drd.MRY(FRAMES);
            }
        }
        GLIDES[slot].setTarget(target);
    }

    private static int axis() {
        return event.kFU().cfM() ? 0 : 1;
    }

    private static void keepCarry(final dSX model, final int axis, final double fraction) {
        CARRY_MODELS[axis] = fraction == 0.0 ? null : model;
        CARRY[axis] = fraction;
    }

    /** Each frame while a glide runs. */
    @Override
    public void aboutToPaint(final dpM frame) {
        try {
            final double seconds = frame.FzB(); // the time since the last frame
            for (int i = running - 1; i >= 0; i--) {
                step(i, seconds);
            }
        } catch (final Throwable t) {
            fail(t);
        }
        if (running == 0) {
            stopClock();
        }
    }

    private static void step(final int slot, final double seconds) {
        final dSX model = MODELS[slot];
        final Glide glide = GLIDES[slot];
        // Follow, zoom, keyboard, drag, a scrollbar, a closed panel: whatever else moved the view has won.
        if (!WIDGETS[slot].Pfd() || glide.movedElsewhere(model.dvk())) {
            stop(slot);
            return;
        }
        final long whole = glide.advance(seconds);
        model.MRY(whole); // keeps to the limits, and writes as every other move does
        glide.written(whole, model.dvk());
        if (glide.arrived()) {
            keepCarry(model, AXES[slot], glide.leftOver());
            stop(slot);
        }
    }

    private static void stop(final int slot) {
        final int last = --running;
        final Glide stopped = GLIDES[slot];
        MODELS[slot] = MODELS[last];
        WIDGETS[slot] = WIDGETS[last];
        AXES[slot] = AXES[last];
        GLIDES[slot] = GLIDES[last];
        GLIDES[last] = stopped;
        MODELS[last] = null;
        WIDGETS[last] = null;
    }

    private static void stopClock() {
        if (clock != null) {
            clock.cfM();
            clock = null;
        }
    }

    private static void fail(final Throwable t) {
        failed = true;
        smoothing = false;
        while (running > 0) {
            stop(running - 1);
        }
        stopClock();
        MOD.log("smooth scrolling failed, using Bitwig's own wheel until Bitwig restarts", t);
    }
}
