package com.bitwig.mods.miditools;

import com.bitwig.mods.miditools.TimeWarpLogic.In;
import com.bitwig.mods.miditools.TimeWarpLogic.Mode;
import com.bitwig.mods.miditools.TimeWarpLogic.Out;
import java.util.List;

/** Run through Bitwig's rounding of note times (1/1048576 of a beat). No Bitwig needed. */
public final class TimeWarpLogicTest {
    static double round(double beats) {
        return Math.rint(beats * 1048576.0) / 1048576.0;
    }

    static Out at(List<Out> out, int i) {
        return out.stream().filter(o -> o.index() == i).findFirst().orElse(null);
    }

    public static void main(String[] args) {
        // Four quarter notes, beats 0 to 4: the range is 0 to 4.
        List<In> run = List.of(new In(0, 0, 0, 1), new In(1, 0, 1, 2), new In(2, 0, 2, 3), new In(3, 0, 3, 4));
        List<Out> fast = TimeWarpLogic.plan(run, Mode.SPEED_UP, 100, true); // exponent 1/4
        check(at(fast, 0) == null, "the first note stays");
        check(close(at(fast, 1).start(), 4 * Math.pow(0.25, 0.25)), "Speed Up pushes the second note later");
        check(at(fast, 1).start() - 0 > at(fast, 2).start() - at(fast, 1).start(), "gaps shrink: faster and faster");
        check(close(at(fast, 1).end() - at(fast, 1).start(), 1), "Keep keeps lengths");
        List<Out> slow = TimeWarpLogic.plan(run, Mode.SLOW_DOWN, 100, true); // exponent 4
        check(close(at(slow, 1).start(), 4 * Math.pow(0.25, 4)), "Slow Down pulls the second note earlier");
        check(at(slow, 3).start() - at(slow, 2).start() > at(slow, 2).start() - at(slow, 1).start(), "gaps grow: slower");
        List<Out> warped = TimeWarpLogic.plan(run, Mode.SLOW_DOWN, 100, false);
        check(at(warped, 3) != null && close(at(warped, 3).end(), 4), "Warp keeps the range's end");
        check(close(at(warped, 0).end(), 4 * Math.pow(0.25, 4)), "Warp bends the ends too");
        check(TimeWarpLogic.plan(run, Mode.SPEED_UP, 0, true).isEmpty(), "0% changes nothing");
        check(TimeWarpLogic.plan(run, Mode.SPEED_UP, 0, false).isEmpty(), "0% changes nothing with Warp");
        // A single note, or notes starting together, have nothing to warp with Keep.
        check(TimeWarpLogic.plan(List.of(new In(0, 0, 1, 2)), Mode.SPEED_UP, 100, true).isEmpty(), "one note");
        // Two clips warp apart: each over its own range.
        List<In> two = List.of(new In(0, 0, 0, 1), new In(1, 0, 1, 2), new In(2, 1, 10, 11), new In(3, 1, 11, 12));
        List<Out> apart = TimeWarpLogic.plan(two, Mode.SPEED_UP, 100, true);
        check(close(at(apart, 1).start(), 2 * Math.pow(0.5, 0.25)), "first clip over 0 to 2");
        check(close(at(apart, 3).start(), 10 + 2 * Math.pow(0.5, 0.25)), "second clip over 10 to 12");
        // Every setting: notes stay valid, starts keep their order and stay inside the range.
        int cases = 0;
        for (Mode mode : Mode.values()) {
            for (int amount = 0; amount <= 100; amount += 10) {
                for (boolean keep : new boolean[] {true, false}) {
                    List<In> notes = List.of(new In(0, 0, round(0.5), round(0.8)), new In(1, 0, round(0.75), round(2.1)),
                            new In(2, 0, round(1.3), round(1.31)), new In(3, 0, round(2.9), round(3.7)));
                    List<Out> out = TimeWarpLogic.plan(notes, mode, amount, keep);
                    double last = -1;
                    for (In note : notes) {
                        Out o = at(out, note.index());
                        double start = o == null ? note.start() : o.start();
                        double end = o == null ? note.end() : o.end();
                        check(end > start && start >= round(0.5) - 1e-9 && start <= round(3.7), "valid " + mode + amount + keep);
                        check(start >= last, "order kept");
                        last = start;
                        cases++;
                    }
                }
            }
        }
        System.out.println("TimeWarpLogic: PASS (" + cases + " sweep cases)");
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
