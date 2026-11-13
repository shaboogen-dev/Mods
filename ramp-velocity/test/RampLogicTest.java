package com.bitwig.mods.rampvelocity;

import java.util.List;

/** The ramp's arithmetic. No Bitwig needed: compile with RampLogic.java and run. */
public final class RampLogicTest {
    static int at(List<RampLogic.Out> out, int i) {
        return out.stream().filter(o -> o.index() == i).findFirst().orElseThrow().velocity();
    }

    public static void main(String[] args) {
        Object clip = "a";
        List<RampLogic.In> line = List.of(new RampLogic.In(0, clip, 0), new RampLogic.In(1, clip, 1),
                new RampLogic.In(2, clip, 2), new RampLogic.In(3, clip, 3), new RampLogic.In(4, clip, 4));
        List<RampLogic.Out> up = RampLogic.plan(line, 1, 127, 0);
        check(at(up, 0) == 1 && at(up, 4) == 127, "ends are From and To");
        check(at(up, 2) == 64, "middle on the line");
        List<RampLogic.Out> down = RampLogic.plan(line, 127, 1, 0);
        check(at(down, 0) == 127 && at(down, 4) == 1, "a ramp down");
        // Curve above 0 starts slowly: the middle is below the line; below 0 above it.
        check(at(RampLogic.plan(line, 1, 127, 100), 2) < 64, "curve up starts slowly");
        check(at(RampLogic.plan(line, 1, 127, -100), 2) > 64, "curve down starts quickly");
        check(at(RampLogic.plan(line, 1, 127, 100), 4) == 127, "curve keeps the ends");
        // A chord: notes starting together share a velocity.
        List<RampLogic.In> chords = List.of(new RampLogic.In(0, clip, 0), new RampLogic.In(1, clip, 0),
                new RampLogic.In(2, clip, 2), new RampLogic.In(3, clip, 2));
        List<RampLogic.Out> c = RampLogic.plan(chords, 20, 100, 0);
        check(at(c, 0) == 20 && at(c, 1) == 20 && at(c, 2) == 100 && at(c, 3) == 100, "chords together");
        // Each clip ramps over its own notes.
        List<RampLogic.In> two = List.of(new RampLogic.In(0, "a", 0), new RampLogic.In(1, "a", 4),
                new RampLogic.In(2, "b", 10), new RampLogic.In(3, "b", 12));
        List<RampLogic.Out> t = RampLogic.plan(two, 10, 110, 0);
        check(at(t, 0) == 10 && at(t, 1) == 110 && at(t, 2) == 10 && at(t, 3) == 110, "per clip");
        // All starting together: From.
        List<RampLogic.Out> same = RampLogic.plan(List.of(new RampLogic.In(0, clip, 1), new RampLogic.In(1, clip, 1)), 30, 90, 0);
        check(at(same, 0) == 30 && at(same, 1) == 30, "one start gets From");
        // Out of range values are held to 1-127.
        check(at(RampLogic.plan(line, 0, 200, 0), 0) == 1 && at(RampLogic.plan(line, 0, 200, 0), 4) == 127, "clamped");
        System.out.println("RAMP_LOGIC_OK");
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }
}
