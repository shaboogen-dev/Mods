package com.bitwig.mods.miditools;

import java.util.List;

/** Run through Bitwig's rounding of note times (1/1048576 of a beat). No Bitwig needed. */
public final class StrumLogicTest {
    static double round(double beats) {
        return Math.rint(beats * 1048576.0) / 1048576.0;
    }

    static StrumLogic.In note(int i, int key, double start, double end) {
        return new StrumLogic.In(i, 1, 0, key, round(start), round(end));
    }

    static StrumLogic.Out at(List<StrumLogic.Out> out, int i) {
        return out.stream().filter(o -> o.index() == i).findFirst().orElseThrow();
    }

    public static void main(String[] args) {
        List<StrumLogic.In> triad = List.of(note(0, 60, 1, 2), note(1, 64, 1, 2), note(2, 67, 1, 2));
        // High +100%: the top note half a beat late, the bottom in place, the middle in proportion to its key.
        List<StrumLogic.Out> up = StrumLogic.plan(triad, 0, 100, 0);
        check(close(at(up, 0).start(), 1), "low stays");
        check(close(at(up, 2).start(), 1.5), "high moves half a beat");
        check(close(at(up, 1).start(), 1 + 0.5 * 4 / 7), "middle in proportion");
        check(close(at(up, 2).end(), 2.5), "length kept");
        // Low -100% at the clip's start moves the chord later as a whole, keeping its shape.
        List<StrumLogic.In> first = List.of(note(0, 60, 0, 1), note(1, 67, 0, 1));
        List<StrumLogic.Out> early = StrumLogic.plan(first, -100, 0, 0);
        check(close(at(early, 0).start(), 0) && close(at(early, 1).start(), 0.5), "whole chord shifted");
        // Tension bends the middle: up late, down early, with the ends fixed.
        check(StrumLogic.bend(0.5, 100) < 0.5 && StrumLogic.bend(0.5, -100) > 0.5, "tension");
        check(StrumLogic.bend(0, 100) == 0 && StrumLogic.bend(1, -100) == 1, "ends");
        // One key, or one note, is no chord.
        check(StrumLogic.plan(List.of(note(0, 60, 0, 1), note(1, 60, 0, 1)), 0, 100, 0).isEmpty(), "same key");
        check(StrumLogic.plan(List.of(note(0, 60, 0, 1)), 0, 100, 0).isEmpty(), "single note");
        // Triplet chords on rounded times stay chords; the next one, a third of a beat on, is its own.
        double third = 1.0 / 3.0;
        List<StrumLogic.In> two = List.of(note(0, 60, third, 1), note(1, 64, third, 1), note(2, 60, third * 2, 1), note(3, 64, third * 2, 1));
        List<StrumLogic.Out> chords = StrumLogic.plan(two, 0, 50, 0);
        check(close(at(chords, 0).start(), third) && close(at(chords, 2).start(), third * 2), "two chords");
        check(close(at(chords, 1).start(), third + 0.25) && close(at(chords, 3).start(), third * 2 + 0.25), "each strummed");
        // Different clips or channels never share a chord.
        check(StrumLogic.plan(List.of(new StrumLogic.In(0, 1, 0, 60, 0, 1), new StrumLogic.In(1, 2, 0, 64, 0, 1)), 0, 100, 0).isEmpty(), "clips");
        check(StrumLogic.plan(List.of(new StrumLogic.In(0, 1, 0, 60, 0, 1), new StrumLogic.In(1, 1, 1, 64, 0, 1)), 0, 100, 0).isEmpty(), "channels");
        System.out.println("strum-chords logic: PASS");
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
