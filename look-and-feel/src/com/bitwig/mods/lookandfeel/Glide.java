package com.bitwig.mods.lookandfeel;

/**
 * The arithmetic of one scroller's glide, and of turning wheel input into a distance. No Bitwig types, so it
 * can be run on its own.
 *
 * Each frame the view covers a share of the distance left, worked out from the time since the last frame. A
 * slow frame moves further, so a glide takes the same time at any frame rate. It starts at once, slows towards
 * the end and can't pass the target. New input only moves the target, so a spinning wheel never restarts it.
 */
final class Glide {
    /**
     * How quickly a glide moves; the value to tune. In this many seconds the view covers about 63% of the
     * distance left, and it arrives after about 5 times this: a 108 pixel wheel notch takes about 135 ms.
     */
    static final double GLIDE_SECONDS = 0.025;

    /** Closer than this to the target, the next frame arrives. Half a pixel can't be seen as a step. */
    private static final double ARRIVED_PIXELS = 0.5;

    /**
     * Input smaller than this share of a wheel notch moves at once, without gliding: a trackpad's stream of
     * small movements would otherwise trail behind the fingers.
     */
    static final double SMALL_INPUT_NOTCHES = 0.2;

    /** Windows' wheel delta for one notch. */
    private static final double WINDOWS_NOTCH_DELTA = 120.0;

    /** How much faster Shift makes pixel input on macOS and Linux, as in Bitwig. */
    private static final double FAST_PIXELS = 5.0;

    private double position;
    private double target;
    private long seen;
    private long requested;

    void begin(final long offset, final double from) {
        position = from;
        target = from;
        seen = offset;
        requested = offset;
    }

    double target() {
        return target;
    }

    void setTarget(final double value) {
        target = value;
    }

    /** Input small enough to apply at once moves the view and the target together. */
    void shift(final double distance) {
        position += distance;
        target += distance;
    }

    /** Something else wrote the offset if it's neither what we last read back nor what we last asked for. */
    boolean movedElsewhere(final long offset) {
        return offset != seen && offset != requested;
    }

    /** One frame. Returns the whole-pixel offset to write. */
    long advance(final double seconds) {
        position += (target - position) * -Math.expm1(-seconds / GLIDE_SECONDS);
        if (arrived()) {
            position = target;
        }
        return Math.round(position);
    }

    boolean arrived() {
        return Math.abs(target - position) < ARRIVED_PIXELS;
    }

    /** What was asked for and what the scroller holds afterwards: its limits may have changed it. */
    void written(final long asked, final long offset) {
        requested = asked;
        seen = offset;
    }

    /** The fraction of a pixel that whole-pixel offsets couldn't show. */
    double leftOver() {
        return target - Math.round(target);
    }

    /**
     * Windows sends a wheel delta, 120 to a notch, and Bitwig reads anything else as pixels, which makes a
     * delta of 119 move further than one of 120 at most display scales. Here every delta is its share of a
     * notch, so distance always follows the size of the input.
     */
    static double windowsDistance(final int delta, final long notchPixels) {
        return delta * notchPixels / WINDOWS_NOTCH_DELTA;
    }

    /**
     * macOS and Linux send pixels with fractions, and Bitwig keeps only the whole part: macOS drops the
     * fraction and Linux rounds it, so slow trackpad movement is lost. This uses the exact value, unless it
     * doesn't belong with the whole number Bitwig was given.
     */
    static double exactDistance(final double exact, final long wholePixels, final double scale, final boolean fast) {
        final double factor = fast ? scale * FAST_PIXELS : scale;
        final double distance = exact * factor;
        return Math.abs(distance - wholePixels) <= 2.0 * factor ? distance : wholePixels;
    }
}
