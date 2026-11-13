package com.bitwig.mods.subdivisionbasedoperations;

/** What the nudge and resize actions do to one note. Nothing of Bitwig's, so it can be tested alone. */
final class GridRule {
    private static final double SLIVER = 1.0 / 960.0; // a valid note length below every grid size
    // Bitwig stores times in 1/1048576ths of a beat. A note on a line is up to that far from the exact line, so
    // nothing here counts as off a line or off a whole length within two of them, in beats whatever the step.
    private static final double TOLERANCE = 2.0 / 1048576.0;

    private GridRule() {
    }

    /**
     * The note's new end. A length that isn't a whole number of steps goes to the next whole number in that
     * direction. A whole number moves by one step, with the sliver below one step. Worked out from the start
     * rather than the end, so rounding doesn't build up from press to press.
     */
    static double newEnd(double start, double end, double step, int direction) {
        double length = end - start;
        double steps = length / step;
        double nearest = Math.rint(steps);
        boolean whole = nearest >= 1 && Math.abs(length - nearest * step) <= TOLERANCE;
        if (direction < 0) {
            if (whole) {
                return nearest <= 1 ? start + SLIVER : start + (nearest - 1) * step;
            }
            return steps < 1 ? start + SLIVER : start + Math.floor(steps) * step;
        }
        if (length <= SLIVER + TOLERANCE) {
            return start + step;
        }
        return start + (whole ? nearest + 1 : Math.ceil(steps)) * step;
    }

    /**
     * The note's new start. A note on a line moves to the next line. After the grid has changed, one off the
     * grid goes to the nearest line in that direction; otherwise it moves one step and stays off the grid.
     */
    static double newStart(double start, double step, int direction, boolean gridChanged) {
        double units = start / step;
        double nearest = Math.rint(units);
        if (Math.abs(start - nearest * step) <= TOLERANCE) {
            return (nearest + direction) * step;
        }
        if (gridChanged) {
            return (direction < 0 ? Math.floor(units) : Math.ceil(units)) * step;
        }
        return start + direction * step;
    }
}
