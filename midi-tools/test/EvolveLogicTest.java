package com.bitwig.mods.miditools;

import java.util.ArrayList;
import java.util.List;

/** Chop Rhythm's Evolve. No Bitwig needed. */
public final class EvolveLogicTest {
    public static void main(String[] args) {
        RhythmPattern four = RhythmPattern.catalog().stream().filter(r -> r.name().equals("Four-to-the-Floor")).findFirst().orElseThrow();
        int[][] chords = {{48, 60, 64, 67}, {53, 60, 65, 69}};
        List<ChopLogic.In> in = new ArrayList<>();
        for (int c = 0; c < chords.length; c++) {
            for (int key : chords[c]) {
                in.add(new ChopLogic.In(in.size(), key, 0, c * 4, c * 4 + 4, 90));
            }
        }
        // Variation 0, Locked, Roll Off, Strict: exactly Chop Rhythm's hits (one a beat).
        ChopLogic.Plan plain = EvolveLogic.plan(in, four, 50, 0, 0, false, new EvolveLogic.Options(2, 0, 0, 0, 0));
        ChopLogic.Plan rhythm = ChopLogic.rhythm(in, four, 50, 0, 0, false);
        check(plain.slices().size() == rhythm.slices().size(), "plain evolve = chop rhythm: " + plain.slices().size() + " vs " + rhythm.slices().size());
        // The bass keeps the base rhythm whatever the options.
        ChopLogic.Plan busy = EvolveLogic.plan(in, four, 50, 0, 100, false, new EvolveLogic.Options(0, 100, 3, 1, 1));
        long bassHits = busy.slices().stream().filter(s -> s.source() == 0).count();
        check(bassHits == 4, "the bass keeps four hits: " + bassHits);
        check(busy.slices().size() > plain.slices().size(), "variation adds hits above the bass");
        // Every note keeps a first slice (itself); keys never change (slices are of their source).
        for (ChopLogic.In note : in) {
            check(busy.slices().stream().filter(s -> s.source() == note.index() && s.sameNote()).count() == 1, "one first slice per note");
        }
        // No two slices of one key and channel overlap.
        for (ChopLogic.Out a : busy.slices()) {
            for (ChopLogic.Out b : busy.slices()) {
                if (a != b && in.get(a.source()).key() == in.get(b.source()).key()) {
                    check(a.end() <= b.start() + 1e-9 || b.end() <= a.start() + 1e-9, "no overlap on one key");
                }
            }
        }
        // The same each time.
        check(busy.equals(EvolveLogic.plan(in, four, 50, 0, 100, false, new EvolveLogic.Options(0, 100, 3, 1, 1))), "deterministic");
        // Harmony Late 1/8 moves the upper voices' chord change half a beat later; the bass stays.
        ChopLogic.Plan late = EvolveLogic.plan(in, four, 50, 0, 0, true, new EvolveLogic.Options(2, 0, 0, 0, 4));
        // (Hold Last: the first chord's upper notes run to 4.5; the second chord's start no earlier.)
        double firstUpperEnd = late.slices().stream().filter(s -> s.source() == 1).mapToDouble(ChopLogic.Out::end).max().orElseThrow();
        double firstBassEnd = late.slices().stream().filter(s -> s.source() == 0).mapToDouble(ChopLogic.Out::end).max().orElseThrow();
        double secondUpper = late.slices().stream().filter(s -> s.source() == 5).mapToDouble(ChopLogic.Out::start).min().orElseThrow();
        check(firstUpperEnd == 4.5 && firstBassEnd == 4 && secondUpper >= 4.5, "late: " + firstUpperEnd + " " + firstBassEnd + " " + secondUpper);
        // Short notes are refused.
        try {
            EvolveLogic.plan(List.of(new ChopLogic.In(0, 60, 0, 0, 0.1, 90)), four, 50, 0, 0, false, new EvolveLogic.Options(2, 50, 2, 1, 0));
            throw new AssertionError("short notes should be refused");
        } catch (EvolveLogic.Refusal expected) {
            // as Chop Rhythm
        }
        System.out.println("evolve logic: PASS");
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }
}
