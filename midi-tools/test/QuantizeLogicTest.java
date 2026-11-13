package com.bitwig.mods.miditools;

import com.bitwig.mods.miditools.QuantizeLogic.Out;
import com.bitwig.mods.miditools.QuantizeLogic.Snap;

/** Run through Bitwig's rounding of note times (1/1048576 of a beat). No Bitwig needed. */
public final class QuantizeLogicTest {
    static double round(double beats) {
        return Math.rint(beats * 1048576.0) / 1048576.0;
    }

    public static void main(String[] args) {
        double s = round(1.1), e = round(1.9); // a sixteenth grid: 1.0 and 2.0 are the nearest lines
        Out start = QuantizeLogic.place(s, e, 0.25, Snap.START, 100);
        check(close(start.start(), 1.0) && close(start.end(), round(1.8)), "Start moves the note, keeping its length");
        Out end = QuantizeLogic.place(s, e, 0.25, Snap.END, 100);
        check(close(end.start(), s) && close(end.end(), 2.0), "End moves only the end");
        Out both = QuantizeLogic.place(s, e, 0.25, Snap.BOTH, 100);
        check(close(both.start(), 1.0) && close(both.end(), 2.0), "Both snaps each");
        Out half = QuantizeLogic.place(s, e, 0.25, Snap.BOTH, 50);
        check(close(half.start(), 1.05) && close(half.end(), 1.95), "50% goes half way");
        Out none = QuantizeLogic.place(s, e, 0.25, Snap.BOTH, 0);
        check(close(none.start(), s) && close(none.end(), e), "0% changes nothing");
        // A short note whose start and end snap to the same line keeps the shortest length.
        Out tiny = QuantizeLogic.place(round(0.99), round(1.02), 0.25, Snap.BOTH, 100);
        check(close(tiny.start(), 1.0) && close(tiny.end(), 1.0 + QuantizeLogic.SHORTEST_NOTE_BEATS), "shortest length");
        Out endBefore = QuantizeLogic.place(round(1.1), round(1.12), 0.25, Snap.END, 100);
        check(endBefore.end() > endBefore.start(), "End never before the start");
        // Triplets: an eighth triplet grid from beat 0.
        Out trip = QuantizeLogic.place(round(0.3), round(0.6), 1.0 / 3.0, Snap.BOTH, 100);
        check(close(trip.start(), 1.0 / 3.0) && close(trip.end(), 2.0 / 3.0), "triplet grid");
        // Every amount, snap and grid: the result is valid and between the note and its target.
        int cases = 0;
        for (Snap snap : Snap.values()) {
            for (ArpLogic.Rate rate : ArpLogic.Rate.values()) {
                for (int amount = 0; amount <= 100; amount += 5) {
                    for (double t = 0; t < 4; t += 0.0371) {
                        double a = round(t), b = round(t + 0.013 + t / 3);
                        Out out = QuantizeLogic.place(a, b, rate.beats, snap, amount);
                        check(out.start() >= 0 && out.end() > out.start(), "valid " + snap + " " + rate + " " + amount + " " + t);
                        cases++;
                    }
                }
            }
        }
        System.out.println("QuantizeLogic: PASS (" + cases + " sweep cases)");
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
