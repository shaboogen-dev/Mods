package com.bitwig.mods.miditools;

/** Which notes Apply clears first, in plain values. */
final class ClearLogic {
    /** Twice Bitwig's rounding of note times (1/1048576 of a beat): times this close are the same time. */
    static final double TOLERANCE = 2.0 / 1048576.0;

    private ClearLogic() {
    }

    /**
     * Whether a note from start to end overlaps a selected note's range. A note that only touches it, ending where
     * the range starts or starting where it ends, doesn't.
     */
    static boolean clears(double rangeStart, double rangeEnd, double start, double end) {
        return start < rangeEnd - TOLERANCE && end > rangeStart + TOLERANCE;
    }
}
