package com.bitwig.mods.miditools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Note Articulation's logic, in plain values: each note's start stays, its end changes. */
final class ArticulationLogic {
    /** How long the notes are held. */
    enum Style {
        LEGATO("Legato"), TENUTO("Tenuto"), STACCATO("Staccato");

        final String label;

        Style(String label) {
            this.label = label;
        }
    }

    /** A note, and its group: notes are measured against the other notes of their clip. */
    record In(int index, int group, int channel, int key, double start, double end) {
    }

    /** A note's new end. */
    record Out(int index, double end) {
    }

    static final double SHORTEST_BEATS = 0.01;

    private ArticulationLogic() {
    }

    /**
     * Legato holds each note to the next later start among the selected notes of its clip (the last ones keep their
     * end). Tenuto keeps the written end. Staccato holds each note for half of the shorter of its own length and the
     * shortest distance between two of the selection's starts. Offset then adds (or takes) that percentage of a grid
     * step to every note, and variation moves each note's end by up to that percentage of a grid step, shorter above
     * 0, longer below, always the same for the same note. A note is never left shorter than a hundredth of a beat.
     * Only notes that change are returned.
     */
    static List<Out> plan(List<In> notes, Style style, double gridBeats, int offsetPercent, int variationPercent) {
        double offset = clamp(offsetPercent) / 100.0 * gridBeats;
        int variation = clamp(variationPercent);
        Map<Integer, List<In>> groups = new TreeMap<>();
        for (In note : notes) {
            groups.computeIfAbsent(note.group(), g -> new ArrayList<>()).add(note);
        }
        List<Out> out = new ArrayList<>();
        for (List<In> unsorted : groups.values()) {
            List<In> group = new ArrayList<>(unsorted);
            group.sort(Comparator.comparingDouble(In::start).thenComparingInt(In::key).thenComparingInt(In::channel)
                                 .thenComparingDouble(In::end));
            List<Double> starts = distinctStarts(group);
            double spacing = smallestSpacing(starts);
            for (int ordinal = 0; ordinal < group.size(); ordinal++) {
                In note = group.get(ordinal);
                double length = note.end() - note.start();
                double base = switch (style) {
                    case LEGATO -> nextStart(note, starts) - note.start();
                    case TENUTO -> length;
                    case STACCATO -> Math.min(length, spacing) / 2.0;
                };
                double held = base + offset + variationDelta(note, ordinal, variation, gridBeats);
                double end = note.start() + Math.max(SHORTEST_BEATS, held);
                if (Math.abs(end - note.end()) > ClearLogic.TOLERANCE) {
                    out.add(new Out(note.index(), end));
                }
            }
        }
        return out;
    }

    /** Starts closer than the rounding of note times count as one. */
    private static List<Double> distinctStarts(List<In> sorted) {
        List<Double> starts = new ArrayList<>();
        for (In note : sorted) {
            if (starts.isEmpty() || note.start() - starts.get(starts.size() - 1) > ClearLogic.TOLERANCE) {
                starts.add(note.start());
            }
        }
        return starts;
    }

    private static double nextStart(In note, List<Double> starts) {
        for (double start : starts) {
            if (start > note.start() + ClearLogic.TOLERANCE) {
                return start;
            }
        }
        return note.end();
    }

    private static double smallestSpacing(List<Double> starts) {
        double smallest = Double.POSITIVE_INFINITY;
        for (int i = 1; i < starts.size(); i++) {
            smallest = Math.min(smallest, starts.get(i) - starts.get(i - 1));
        }
        return smallest;
    }

    /** Above 0 shortens, below 0 lengthens. */
    private static double variationDelta(In note, int ordinal, int percent, double gridBeats) {
        if (percent == 0) {
            return 0.0;
        }
        long hash = 0x9e3779b97f4a7c15L;
        hash = mix(hash ^ note.key());
        hash = mix(hash ^ note.channel());
        hash = mix(hash ^ Double.doubleToLongBits(note.start()));
        hash = mix(hash ^ Double.doubleToLongBits(note.end()));
        double magnitude = (mix(hash ^ ordinal) >>> 11) * 0x1.0p-53 * Math.abs(percent) / 100.0 * gridBeats;
        return percent > 0 ? -magnitude : magnitude;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static int clamp(int value) {
        return Math.max(-100, Math.min(100, value));
    }
}
