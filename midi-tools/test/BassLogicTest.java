package com.bitwig.mods.miditools;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/** The original's Smart Bass contracts, on BassLogic, and the two outputs. No Bitwig needed. */
public final class BassLogicTest {
    static List<BassLogic.In> chords(double[][] times, int[][] keys) {
        List<BassLogic.In> notes = new ArrayList<>();
        for (int c = 0; c < keys.length; c++) {
            for (int key : keys[c]) {
                notes.add(new BassLogic.In(notes.size(), 0, 0, key, times[c][0], times[c][1]));
            }
        }
        return notes;
    }

    static int[] roots(List<BassLogic.Chord> chords) {
        return chords.stream().mapToInt(c -> c.bassKey() % 12).toArray();
    }

    public static void main(String[] args) {
        BitSet minor = new BitSet(12);
        for (int step : new int[] {0, 2, 3, 5, 7, 8, 10}) {
            minor.set(step);
        }
        BassLogic.Scale fsMinor = new BassLogic.Scale(6, minor);
        List<BassLogic.In> add9 = chords(new double[][] {{0, 4}}, new int[][] {{54, 56, 57}});
        List<BassLogic.Chord> one = BassLogic.plan(add9, fsMinor, 0);
        check(one.size() == 1 && one.get(0).bassKey() % 12 == 6, "F#-G#-A is F# minor add9");
        check(one.get(0).keeper() == 0 && one.get(0).bassKey() == 54 && one.get(0).others().size() == 2, "the F# is the bass");

        List<BassLogic.In> cadence = chords(new double[][] {{0, 4}, {4, 8}}, new int[][] {{49, 53, 56, 59}, {45, 49, 52, 54}});
        check(java.util.Arrays.equals(roots(BassLogic.plan(cadence, fsMinor, 0)), new int[] {1, 6}), "V7-i reads F#m7/A, not A6");

        List<BassLogic.In> progression = chords(new double[][] {{0, 4}, {4, 8}, {8, 12}, {12, 16}},
                new int[][] {{54, 57, 61}, {50, 54, 57}, {52, 56, 59}, {49, 53, 56, 59}});
        List<BassLogic.Chord> four = BassLogic.plan(progression, fsMinor, 0);
        check(java.util.Arrays.equals(roots(four), new int[] {6, 2, 4, 1}), "i-VI-VII-V");
        // An octave lower, the bass sits under each chord.
        List<BassLogic.Chord> lower = BassLogic.plan(progression, fsMinor, -1);
        for (int c = 0; c < lower.size(); c++) {
            double start = c * 4;
            int lowest = progression.stream().filter(n -> n.start() == start).mapToInt(BassLogic.In::key).min().getAsInt();
            check(lower.get(c).bassKey() < lowest, "chord " + c + ": bass " + lower.get(c).bassKey() + " under " + lowest);
        }
        // Outputs: Add Below copies the carrying note at the bass key; Replace moves it and removes the rest.
        List<LandLogic.N> plain = new ArrayList<>();
        for (BassLogic.In n : progression) {
            plain.add(new LandLogic.N(n.index(), 0, 0, n.key(), n.start(), n.end()));
        }
        BassLogic.Output add = BassLogic.output(lower, plain, false);
        check(add.removed().isEmpty() && add.newKeys().isEmpty() && add.copies().size() == 4, "Add Below adds four bass notes");
        for (LandLogic.Copy copy : add.copies()) {
            check(copy.key() < plain.get(copy.of()).key(), "each under its chord");
        }
        BassLogic.Output replace = BassLogic.output(four, plain, true);
        check(replace.copies().isEmpty() && replace.removed().size() == progression.size() - 4, "Replace leaves one note a chord");
        // Without a key signature the chords alone decide.
        check(BassLogic.plan(progression, null, 0).size() == 4, "no key: still four chords");
        System.out.println("bass logic: PASS");
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }
}
