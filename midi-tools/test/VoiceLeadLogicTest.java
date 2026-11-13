package com.bitwig.mods.miditools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The original's progression contracts, on VoiceLeadLogic. No Bitwig needed. */
public final class VoiceLeadLogicTest {
    static VoiceLeadLogic.In note(int i, int key, double start, double end) {
        return new VoiceLeadLogic.In(i, 0, 0, key, start, end);
    }

    static List<VoiceLeadLogic.In> notes(int[][] spec) { // {key, start, end}
        List<VoiceLeadLogic.In> notes = new ArrayList<>();
        for (int i = 0; i < spec.length; i++) {
            notes.add(note(i, spec[i][0], spec[i][1], spec[i][2]));
        }
        return notes;
    }

    static int[] at(List<VoiceLeadLogic.In> notes, int[] keys, double start) {
        return notes.stream().filter(n -> n.start() == start).mapToInt(n -> keys[n.index()]).sorted().toArray();
    }

    public static void main(String[] args) {
        List<VoiceLeadLogic.In> cToG = notes(new int[][] {{60, 0, 4}, {64, 0, 4}, {67, 0, 4}, {67, 4, 8}, {71, 4, 8}, {74, 4, 8}});
        VoiceLeadLogic.Result smooth = VoiceLeadLogic.plan(cToG, true, VoiceLeadLogic.Bass.FREE, null, VoiceLeadLogic.Voicing.KEEP);
        check(smooth.chords() == 2, "two chords, not six roots");
        check(Arrays.equals(at(cToG, smooth.keys(), 4), new int[] {59, 62, 67}), "C to G leads to B D G");
        check(Arrays.equals(at(cToG, smooth.keys(), 0), new int[] {60, 64, 67}), "the first chord stays");
        // Again on its own result: nothing moves.
        List<VoiceLeadLogic.In> again = new ArrayList<>();
        for (VoiceLeadLogic.In n : cToG) {
            again.add(note(n.index(), smooth.keys()[n.index()], n.start(), n.end()));
        }
        VoiceLeadLogic.Result twice = VoiceLeadLogic.plan(again, true, VoiceLeadLogic.Bass.FREE, null, VoiceLeadLogic.Voicing.KEEP);
        for (VoiceLeadLogic.In n : again) {
            check(twice.keys()[n.index()] == n.key(), "a second Apply changes nothing");
        }
        VoiceLeadLogic.Result off = VoiceLeadLogic.plan(cToG, false, VoiceLeadLogic.Bass.FREE, null, VoiceLeadLogic.Voicing.KEEP);
        for (VoiceLeadLogic.In n : cToG) {
            check(off.keys()[n.index()] == n.key(), "every option off changes nothing");
        }
        VoiceLeadLogic.Result root = VoiceLeadLogic.plan(cToG, true, VoiceLeadLogic.Bass.ROOT, null, VoiceLeadLogic.Voicing.KEEP);
        check(at(cToG, root.keys(), 4)[0] % 12 == 7, "Root puts G in the bass");
        VoiceLeadLogic.Result keep = VoiceLeadLogic.plan(cToG, true, VoiceLeadLogic.Bass.KEEP, null, VoiceLeadLogic.Voicing.KEEP);
        check(at(cToG, keep.keys(), 4)[0] == 67, "Keep Lowest keeps the G chord's lowest note");
        VoiceLeadLogic.Result inverted = VoiceLeadLogic.plan(cToG, false, VoiceLeadLogic.Bass.FREE, 1, VoiceLeadLogic.Voicing.KEEP);
        check(at(cToG, inverted.keys(), 0)[0] % 12 == 4, "Inversion 1 puts E in the bass");
        VoiceLeadLogic.Result down = VoiceLeadLogic.plan(cToG, false, VoiceLeadLogic.Bass.OCTAVE_DOWN, null, VoiceLeadLogic.Voicing.KEEP);
        check(Arrays.equals(at(cToG, down.keys(), 0), new int[] {48, 64, 67}), "Lowest -1 Oct");
        VoiceLeadLogic.Result drop2 = VoiceLeadLogic.plan(cToG, false, VoiceLeadLogic.Bass.FREE, null, VoiceLeadLogic.Voicing.DROP_2);
        check(Arrays.equals(at(cToG, drop2.keys(), 0), new int[] {52, 60, 67}), "Drop 2 drops the second voice from the top");
        // Every note keeps its pitch class.
        for (VoiceLeadLogic.Result r : List.of(smooth, root, keep, inverted, down, drop2)) {
            for (VoiceLeadLogic.In n : cToG) {
                check(Math.floorMod(r.keys()[n.index()] - n.key(), 12) == 0, "pitch class kept");
            }
        }
        // A held note doesn't move, and the next chord is read with it.
        List<VoiceLeadLogic.In> held = notes(new int[][] {{60, 0, 8}, {64, 0, 4}, {67, 0, 4}, {65, 4, 8}, {69, 4, 8}});
        VoiceLeadLogic.Result sustain = VoiceLeadLogic.plan(held, true, VoiceLeadLogic.Bass.ROOT, null, VoiceLeadLogic.Voicing.KEEP);
        check(sustain.chords() == 2 && sustain.keys()[0] == 60, "the held C stays");
        check(Arrays.stream(new int[] {sustain.keys()[0], sustain.keys()[3], sustain.keys()[4]}).min().getAsInt() % 12 == 5, "F root under the held C");
        // Notes a little apart are one chord; a melody is no chord.
        List<VoiceLeadLogic.In> strum = List.of(new VoiceLeadLogic.In(0, 0, 0, 60, 0, 4), new VoiceLeadLogic.In(1, 0, 0, 64, 0.005, 3.5),
                new VoiceLeadLogic.In(2, 0, 0, 67, 0.012, 4));
        check(VoiceLeadLogic.plan(strum, false, VoiceLeadLogic.Bass.FREE, 1, VoiceLeadLogic.Voicing.KEEP).chords() == 1, "one chord");
        List<VoiceLeadLogic.In> melody = notes(new int[][] {{60, 0, 1}, {64, 1, 2}, {67, 2, 3}});
        check(VoiceLeadLogic.plan(melody, true, VoiceLeadLogic.Bass.ROOT, null, VoiceLeadLogic.Voicing.KEEP).chords() == 0, "no chords in a melody");
        // The order of the selection doesn't matter.
        List<VoiceLeadLogic.In> reversed = new ArrayList<>(cToG);
        Collections.reverse(reversed);
        check(Arrays.equals(at(reversed, VoiceLeadLogic.plan(reversed, true, VoiceLeadLogic.Bass.FREE, null, VoiceLeadLogic.Voicing.KEEP).keys(), 4),
                new int[] {59, 62, 67}), "order doesn't matter");
        // Clips and channels lead separately.
        List<VoiceLeadLogic.In> scoped = new ArrayList<>(cToG);
        int i = scoped.size();
        for (int key : new int[] {72, 76, 79}) {
            scoped.add(new VoiceLeadLogic.In(i++, 1, 0, key, 4, 8));
            scoped.add(new VoiceLeadLogic.In(i++, 0, 1, key, 4, 8));
        }
        VoiceLeadLogic.Result separate = VoiceLeadLogic.plan(scoped, true, VoiceLeadLogic.Bass.FREE, null, VoiceLeadLogic.Voicing.KEEP);
        for (int j = cToG.size(); j < scoped.size(); j++) {
            check(separate.keys()[j] == scoped.get(j).key(), "a chord alone in its clip or channel stays");
        }
        refused(() -> VoiceLeadLogic.plan(cToG, false, VoiceLeadLogic.Bass.FREE, null, VoiceLeadLogic.Voicing.DROP_4), "Drop 4 on triads");
        check(VoiceLeadLogic.rootPitchClass(new int[] {64, 67, 72}) == 0, "C/E has root C");
        check(VoiceLeadLogic.rootPitchClass(new int[] {60, 61}) == -1, "a dyad has no root");
        System.out.println("voice-lead logic: PASS");
    }

    static void refused(Runnable run, String what) {
        try {
            run.run();
        } catch (IllegalArgumentException expected) {
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
