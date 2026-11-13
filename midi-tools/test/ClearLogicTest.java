package com.bitwig.mods.miditools;

import java.util.ArrayList;
import java.util.List;

/** The clear rule, run through Bitwig's rounding of note times (1/1048576 of a beat). No Bitwig needed. */
public final class ClearLogicTest {
    static double round(double beats) {
        return Math.rint(beats * 1048576.0) / 1048576.0;
    }

    /** {key, start, end}; returns the positions of the other notes cleared by any selected note's range. */
    static List<Integer> cleared(double[][] selected, double[][] others) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < others.length; i++) {
            for (double[] s : selected) {
                if (ClearLogic.clears(round(s[1]), round(s[2]), round(others[i][1]), round(others[i][2]))) {
                    out.add(i);
                    break;
                }
            }
        }
        return out;
    }

    public static void main(String[] args) {
        double third = 1.0 / 3.0;
        double[][] one = {{60, 1, 2}};
        // Inside, across either edge, and covering the range: cleared, on any key.
        check(cleared(one, new double[][] {{64, 1.25, 1.5}, {40, 0.5, 1.5}, {72, 1.5, 3}, {36, 0, 4}}).equals(List.of(0, 1, 2, 3)), "overlaps");
        // Touching at either end: kept. Ending exactly at the start, starting exactly at the end.
        check(cleared(one, new double[][] {{60, 0, 1}, {60, 2, 3}, {64, 2, 2.5}}).isEmpty(), "touching");
        // The same, on triplet times that Bitwig has rounded.
        double[][] triplet = {{60, third, 2 * third}};
        check(cleared(triplet, new double[][] {{60, 0, third}, {62, 2 * third, 1}}).isEmpty(), "touching triplets");
        check(cleared(triplet, new double[][] {{62, third + 0.001, 2 * third}}).equals(List.of(0)), "inside a triplet");
        // A sliver past the edge, larger than the rounding: cleared.
        check(cleared(one, new double[][] {{60, 1.999, 3}}).equals(List.of(0)), "just past the end");
        // Several selected notes: each its own range; a note in the gap between them is kept.
        double[][] two = {{60, 0, 1}, {60, 2, 3}};
        check(cleared(two, new double[][] {{64, 0.5, 0.75}, {64, 1.25, 1.75}, {64, 2.5, 2.75}}).equals(List.of(0, 2)), "own ranges");
        // A note on the same key as a selected note, overlapping it: cleared like any other.
        check(cleared(one, new double[][] {{60, 1.5, 2.5}}).equals(List.of(0)), "same key");
        System.out.println("clear rule: PASS");
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }
}
