package com.bitwig.mods.miditools;

/** Quantize's logic, in plain values: where one note's start and end go. */
final class QuantizeLogic {
    /** What is pulled to the grid. */
    enum Snap {
        START("Start"), END("End"), BOTH("Both");

        final String label;

        Snap(String label) {
            this.label = label;
        }
    }

    /** Where a note goes. */
    record Out(double start, double end) {
    }

    static final double SHORTEST_NOTE_BEATS = 0.01;

    private QuantizeLogic() {
    }

    /**
     * The note from start to end, pulled amountPercent of the way to the nearest grid line, grid lines being multiples
     * of gridBeats from the clip's beat 0. Start moves the whole note, keeping its length; End moves only the end;
     * Both moves each to its own nearest line. A note never ends up shorter than {@link #SHORTEST_NOTE_BEATS}.
     */
    static Out place(double start, double end, double gridBeats, Snap snap, int amountPercent) {
        if (!(gridBeats > 0) || !Double.isFinite(gridBeats)) {
            throw new IllegalArgumentException("the grid must be above 0");
        }
        double amount = Math.max(0, Math.min(100, amountPercent)) / 100.0;
        double toStart = start;
        double toEnd = end;
        switch (snap) {
            case START -> {
                toStart = nearest(start, gridBeats);
                toEnd = end + (toStart - start);
            }
            case END -> toEnd = Math.max(nearest(end, gridBeats), start + SHORTEST_NOTE_BEATS);
            case BOTH -> {
                toStart = nearest(start, gridBeats);
                toEnd = Math.max(nearest(end, gridBeats), toStart + SHORTEST_NOTE_BEATS);
            }
        }
        double newStart = start + (toStart - start) * amount;
        double newEnd = end + (toEnd - end) * amount;
        return new Out(newStart, Math.max(newEnd, newStart + SHORTEST_NOTE_BEATS));
    }

    static double nearest(double beat, double gridBeats) {
        return Math.round(beat / gridBeats) * gridBeats;
    }
}
