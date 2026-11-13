package com.bitwig.mods.miditools;

import java.util.HashSet;
import java.util.List;

/** Run through Bitwig's rounding of note times (1/1048576 of a beat). No Bitwig needed. */
public final class ChopLogicTest {
    static double round(double beats) {
        return Math.rint(beats * 1048576.0) / 1048576.0;
    }

    static ChopLogic.In note(int i, int key, double start, double end, double velocity) {
        return new ChopLogic.In(i, key, 0, round(start), round(end), velocity);
    }

    public static void main(String[] args) {
        // The catalog, as the original's own fixture test states it.
        List<RhythmPattern> catalog = RhythmPattern.catalog();
        check(catalog.size() == 64, "64 rhythms");
        check(new HashSet<>(catalog.stream().map(RhythmPattern::mask).toList()).size() == 64, "all different");
        check(find("House", "Four-to-the-Floor").mask() == 0x1111, "four-to-the-floor mask");
        check(find("House", "Standard Offbeat").mask() == 0x4444, "offbeat mask");
        check(find("Syncopated", "3-3-2").mask() == 0x4949, "3-3-2 mask");

        // Parts: 4 parts of a 2-beat note.
        ChopLogic.Plan four = ChopLogic.parts(List.of(note(0, 60, 0, 2, 100)), 4, 0, 0);
        check(four.slices().size() == 4, "4 slices");
        check(four.slices().get(0).sameNote() && !four.slices().get(1).sameNote(), "first is the note");
        check(close(four.slices().get(1).start(), 0.5) && close(four.slices().get(3).end(), 2), "times");
        // Gaps: +1 plays one, rests one; +2 plays two, rests one; -1 plays one, rests one; -3 plays one, rests three.
        check(ChopLogic.parts(List.of(note(0, 60, 0, 2, 100)), 8, 1, 0).slices().size() == 4, "gaps 1");
        check(ChopLogic.parts(List.of(note(0, 60, 0, 2, 100)), 8, 2, 0).slices().size() == 6, "gaps 2");
        check(ChopLogic.parts(List.of(note(0, 60, 0, 2, 100)), 8, -1, 0).slices().size() == 4, "gaps -1");
        check(ChopLogic.parts(List.of(note(0, 60, 0, 2, 100)), 8, -3, 0).slices().size() == 2, "gaps -3");
        // Variation stays inside the note, always the same, and never inverts a slice, on a triplet-long note.
        double third = 1.0 / 3.0;
        List<ChopLogic.In> triplet = List.of(note(0, 60, third, third * 4, 100));
        ChopLogic.Plan varied = ChopLogic.parts(triplet, 16, 0, 100);
        ChopLogic.Plan again = ChopLogic.parts(triplet, 16, 0, 100);
        check(varied.equals(again), "variation repeats");
        for (ChopLogic.Out slice : varied.slices()) {
            check(slice.end() > slice.start() && slice.start() >= triplet.get(0).start() && slice.end() <= triplet.get(0).end(), "inside");
        }

        // Rhythm: four to the floor on a bar-long chord: 4 hits per note, chord notes together.
        RhythmPattern floor = find("House", "Four-to-the-Floor");
        List<ChopLogic.In> chord = List.of(note(0, 48, 8, 12, 100), note(1, 52, 8, 12, 100), note(2, 55, 8, 12, 100));
        ChopLogic.Plan bar = ChopLogic.rhythm(chord, floor, 50, 0, 50, false);
        check(bar.slices().size() == 12, "three voices of four hits");
        check(close(bar.slices().get(1).start(), 9), "second hit on the beat");
        double expected = ChopLogic.gateLength(1, 50);
        check(close(bar.slices().get(0).end() - bar.slices().get(0).start(), expected), "gate");
        check(bar.slices().get(0).velocity127() == 110, "accent 50%: (100 + 120) / 2");
        check(ChopLogic.rhythm(chord, floor, 50, 0, 0, false).slices().get(0).velocity127() < 0, "accent 0 leaves velocity");
        check(close(ChopLogic.rhythm(chord, floor, 50, 0, 50, true).slices().get(3).end(), 12), "tail holds");
        // A note in a rest with this rhythm has no hit: it is silenced.
        ChopLogic.Plan rest = ChopLogic.rhythm(List.of(note(0, 60, 0, 4, 100), note(1, 64, 0.25, 0.75, 100)), floor, 50, 0, 50, false);
        check(rest.silenced().equals(List.of(1)), "a note with no hit");
        // Too short.
        boolean refused = false;
        try {
            ChopLogic.rhythm(List.of(note(0, 60, 0, 0.1, 100)), floor, 50, 0, 50, false);
        } catch (ChopLogic.TooShort e) {
            refused = true;
        }
        check(refused, "shorter than a step");
        // A rounded sixteenth-long note is long enough.
        ChopLogic.rhythm(List.of(note(0, 60, third, third + 0.25, 100)), floor, 50, 0, 50, false);
        // Swing delays odd steps by under half a step.
        check(ChopLogic.swingDelay(1, 100) < ChopLogic.STEP_BEATS / 2 && ChopLogic.swingDelay(2, 100) == 0, "swing");
        System.out.println("chop-notes logic: PASS");
    }

    static RhythmPattern find(String family, String name) {
        return RhythmPattern.catalog().stream().filter(r -> r.family().equals(family) && r.name().equals(name)).findFirst().orElseThrow();
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
