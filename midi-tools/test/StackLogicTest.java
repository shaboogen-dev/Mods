package com.bitwig.mods.miditools;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/** The cases the original's own tests and comments state. No Bitwig needed. */
public final class StackLogicTest {
    static BitSet major() {
        BitSet scale = new BitSet(12);
        for (int semitones : new int[] {0, 2, 4, 5, 7, 9, 11}) {
            scale.set(semitones);
        }
        return scale;
    }

    public static void main(String[] args) {
        check(StackLogic.Chord.values().length == 14, "14 chords");
        check(StackLogic.Degrees.values().length == 16, "16 patterns");
        int[] major = StackLogic.Chord.MAJOR.intervals;
        check(Arrays.equals(StackLogic.voicing(60, major, 0, 0), new int[] {60, 64, 67}), "root position");
        check(Arrays.equals(StackLogic.voicing(60, major, 1, 0), new int[] {64, 67, 72}), "first inversion");
        check(Arrays.equals(StackLogic.voicing(60, major, -1, 0), new int[] {55, 60, 64}), "drops the top");
        check(Arrays.equals(StackLogic.voicing(60, major, 0, 1), new int[] {72, 76, 79}), "octave");
        check(Arrays.equals(StackLogic.voicing(60, major, 3, 0), new int[] {72, 76, 79}), "three turns");
        // Round trip: voicings always start from the original root, so 1 then 0 is the original chord.
        check(Arrays.equals(StackLogic.voicing(60, major, 0, 0), StackLogic.voicing(60, major, 1 - 1, 0)), "round trip");
        // The root note stays when its key is in the chord.
        StackLogic.Plan keep = StackLogic.plan(60, new int[] {60, 64, 67});
        check(keep.rootKey() == 60 && keep.copies().equals(List.of(64, 67)), "root stays");
        // Otherwise it takes its own note in another octave: C3 to C4 through an inversion.
        StackLogic.Plan lifted = StackLogic.plan(60, new int[] {64, 67, 72});
        check(lifted.rootKey() == 72 && lifted.copies().equals(List.of(64, 67)), "root lifts");
        // And failing that the lowest key.
        StackLogic.Plan other = StackLogic.plan(61, new int[] {60, 64, 67});
        check(other.rootKey() == 60, "lowest");
        // The voicings Apply prints (user, 8 Nov: an inversion moving the root is musically right): Major on C3.
        StackLogic.Plan inversion1 = StackLogic.plan(60, StackLogic.voicing(60, major, 1, 0));
        check(inversion1.rootKey() == 72 && inversion1.copies().equals(List.of(64, 67)), "inversion 1: root to C4, E3 and G3 copies");
        StackLogic.Plan inversionMinus1 = StackLogic.plan(60, StackLogic.voicing(60, major, -1, 0));
        check(inversionMinus1.rootKey() == 60 && inversionMinus1.copies().equals(List.of(55, 64)), "inversion -1: root stays, G2 and E3");
        StackLogic.Plan octaveUp = StackLogic.plan(60, StackLogic.voicing(60, major, 0, 1));
        check(octaveUp.rootKey() == 72 && octaveUp.copies().equals(List.of(76, 79)), "octave +1: root to C4, E4 and G4 copies");
        // Every plan is the whole voicing: the root's key and its copies are exactly the chord's keys.
        for (StackLogic.Chord chord : StackLogic.Chord.values()) {
            if (chord == StackLogic.Chord.SCALE_TRIAD) {
                continue;
            }
            for (int inversion = -3; inversion <= 3; inversion++) {
                for (int octave = -2; octave <= 2; octave++) {
                    int[] keys = StackLogic.voicing(60, chord.intervals, inversion, octave);
                    StackLogic.Plan plan = StackLogic.plan(60, keys);
                    List<Integer> all = new java.util.ArrayList<>(plan.copies());
                    all.add(plan.rootKey());
                    all.sort(null);
                    int[] sorted = keys.clone();
                    Arrays.sort(sorted);
                    check(Arrays.equals(all.stream().mapToInt(Integer::intValue).toArray(), sorted),
                            "voicing kept: " + chord.label + " " + inversion + " " + octave);
                }
            }
        }
        // Out of range is refused by the caller.
        check(!StackLogic.inRange(StackLogic.voicing(120, major, 0, 1)), "range");
        // Scale Triad: D in C major is D, F, A; a note outside the scale has none.
        check(Arrays.equals(StackLogic.scaleTriad(62, 0, major()), new int[] {0, 3, 7}), "D minor in C major");
        check(Arrays.equals(StackLogic.scaleTriad(60, 0, major()), new int[] {0, 4, 7}), "C major");
        check(Arrays.equals(StackLogic.scaleTriad(71, 0, major()), new int[] {0, 3, 6}), "B diminished");
        check(StackLogic.scaleTriad(61, 0, major()).length == 0, "outside");
        // With the key root elsewhere: E in D major's scale is E, G, B.
        check(Arrays.equals(StackLogic.scaleTriad(64, 2, major()), new int[] {0, 3, 7}), "another key");
        // Degrees: 1-3-5 on D in C major is D, F, A; 1-3-5-7 on C is the major seventh.
        check(Arrays.equals(StackLogic.degreeIntervals(62, 0, major(), StackLogic.Degrees.D135.steps), new int[] {0, 3, 7}), "degrees on D");
        check(Arrays.equals(StackLogic.degreeIntervals(60, 0, major(), StackLogic.Degrees.D1357.steps), new int[] {0, 4, 7, 11}), "seventh");
        // A root outside the scale stays the first degree and borrows the scale's shape.
        check(Arrays.equals(StackLogic.degreeIntervals(63, 0, major(), StackLogic.Degrees.D135.steps), new int[] {0, 4, 7}), "chromatic root");
        System.out.println("stack-chords logic: PASS");
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }
}
