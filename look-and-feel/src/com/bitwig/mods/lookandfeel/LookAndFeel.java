package com.bitwig.mods.lookandfeel;

import bitwig.obf.dJN; // a wheel event for a widget
import bitwig.obf.dSX; // a scroller's offset
import bitwig.obf.dTE; // the wheel handler of every normal scrolling view
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.This;

/**
 * The mod's hooks, all in one class because the loader calls one helper class. The work is in
 * {@link SmoothScroll}.
 */
public final class LookAndFeel {
    private LookAndFeel() {
    }

    /** Runs first for each wheel event in a scrolling view. */
    @Hook(target = dTE.class, method = "cfM(LdJN;)V", at = At.ENTRY, id = "wheel-input")
    public static void wheelInput(@This final dTE handler, @Arg(0) final dJN input) {
        SmoothScroll.wheelInput(handler, input);
    }

    /** Replaces the wheel's jump when its input is counted in notches. */
    @Hook(target = dTE.class, method = "cfM(LdJN;)V", at = At.REPLACE_CALL,
          callOwner = dSX.class, call = "cfM(IZD)V", occurrence = 1, id = "wheel-notches")
    public static void wheelNotches(final dSX model, final int steps, final boolean fine, final double scale) {
        SmoothScroll.wheelNotches(model, steps, fine, scale);
    }

    /** Replaces the wheel's jump when its input is counted in pixels. */
    @Hook(target = dTE.class, method = "cfM(LdJN;)V", at = At.REPLACE_CALL,
          callOwner = dSX.class, call = "IwV(J)V", occurrence = 1, id = "wheel-pixels")
    public static void wheelPixels(final dSX model, final long pixels) {
        SmoothScroll.wheelPixels(model, pixels);
    }
}
