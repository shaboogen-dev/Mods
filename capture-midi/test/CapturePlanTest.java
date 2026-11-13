package com.bitwig.mods.capturemidi;

import java.util.List;

/** Offline checks of CapturePlan (no Bitwig). Run: javac -d out src/.../CapturePlan.java src/.../NoteBuffer.java test/CapturePlanTest.java; java -cp out com.bitwig.mods.capturemidi.CapturePlanTest */
public final class CapturePlanTest {
    private static final long S = 1_000_000_000L;

    public static void main(String[] args) {
        // 120 BPM: one beat is 0.5 s. Four quarter notes, the last one still held.
        long t0 = 100 * S;
        NoteBuffer.Snapshot snap = new NoteBuffer.Snapshot(
                new long[] {t0, t0 + S / 4, t0 + S / 2, t0 + 3 * S / 4, t0 + S, t0 + 5 * S / 4, t0 + 3 * S / 2},
                new int[] {on(60, 100), off(60), on(62, 90), off(62), on(64, 80), off(64), on(65, 70)},
                new int[] {1, 1, 1, 1, 1, 1, 1}, 7);
        long now = t0 + 2 * S;
        List<CapturePlan.Note> notes = CapturePlan.notes(snap, now);
        check(notes.size() == 4, "four notes");
        check(notes.get(3).end() == now, "held note ends at the capture");

        // Stopped, play position at beat 9 (bar 3): first note on the downbeat of bar 3.
        CapturePlan.Clip stopped = CapturePlan.place(notes, now, 120, 4, false, 9.0);
        check(stopped.start() == 8.0, "stopped: clip at the play position's bar, got " + stopped.start());
        check(near(stopped.notes().get(0).start(), 0) && near(stopped.notes().get(1).start(), 1), "stopped: beats kept");
        check(stopped.length() == 4.0, "stopped: one bar, got " + stopped.length());

        // Playing, play position now at beat 20: notes were at beats 16, 17, 18, 19.
        CapturePlan.Clip playing = CapturePlan.place(notes, now, 120, 4, true, 20.0);
        check(playing.start() == 16.0, "playing: clip at the notes' bar, got " + playing.start());
        check(near(playing.notes().get(2).start(), 2), "playing: song beats kept");

        // A note played a hair before the bar line is that bar's downbeat.
        CapturePlan.Clip early = CapturePlan.place(notes, now, 120, 4, true, 19.95);
        check(early.start() == 16.0 && early.notes().get(0).start() == 0, "early note snaps to the downbeat");

        // Sustain pedal: a note released under the pedal lasts until the pedal comes up.
        NoteBuffer.Snapshot pedal = new NoteBuffer.Snapshot(
                new long[] {t0, t0 + S / 10, t0 + S / 4, t0 + S},
                new int[] {cc64(127), on(60, 100), off(60), cc64(0)},
                new int[] {3, 3, 3, 3}, 4);
        List<CapturePlan.Note> held = CapturePlan.notes(pedal, t0 + 2 * S);
        check(held.size() == 1 && held.get(0).end() == t0 + S, "sustain extends the note");

        // Phrases: a 10 s silence splits; with a 5 s pause setting only the last phrase is kept.
        NoteBuffer.Snapshot two = new NoteBuffer.Snapshot(
                new long[] {t0, t0 + S / 2, t0 + 11 * S, t0 + 23 * S / 2},
                new int[] {on(48, 100), off(48), on(50, 100), off(50)},
                new int[] {1, 1, 1, 1}, 4);
        List<CapturePlan.Note> both = CapturePlan.notes(two, t0 + 12 * S);
        check(CapturePlan.lastPhrase(both, 5 * S).size() == 1, "last phrase only");
        check(CapturePlan.lastPhrase(both, 0).size() == 2, "pause 0 keeps all");

        // A note-off with no note-on is ignored; two inputs don't close each other's notes.
        NoteBuffer.Snapshot mixed = new NoteBuffer.Snapshot(
                new long[] {t0, t0 + S / 4, t0 + S / 2, t0 + S},
                new int[] {off(70), on(60, 100), off(60), off(60)},
                new int[] {1, 1, 5, 1}, 4);
        List<CapturePlan.Note> m = CapturePlan.notes(mixed, t0 + 2 * S);
        check(m.size() == 1 && m.get(0).end() == t0 + S, "per-input pairing");

        // Playing through a tempo map: here 240 BPM over the phrase (the map, not the 120 passed).
        CapturePlan.Clip mapped = CapturePlan.place(notes, now, 120, 4, true, 20.0, ago -> 20.0 - ago * 4);
        check(mapped.start() == 12.0 && near(mapped.notes().get(2).start(), 4), "playing: tempo map followed, got " + mapped.start());

        // Looping: loop from beat 8 to 16 at 120 BPM (song seconds 4 to 8), play position at beat 10
        // (5 s), so this pass began 1 s ago and the last complete pass ran from 5 s to 1 s ago.
        long n0 = 200 * S;
        NoteBuffer.Snapshot looped = new NoteBuffer.Snapshot(
                new long[] {n0 - 6 * S, n0 - 6 * S + S / 4, n0 - 9 * S / 2, n0 - 4 * S, n0 - 2 * S, n0 - S / 2, n0 - S / 4},
                new int[] {on(40, 100), off(40), on(50, 100), off(50), on(52, 100), on(55, 100), off(55)},
                new int[] {1, 1, 1, 1, 1, 1, 1}, 7);
        List<CapturePlan.Note> all = CapturePlan.notes(looped, n0);
        List<CapturePlan.Note> pass = CapturePlan.lastLoopPass(all, n0, 5.0, 4.0, 8.0);
        check(pass.size() == 2, "loop: only the last complete pass, got " + pass.size());
        CapturePlan.Clip loopClip = CapturePlan.place(pass, n0, 120, 4, true, 10.0);
        check(loopClip.start() == 8.0, "loop: clip at the loop start's bar, got " + loopClip.start());
        check(near(loopClip.notes().get(0).start(), 1) && loopClip.notes().get(0).pitch() == 50,
                "loop: first note at beat 9, got " + loopClip.notes().get(0).start());
        check(near(loopClip.notes().get(1).start(), 6) && near(loopClip.notes().get(1).start() + loopClip.notes().get(1).length(), 8),
                "loop: held note at beat 14, cut at the loop end");
        check(loopClip.length() == 8.0, "loop: two bars, got " + loopClip.length());
        check(CapturePlan.lastLoopPass(all, n0, 9.0, 4.0, 8.0).isEmpty(), "loop: play position outside the loop");

        System.out.println("CAPTURE_PLAN_TESTS_OK");
    }

    private static int on(int pitch, int velocity) { return 0x90 | pitch << 8 | velocity << 16; }
    private static int off(int pitch) { return 0x80 | pitch << 8; }
    private static int cc64(int value) { return 0xb0 | 64 << 8 | value << 16; }
    private static boolean near(double a, double b) { return Math.abs(a - b) < 1e-6; }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
