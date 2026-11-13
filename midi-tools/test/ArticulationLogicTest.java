package com.bitwig.mods.miditools;

import java.util.List;

/** Note Articulation's lengths. No Bitwig needed. */
public final class ArticulationLogicTest {
    static ArticulationLogic.In note(int i, int key, double start, double end) {
        return new ArticulationLogic.In(i, 0, 0, key, start, end);
    }

    static double end(List<ArticulationLogic.Out> out, int index, double unchanged) {
        return out.stream().filter(o -> o.index() == index).mapToDouble(ArticulationLogic.Out::end).findFirst().orElse(unchanged);
    }

    public static void main(String[] args) {
        // Three notes a beat apart, a quarter beat long, and a chord note with the first.
        List<ArticulationLogic.In> notes = List.of(note(0, 60, 0, 0.25), note(1, 64, 0, 0.25), note(2, 62, 1, 1.25), note(3, 65, 2, 2.25));
        List<ArticulationLogic.Out> legato = ArticulationLogic.plan(notes, ArticulationLogic.Style.LEGATO, 0.25, 0, 0);
        check(end(legato, 0, 0.25) == 1 && end(legato, 1, 0.25) == 1 && end(legato, 2, 1.25) == 2, "legato to the next start");
        check(end(legato, 3, 2.25) == 2.25, "the last keeps its end");
        check(ArticulationLogic.plan(notes, ArticulationLogic.Style.TENUTO, 0.25, 0, 0).isEmpty(), "tenuto with no offset changes nothing");
        List<ArticulationLogic.In> long_ = List.of(note(0, 60, 0, 1), note(1, 62, 1, 2), note(2, 64, 2, 3));
        List<ArticulationLogic.Out> staccato = ArticulationLogic.plan(long_, ArticulationLogic.Style.STACCATO, 0.25, 0, 0);
        check(end(staccato, 0, 1) == 0.5 && end(staccato, 2, 3) == 2.5, "staccato halves");
        // A single note: half its own length (the original left it as it was).
        check(end(ArticulationLogic.plan(List.of(note(0, 60, 0, 2)), ArticulationLogic.Style.STACCATO, 0.25, 0, 0), 0, 2) == 1, "one note");
        // Staccato never lengthens: short notes far apart are halved, not stretched to half the spacing.
        check(end(staccato(notes), 2, 1.25) == 1.125, "short notes halved");
        // Offset: +100% of a 1/4 grid on tenuto adds a beat; -100% can't go below a hundredth of a beat.
        check(end(ArticulationLogic.plan(notes, ArticulationLogic.Style.TENUTO, 1, 100, 0), 0, 0) == 1.25, "offset adds");
        check(end(ArticulationLogic.plan(notes, ArticulationLogic.Style.TENUTO, 1, -100, 0), 0, 0) == ArticulationLogic.SHORTEST_BEATS, "shortest");
        // Variation: above 0 only shortens, below 0 only lengthens, by up to the grid step, the same each time.
        List<ArticulationLogic.Out> shorter = ArticulationLogic.plan(long_, ArticulationLogic.Style.TENUTO, 0.25, 0, 100);
        List<ArticulationLogic.Out> longer = ArticulationLogic.plan(long_, ArticulationLogic.Style.TENUTO, 0.25, 0, -100);
        for (ArticulationLogic.In n : long_) {
            double s = end(shorter, n.index(), n.end());
            double l = end(longer, n.index(), n.end());
            check(s <= n.end() && s >= n.end() - 0.25 && l >= n.end() && l <= n.end() + 0.25, "variation within a grid step");
        }
        check(shorter.equals(ArticulationLogic.plan(long_, ArticulationLogic.Style.TENUTO, 0.25, 0, 100)), "the same each time");
        // Clips are measured apart.
        List<ArticulationLogic.In> two = List.of(new ArticulationLogic.In(0, 0, 0, 60, 0, 0.5), new ArticulationLogic.In(1, 1, 0, 60, 1, 1.5));
        check(ArticulationLogic.plan(two, ArticulationLogic.Style.LEGATO, 0.25, 0, 0).isEmpty(), "no next start across clips");
        System.out.println("articulation logic: PASS");
    }

    static List<ArticulationLogic.Out> staccato(List<ArticulationLogic.In> notes) {
        return ArticulationLogic.plan(notes, ArticulationLogic.Style.STACCATO, 0.25, 0, 0);
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }
}
