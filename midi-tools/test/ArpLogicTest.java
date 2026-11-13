package com.bitwig.mods.miditools;

import java.util.List;
import java.util.stream.Collectors;

/** Run through Bitwig's rounding of note times (1/1048576 of a beat). No Bitwig needed. */
public final class ArpLogicTest {
    static double round(double beats) {
        return Math.rint(beats * 1048576.0) / 1048576.0;
    }

    static ArpLogic.In note(int i, int key, double start, double end) {
        return new ArpLogic.In(i, key, round(start), round(end));
    }

    static List<Integer> keys(ArpLogic.Style style) {
        // 4 beats of 1/8 steps: 8 steps, enough to show each style's first round.
        List<ArpLogic.In> chord = List.of(note(0, 60, 0, 4), note(1, 64, 0, 4), note(2, 67, 0, 4));
        return ArpLogic.plan(chord, style, ArpLogic.Rate.EIGHTH, 100, 1, 12).notes().stream().map(ArpLogic.Out::key).collect(Collectors.toList());
    }

    public static void main(String[] args) {
        check(ArpLogic.Style.values().length == 18, "18 styles");
        check(keys(ArpLogic.Style.UP).equals(List.of(60, 64, 67, 60, 64, 67, 60, 64)), "up");
        check(keys(ArpLogic.Style.DOWN).equals(List.of(67, 64, 60, 67, 64, 60, 67, 64)), "down");
        check(keys(ArpLogic.Style.UP_DOWN).equals(List.of(60, 64, 67, 64, 60, 64, 67, 64)), "updown");
        check(keys(ArpLogic.Style.DOWN_UP).equals(List.of(67, 64, 60, 64, 67, 64, 60, 64)), "downup");
        check(keys(ArpLogic.Style.UP_AND_DOWN).equals(List.of(60, 64, 67, 67, 64, 60, 60, 64)), "up & down");
        check(keys(ArpLogic.Style.CONVERGE).equals(List.of(60, 67, 64, 60, 67, 64, 60, 67)), "converge");
        check(keys(ArpLogic.Style.PINKY_UP).equals(List.of(60, 67, 64, 67, 60, 67, 64, 67)), "pinky");
        check(keys(ArpLogic.Style.THUMB_UP).equals(List.of(60, 64, 60, 67, 60, 64, 60, 67)), "thumb");
        // Random styles repeat for the same chord, and every style only uses the chord's keys.
        for (ArpLogic.Style style : ArpLogic.Style.values()) {
            check(keys(style).equals(keys(style)), style + " repeats");
            check(List.of(60, 64, 67).containsAll(keys(style)), style + " keys");
        }
        // Chord Trigger plays all keys at every step.
        check(keys(ArpLogic.Style.CHORD_TRIGGER).size() == 24, "chord trigger");

        // Each chord note is used once as itself; the rest are copies.
        List<ArpLogic.In> chord = List.of(note(0, 60, 0, 4), note(1, 64, 0, 4), note(2, 67, 0, 4));
        ArpLogic.Plan plan = ArpLogic.plan(chord, ArpLogic.Style.UP, ArpLogic.Rate.EIGHTH, 100, 1, 12);
        check(plan.notes().stream().filter(ArpLogic.Out::sameNote).count() == 3 && plan.unused().isEmpty(), "same notes");
        // A coarse rate has no use for some notes.
        ArpLogic.Plan coarse = ArpLogic.plan(List.of(note(0, 60, 0, 1), note(1, 64, 0, 1), note(2, 67, 0, 1)), ArpLogic.Style.UP, ArpLogic.Rate.QUARTER, 100, 1, 12);
        check(coarse.notes().size() == 1 && coarse.unused().equals(List.of(1, 2)), "coarse");
        // Gate shortens each step.
        check(close(plan.notes().get(0).end(), 0.25) == false, "full gate");
        check(close(ArpLogic.plan(chord, ArpLogic.Style.UP, ArpLogic.Rate.EIGHTH, 50, 1, 12).notes().get(0).end(), 0.25), "gate 50");
        // Steps and Distance: two steps an octave apart.
        List<Integer> octave = ArpLogic.plan(chord, ArpLogic.Style.UP, ArpLogic.Rate.EIGHTH, 100, 2, 12).notes().stream().map(ArpLogic.Out::key).collect(Collectors.toList());
        check(octave.subList(0, 6).equals(List.of(60, 64, 67, 72, 76, 79)), "two steps");
        // Above 127 is dropped.
        check(ArpLogic.plan(List.of(note(0, 120, 0, 1)), ArpLogic.Style.UP, ArpLogic.Rate.QUARTER, 100, 2, 24).notes().get(0).key() == 120, "range");

        // The triplet case: a chord exactly a bar of 1/8T steps long has exactly 12 steps, however its times round.
        for (int offsetThirds = 0; offsetThirds < 12; offsetThirds++) {
            double start = offsetThirds / 3.0;
            List<ArpLogic.In> triplets = List.of(note(0, 60, start, start + 4), note(1, 64, start, start + 4));
            ArpLogic.Plan t = ArpLogic.plan(triplets, ArpLogic.Style.UP, ArpLogic.Rate.EIGHTH_TRIPLET, 100, 1, 12);
            check(t.notes().size() == 12, "12 steps from third " + offsetThirds + ": " + t.notes().size());
            for (ArpLogic.Out out : t.notes()) {
                check(out.end() - out.start() > 0.3, "no sliver at the end");
            }
        }
        System.out.println("arpeggiate-notes logic: PASS");
    }

    static boolean close(double a, double b) {
        return Math.abs(a - b) <= 2.0 / 1048576.0;
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }
}
