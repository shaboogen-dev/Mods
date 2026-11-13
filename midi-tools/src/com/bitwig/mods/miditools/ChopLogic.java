package com.bitwig.mods.miditools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Chop's musical logic, in plain values: notes in, slices out. */
final class ChopLogic {
    /** A note: its place in the list given, and what the slices depend on. */
    record In(int index, int key, int channel, double start, double end, double velocity127) {
    }

    /** A slice: the first of a note's slices is the note itself, the others are copies of it. */
    record Out(int source, boolean sameNote, double start, double end, double velocity127) {
    }

    /** The slices, and the notes with no slices at all. */
    record Plan(List<Out> slices, List<Integer> silenced) {
    }

    static final int MIN_PARTS = 2;
    static final int MAX_PARTS = 64;
    static final int MAX_SLICES = 8192; // more would make the editor unusable
    private static final double SHORTEST_BEATS = 0.01;
    /** Twice Bitwig's rounding of note times (1/1048576 of a beat): times this close are the same time. */
    private static final double TOLERANCE = 2.0 / 1048576.0;

    private ChopLogic() {
    }

    /**
     * Equal parts of each note. Above 0, gaps plays that many parts and rests one; below 0 it plays one and rests
     * that many. Variation moves each part's start and end by up to 40% of a part, always the same for the same note.
     */
    static Plan parts(List<In> notes, int parts, int gaps, int variationPercent) {
        int cells = clamp(parts, MIN_PARTS, MAX_PARTS);
        int gap = clamp(gaps, -15, 15);
        int variation = clamp(variationPercent, 0, 100);
        List<In> sorted = new ArrayList<>(notes);
        sorted.sort(Comparator.comparingDouble(In::start).thenComparingDouble(In::end)
                              .thenComparingInt(In::key).thenComparingInt(In::channel));
        List<Out> slices = new ArrayList<>();
        for (int ordinal = 0; ordinal < sorted.size(); ordinal++) {
            In note = sorted.get(ordinal);
            double cell = (note.end() - note.start()) / cells;
            double shortest = Math.min(SHORTEST_BEATS, cell);
            boolean first = true;
            int firstOfNote = slices.size();
            for (int i = 0; i < cells; i++) {
                if (!audible(i, gap)) {
                    continue;
                }
                double cellStart = i == 0 ? note.start() : note.start() + cell * i;
                double cellEnd = i == cells - 1 ? note.end() : note.start() + cell * (i + 1);
                double budget = Math.min(Math.max(0.0, cellEnd - cellStart - shortest), cell * 0.40 * variation / 100.0);
                double start = cellStart + (unit(note, ordinal, i, 0) * 2.0 - 1.0) * budget;
                double end = cellEnd + (unit(note, ordinal, i, 1) * 2.0 - 1.0) * budget;
                // Moved parts stay inside the note; where two would overlap, the earlier ends where the later begins.
                start = Math.max(note.start(), Math.min(start, end - shortest));
                end = Math.max(start + shortest, Math.min(end, note.end()));
                start = Math.max(note.start(), Math.min(start, end - shortest));
                slices.add(new Out(note.index(), first, start, end, -1));
                first = false;
            }
            // Bitwig keeps no two notes of one key over each other: its insert cuts the one already there where the
            // new one begins. Ending each part where the next begins gives that result here, so the preview shows it.
            for (int i = firstOfNote; i + 1 < slices.size(); i++) {
                Out part = slices.get(i);
                Out next = slices.get(i + 1);
                if (next.start() < part.end()) {
                    slices.set(i, new Out(part.source(), part.sameNote(), part.start(), next.start(), part.velocity127()));
                }
            }
        }
        return new Plan(slices, List.of());
    }

    private static boolean audible(int cell, int gaps) {
        if (gaps == 0) {
            return true;
        }
        int cycle = Math.abs(gaps) + 1;
        return gaps > 0 ? cell % cycle < gaps : cell % cycle == 0;
    }

    private static double unit(In note, int ordinal, int cell, int edge) {
        long hash = 0x9e3779b97f4a7c15L;
        hash = mix(hash ^ note.key());
        hash = mix(hash ^ note.channel());
        hash = mix(hash ^ Double.doubleToLongBits(note.start()));
        hash = mix(hash ^ Double.doubleToLongBits(note.end()));
        return (mix(hash ^ ((long) ordinal << 32) ^ ((long) cell << 1) ^ edge) >>> 11) * 0x1.0p-53;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    /** The length of a step of a rhythm, in beats: 16 steps fill a 4-beat bar. */
    static final double STEP_BEATS = 4.0 / RhythmPattern.STEPS;

    /**
     * A rhythm on one timeline from the earliest note's start, so chord notes stay together. Each hit runs up to the
     * next by gate percent. tailHolds lets the last hit of a note run to its end. accentPercent moves each hit's
     * velocity towards the rhythm's accent; at 0 velocities don't change. Refused by the caller when empty.
     */
    static Plan rhythm(List<In> notes, RhythmPattern rhythm, int gatePercent, int swingPercent, int accentPercent, boolean tailHolds) {
        double origin = notes.stream().mapToDouble(In::start).min().orElse(0.0);
        List<Out> slices = new ArrayList<>();
        List<Integer> silenced = new ArrayList<>();
        for (In note : notes) {
            if (note.end() - note.start() < STEP_BEATS - TOLERANCE) {
                throw new TooShort();
            }
            List<double[]> hits = new ArrayList<>(); // {start, accent}
            long firstCell = Math.max(0, (long) Math.floor((note.start() - origin) / STEP_BEATS) - 1);
            long endCell = (long) Math.ceil((note.end() - origin) / STEP_BEATS);
            for (long cell = firstCell; cell < endCell; cell++) {
                int step = (int) (cell % RhythmPattern.STEPS);
                if (!rhythm.hitAt(step)) {
                    continue;
                }
                double start = origin + cell * STEP_BEATS + swingDelay(step, swingPercent);
                if (start >= note.start() - TOLERANCE && start < note.end() - TOLERANCE) {
                    hits.add(new double[] {Math.max(note.start(), start), rhythm.accentAt(step)});
                }
            }
            if (hits.isEmpty()) {
                silenced.add(note.index());
            }
            for (int i = 0; i < hits.size(); i++) {
                double start = hits.get(i)[0];
                double next = i + 1 < hits.size() ? hits.get(i + 1)[0] : note.end();
                double end = i + 1 == hits.size() && tailHolds ? note.end() : start + gateLength(next - start, gatePercent);
                double velocity = accentPercent == 0 ? -1
                        : Math.max(1, Math.min(127, Math.round(note.velocity127() + (hits.get(i)[1] - note.velocity127()) * accentPercent / 100.0)));
                slices.add(new Out(note.index(), i == 0, start, end, velocity));
            }
        }
        return new Plan(slices, silenced);
    }

    /** From a shortest audible note up to the whole interval. */
    static double gateLength(double interval, int percent) {
        double shortest = Math.min(SHORTEST_BEATS, interval);
        return shortest + (interval - shortest) * percent / 100.0;
    }

    /** Every second step comes later, always by less than half a step. */
    static double swingDelay(int step, int swingPercent) {
        return (step & 1) == 0 ? 0.0 : STEP_BEATS * 0.499999 * swingPercent / 100.0;
    }

    /** A note shorter than a step can't take a rhythm. */
    static final class TooShort extends RuntimeException {
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
}
