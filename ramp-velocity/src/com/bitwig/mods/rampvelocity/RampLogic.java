package com.bitwig.mods.rampvelocity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ramp, with no Bitwig in it. Within each clip, the earliest selected start gets From and the latest gets To; a
 * note in between gets a velocity by where its start lies, bent by Curve. Notes starting together get the same
 * velocity. Velocities are whole steps of 1 to 127, as Bitwig's 127 is 1.
 */
final class RampLogic {
    private RampLogic() {
    }

    /** A selected note: its position in the list, its clip (any value that is equal for the same clip) and start. */
    record In(int index, Object clip, double start) {
    }

    /** The velocity for the note at {@code index}, 1 to 127. */
    record Out(int index, int velocity) {
    }

    /**
     * @param from  the earliest start's velocity, 1 to 127
     * @param to    the latest start's velocity, 1 to 127
     * @param curve -100 to 100: 0 is a straight line; above 0 the ramp changes slowly at first and quickly at the end,
     *              below 0 quickly at first and slowly at the end
     */
    static List<Out> plan(List<In> notes, int from, int to, int curve) {
        from = clamp(from, 1, 127);
        to = clamp(to, 1, 127);
        curve = clamp(curve, -100, 100);
        Map<Object, double[]> ranges = new HashMap<>();
        for (In note : notes) {
            double[] range = ranges.computeIfAbsent(key(note), ignored -> new double[] {note.start(), note.start()});
            range[0] = Math.min(range[0], note.start());
            range[1] = Math.max(range[1], note.start());
        }
        double exponent = curve >= 0 ? 1 + 3 * curve / 100.0 : 1 / (1 + 3 * -curve / 100.0);
        List<Out> out = new ArrayList<>(notes.size());
        for (In note : notes) {
            double[] range = ranges.get(key(note));
            double span = range[1] - range[0];
            double t = span <= 1e-9 ? 0 : (note.start() - range[0]) / span;
            t = Math.pow(Math.max(0, Math.min(1, t)), exponent);
            out.add(new Out(note.index(), clamp((int) Math.round(from + (to - from) * t), 1, 127)));
        }
        return out;
    }

    private static Object key(In note) {
        return note.clip() == null ? RampLogic.class : note.clip();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
