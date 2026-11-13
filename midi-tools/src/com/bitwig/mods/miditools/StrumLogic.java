package com.bitwig.mods.miditools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Strum's musical logic, in plain values. */
final class StrumLogic {
    /** A note's times, and what keeps chords apart: its clip and channel. */
    record In(int index, int clip, int channel, int key, double start, double end) {
    }

    /** Where a note goes. */
    record Out(int index, double start, double end) {
    }

    static final double FULL_SPREAD_BEATS = 0.5; // 100% is half a beat
    private static final double SAME_CHORD_BEATS = 0.0625; // notes this close to a chord's first note are in it
    private static final double SHORTEST_NOTE_BEATS = 0.01;

    private StrumLogic() {
    }

    /**
     * Moves each chord's notes by their key: the lowest by low percent, the highest by high percent, the others
     * between, bent by tension. A chord that would start before the clip's beat 0 moves later as a whole.
     */
    static List<Out> plan(List<In> notes, int lowPercent, int highPercent, int tensionPercent) {
        double low = clamp(lowPercent) / 100.0 * FULL_SPREAD_BEATS;
        double high = clamp(highPercent) / 100.0 * FULL_SPREAD_BEATS;
        List<Out> placed = new ArrayList<>();
        for (List<In> chord : chords(notes)) {
            int lowest = chord.stream().mapToInt(In::key).min().orElse(0);
            int highest = chord.stream().mapToInt(In::key).max().orElse(lowest);
            if (chord.size() < 2 || lowest == highest) {
                continue;
            }
            double[] offsets = new double[chord.size()];
            double earliest = Double.POSITIVE_INFINITY;
            for (int i = 0; i < offsets.length; i++) {
                double progress = bend((double) (chord.get(i).key() - lowest) / (highest - lowest), tensionPercent);
                offsets[i] = (1.0 - progress) * low + progress * high;
                earliest = Math.min(earliest, chord.get(i).start() + offsets[i]);
            }
            double shift = Math.max(0.0, -earliest);
            for (int i = 0; i < offsets.length; i++) {
                In note = chord.get(i);
                double start = note.start() + offsets[i] + shift;
                placed.add(new Out(note.index(), start, Math.max(start + SHORTEST_NOTE_BEATS, note.end() + offsets[i] + shift)));
            }
        }
        return placed;
    }

    /** Above 0 it starts slow and speeds up, below 0 it starts fast and slows down. */
    static double bend(double progress, int tensionPercent) {
        if (progress <= 0.0) {
            return 0.0;
        }
        if (progress >= 1.0) {
            return 1.0;
        }
        double tension = clamp(tensionPercent) / 100.0;
        if (tension > 0.0) {
            return Math.pow(progress, 1.0 + 2.5 * tension);
        }
        return tension < 0.0 ? 1.0 - Math.pow(1.0 - progress, 1.0 - 2.5 * tension) : progress;
    }

    /** Notes of one clip and channel that start together, within a 1/64 note of the first. */
    private static List<List<In>> chords(List<In> notes) {
        Map<Long, List<In>> byClipAndChannel = new HashMap<>();
        for (In note : notes) {
            byClipAndChannel.computeIfAbsent(((long) note.clip() << 8) | note.channel(), k -> new ArrayList<>()).add(note);
        }
        List<List<In>> chords = new ArrayList<>();
        for (List<In> part : byClipAndChannel.values()) {
            part.sort(Comparator.comparingDouble(In::start).thenComparingInt(In::key));
            List<In> chord = new ArrayList<>();
            for (In note : part) {
                if (!chord.isEmpty() && Math.abs(note.start() - chord.get(0).start()) > SAME_CHORD_BEATS) {
                    chords.add(chord);
                    chord = new ArrayList<>();
                }
                chord.add(note);
            }
            chords.add(chord);
        }
        return chords;
    }

    private static int clamp(int percent) {
        return Math.max(-100, Math.min(100, percent));
    }
}
