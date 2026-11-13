package com.bitwig.mods.miditools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Time Warp's logic, in plain values. */
final class TimeWarpLogic {
    /** Which way the timing bends. */
    enum Mode {
        SPEED_UP("Speed Up"), SLOW_DOWN("Slow Down");

        final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    /** A note's times, and its group: notes are warped with the other notes of their clip. */
    record In(int index, int group, double start, double end) {
    }

    /** Where a note goes. */
    record Out(int index, double start, double end) {
    }

    static final double SHORTEST_NOTE_BEATS = 0.01;

    private TimeWarpLogic() {
    }

    /**
     * Each group's range runs from its earliest start to its latest end, and both ends of the range stay where they
     * are. Every time inside is bent along a curve: with Speed Up the notes near the start of the range spread out
     * and those near its end crowd together, so the notes come faster and faster; Slow Down is the reverse. Amount 0
     * changes nothing; 100 is the strongest bend (a power of 4 or 1/4). keepLengths moves each note keeping its length;
     * otherwise its end is bent too. Only notes that change are returned.
     */
    static List<Out> plan(List<In> notes, Mode mode, int amountPercent, boolean keepLengths) {
        double amount = Math.max(0, Math.min(100, amountPercent)) / 100.0;
        double exponent = mode == Mode.SPEED_UP ? 1.0 / (1.0 + 3.0 * amount) : 1.0 + 3.0 * amount;
        Map<Integer, double[]> ranges = new HashMap<>(); // group -> {earliest start, latest end}
        for (In note : notes) {
            double[] range = ranges.computeIfAbsent(note.group(), g -> new double[] {note.start(), note.end()});
            range[0] = Math.min(range[0], note.start());
            range[1] = Math.max(range[1], note.end());
        }
        List<Out> placed = new ArrayList<>();
        for (In note : notes) {
            double[] range = ranges.get(note.group());
            double start = bend(note.start(), range[0], range[1], exponent);
            double end = keepLengths ? note.end() + (start - note.start()) : bend(note.end(), range[0], range[1], exponent);
            end = Math.max(end, start + SHORTEST_NOTE_BEATS);
            if (Math.abs(start - note.start()) > ClearLogic.TOLERANCE || Math.abs(end - note.end()) > ClearLogic.TOLERANCE) {
                placed.add(new Out(note.index(), start, end));
            }
        }
        return placed;
    }

    /** A time between from and to, bent: its place in the range, 0 to 1, raised to the exponent. */
    static double bend(double time, double from, double to, double exponent) {
        double span = to - from;
        if (!(span > 0)) {
            return time;
        }
        double place = Math.max(0.0, Math.min(1.0, (time - from) / span));
        return from + Math.pow(place, exponent) * span;
    }
}
