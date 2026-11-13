package com.bitwig.mods.miditools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/** Stack's musical logic, in plain values: a root key and a chord in, the chord's keys out. */
final class StackLogic {
    /** The chord types, in the dropdown's order. An empty list of intervals follows the key signature. */
    enum Chord {
        MAJOR("Major", 0, 4, 7), MINOR("Minor", 0, 3, 7), SCALE_TRIAD("Scale Triad"),
        DIMINISHED("Diminished", 0, 3, 6), AUGMENTED("Augmented", 0, 4, 8),
        SUS2("Sus2", 0, 2, 7), SUS4("Sus4", 0, 5, 7),
        MAJOR_7("Major 7", 0, 4, 7, 11), MINOR_7("Minor 7", 0, 3, 7, 10), DOMINANT_7("Dominant 7", 0, 4, 7, 10),
        ADD9("Add9", 0, 4, 7, 14), ADD11("Add11", 0, 4, 5, 7), NINTH("9th", 0, 4, 7, 10, 14),
        ELEVENTH("11th", 0, 4, 7, 10, 14, 17);

        final String label;
        final int[] intervals;

        Chord(String label, int... intervals) {
            this.label = label;
            this.intervals = intervals;
        }
    }

    /** Scale degree patterns, in the dropdown's order, as steps of the scale from the root. */
    enum Degrees {
        D135("1–3–5", 0, 2, 4), D136("1–3–6", 0, 2, 5), D1357("1–3–5–7", 0, 2, 4, 6),
        D1359("1–3–5–9", 0, 2, 4, 8), D145("1–4–5", 0, 3, 4), D146("1–4–6", 0, 3, 5),
        D1457("1–4–5–7", 0, 3, 4, 6), D1369("1–3–6–9", 0, 2, 5, 8),
        D125("1–2–5", 0, 1, 4), D156("1–5–6", 0, 4, 5), D1579("1–5–7–9", 0, 4, 6, 8),
        D1469("1–4–6–9", 0, 3, 5, 8), D157("1–5–7", 0, 4, 6), D126("1–2–6", 0, 1, 5),
        D12710("1–2–7–10", 0, 1, 6, 9), D1479("1–4–7–9", 0, 3, 6, 8);

        final String label;
        final int[] steps;

        Degrees(String label, int... steps) {
            this.label = label;
            this.steps = steps;
        }
    }

    /** What the root note does and which keys are new. */
    record Plan(int rootKey, List<Integer> copies) {
    }

    private StackLogic() {
    }

    /** The chord's keys: above 0 lifts its lowest voices an octave, below 0 drops its highest, then all move by octave. */
    static int[] voicing(int root, int[] intervals, int inversion, int octave) {
        int[] voiced = intervals.clone();
        int turns = Math.min(Math.abs(inversion), voiced.length);
        for (int i = 0; i < turns; i++) {
            if (inversion > 0) {
                voiced[i] += 12;
            } else {
                voiced[voiced.length - 1 - i] -= 12;
            }
        }
        Arrays.sort(voiced);
        for (int i = 0; i < voiced.length; i++) {
            voiced[i] += root + octave * 12;
        }
        return voiced;
    }

    static boolean inRange(int[] keys) {
        return Arrays.stream(keys).allMatch(key -> key >= 0 && key <= 127);
    }

    /**
     * The root note stays at its key when the chord has it. Otherwise it takes the chord's key of the same note in
     * another octave, and failing that the chord's lowest key. The other keys are copies.
     */
    static Plan plan(int rootKey, int[] keys) {
        int chosen = -1;
        for (int i = 0; i < keys.length && chosen < 0; i++) {
            chosen = keys[i] == rootKey ? i : -1;
        }
        for (int i = 0; i < keys.length && chosen < 0; i++) {
            chosen = Math.floorMod(keys[i] - rootKey, 12) == 0 ? i : -1;
        }
        chosen = Math.max(chosen, 0);
        List<Integer> copies = new ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            if (i != chosen) {
                copies.add(keys[i]);
            }
        }
        return new Plan(keys[chosen], copies);
    }

    /** A triad of thirds in the scale from this key, as intervals; empty when the key isn't in the scale. */
    static int[] scaleTriad(int key, int keyRoot, BitSet scale) {
        int degree = Math.floorMod(key - keyRoot, 12);
        if (!scale.get(degree)) {
            return new int[0];
        }
        int[] tones = new int[5];
        int count = 0;
        for (int semitones = 0; semitones <= 24 && count < tones.length; semitones++) {
            if (scale.get(Math.floorMod(degree + semitones, 12))) {
                tones[count++] = semitones;
            }
        }
        return count < 5 ? new int[0] : new int[] {tones[0], tones[2], tones[4]};
    }

    /**
     * Scale steps from this key as intervals, so a D root in C major makes D, F, A. A root outside the scale keeps
     * its place as the first degree and borrows the shape of the scale from its root.
     */
    static int[] degreeIntervals(int key, int keyRoot, BitSet scale, int[] steps) {
        int root = Math.floorMod(key - keyRoot, 12);
        int highest = Arrays.stream(steps).max().orElse(0);
        int from = scale.get(root) ? root : 0;
        int[] found = new int[highest + 1];
        int count = 0;
        for (int semitones = 0; semitones <= 48 && count <= highest; semitones++) {
            if (scale.get(Math.floorMod(from + semitones, 12))) {
                found[count++] = semitones;
            }
        }
        if (count <= highest) {
            return new int[0];
        }
        return Arrays.stream(steps).map(step -> found[step]).toArray();
    }
}
