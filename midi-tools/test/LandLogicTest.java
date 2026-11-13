package com.bitwig.mods.miditools;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Notes landing on other keys: the cuts and the order. No Bitwig needed. */
public final class LandLogicTest {
    static LandLogic.N n(int id, int key, double start, double end) {
        return new LandLogic.N(id, 0, 0, key, start, end);
    }

    public static void main(String[] args) {
        // A note moving from 60 to 72 over beats 1-2: a note on 72 from 0 to 4 keeps 0-1 and 2-4.
        List<LandLogic.N> notes = List.of(n(0, 60, 1, 2), n(1, 72, 0, 4), n(2, 72, 1.5, 1.75), n(3, 72, 2, 3), n(4, 74, 1, 2));
        LandLogic.Plan plan = LandLogic.plan(notes, Set.of(), Map.of(0, 72), List.of());
        check(plan.moveOrder().equals(List.of(0)), "one move");
        check(plan.cuts().size() == 2, "two notes cut: " + plan.cuts().size());
        LandLogic.Cut around = plan.cuts().stream().filter(c -> c.note() == 1).findFirst().orElseThrow();
        check(around.pieces().size() == 2 && around.pieces().get(0)[1] == 1 && around.pieces().get(1)[0] == 2, "head and tail");
        LandLogic.Cut inside = plan.cuts().stream().filter(c -> c.note() == 2).findFirst().orElseThrow();
        check(inside.pieces().isEmpty(), "a note inside goes");
        // Touching (note 3 starts where it ends) and other keys (note 4) aren't cut.
        check(plan.cuts().stream().noneMatch(c -> c.note() == 3 || c.note() == 4), "touching and other keys stay");
        // A removed note isn't cut.
        check(LandLogic.plan(notes, Set.of(1, 2), Map.of(0, 72), List.of()).cuts().isEmpty(), "removed notes aren't cut");
        // Order: 64 -> 76 waits for 76 -> 88 to leave.
        List<LandLogic.N> chain = List.of(n(0, 64, 0, 1), n(1, 76, 0, 1));
        check(LandLogic.plan(chain, Set.of(), Map.of(0, 76, 1, 88), List.of()).moveOrder().equals(List.of(1, 0)), "leaver first");
        // A swap can't be done in any order; two notes landing together collide.
        refused(() -> LandLogic.plan(chain, Set.of(), Map.of(0, 76, 1, 64), List.of()), "swap");
        refused(() -> LandLogic.plan(chain, Set.of(), Map.of(0, 52), List.of(new LandLogic.Copy(1, 52, 0.5, 2))), "land together");
        // Copies cut what they land on too.
        LandLogic.Plan copy = LandLogic.plan(List.of(n(0, 60, 0, 1), n(1, 48, 0.5, 3)), Set.of(), Map.of(), List.of(new LandLogic.Copy(0, 48, 0, 1)));
        check(copy.cuts().size() == 1 && copy.cuts().get(0).pieces().get(0)[0] == 1, "the copy trims the start of the note it lands on");
        System.out.println("land logic: PASS");
    }

    static void refused(Runnable run, String what) {
        try {
            run.run();
        } catch (LandLogic.Collision expected) {
            return;
        }
        throw new AssertionError(what + " should be refused");
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }
}
