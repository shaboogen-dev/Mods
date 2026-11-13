package com.bitwig.mods.capturemidi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

/**
 * Turns buffered MIDI into a clip: notes paired from their on and off messages, the latest phrase
 * chosen, and the notes placed in beats. No Bitwig code here, so it can be tested on its own.
 */
final class CapturePlan {
    /** A note played a little before a bar line still counts as that bar's downbeat (beats). */
    static final double EARLY = 0.125;
    /** Shortest note written (beats), so a very short tap is still a note. */
    static final double SHORTEST = 1.0 / 128;

    private CapturePlan() {
    }

    /** One played note, in nanoTime. */
    record Note(int channel, int pitch, int velocity, long start, long end) {
    }

    /** A note in the clip, in beats from the clip's start. */
    record Placed(int channel, int pitch, double velocity, double start, double length) {
    }

    /** Where the clip goes (arranger beat) and what it holds. */
    record Clip(double start, double length, List<Placed> notes) {
    }

    /**
     * Pairs note-ons with note-offs per input, channel and pitch. A note released while that input's
     * sustain pedal is down lasts until the pedal comes up (or the same key is struck again), so the
     * clip sounds as it was played without the pedal messages. Notes still held end at {@code now}.
     * Note-offs without a note-on (a note started before the buffer or the last capture) are ignored.
     */
    static List<Note> notes(NoteBuffer.Snapshot s, long now) {
        List<Note> out = new ArrayList<>();
        Map<Long, ArrayDeque<long[]>> held = new HashMap<>(); // key -> {start, velocity}
        Map<Long, List<long[]>> sustained = new HashMap<>(); // input+channel -> {start, velocity, key}
        Map<Long, Boolean> pedal = new HashMap<>();
        for (int i = 0; i < s.time().length; i++) {
            int m = s.message()[i];
            int type = m & 0xf0, channel = m & 0x0f, d1 = (m >>> 8) & 0x7f, d2 = (m >>> 16) & 0x7f;
            long t = s.time()[i];
            long input = ((long) s.source()[i] << 4) | channel;
            long key = (input << 7) | d1;
            if (type == 0xb0) { // sustain pedal
                boolean down = d2 >= 64;
                pedal.put(input, down);
                if (!down) {
                    List<long[]> waiting = sustained.remove(input);
                    if (waiting != null) for (long[] n : waiting) out.add(note(channel, (int) n[2] & 0x7f, n, t));
                }
                continue;
            }
            if (type == 0x90 && d2 > 0) {
                List<long[]> waiting = sustained.get(input);
                if (waiting != null) { // the same key struck again ends its sustained note
                    for (int w = waiting.size() - 1; w >= 0; w--) {
                        if (waiting.get(w)[2] == key) out.add(note(channel, d1, waiting.remove(w), t));
                    }
                }
                held.computeIfAbsent(key, k -> new ArrayDeque<>()).addLast(new long[] {t, d2, key});
                continue;
            }
            ArrayDeque<long[]> on = held.get(key); // a note-off, or a note-on with velocity 0
            if (on == null || on.isEmpty()) continue;
            long[] started = on.removeFirst();
            if (Boolean.TRUE.equals(pedal.get(input))) sustained.computeIfAbsent(input, k -> new ArrayList<>()).add(started);
            else out.add(note(channel, d1, started, t));
        }
        for (ArrayDeque<long[]> on : held.values()) {
            for (long[] n : on) out.add(note((int) (n[2] >>> 7) & 0x0f, (int) n[2] & 0x7f, n, now));
        }
        for (List<long[]> waiting : sustained.values()) {
            for (long[] n : waiting) out.add(note((int) (n[2] >>> 7) & 0x0f, (int) n[2] & 0x7f, n, now));
        }
        out.sort(Comparator.comparingLong(Note::start).thenComparingInt(Note::pitch));
        return out;
    }

    private static Note note(int channel, int pitch, long[] started, long end) {
        return new Note(channel, pitch, (int) Math.max(1, Math.min(127, started[1])), started[0], Math.max(started[0], end));
    }

    /**
     * The latest phrase: the notes after the last silence longer than {@code gap} nanoseconds
     * (silence: no note sounding). {@code gap <= 0} keeps every note.
     */
    static List<Note> lastPhrase(List<Note> notes, long gap) {
        if (gap <= 0 || notes.isEmpty()) return notes;
        int from = 0;
        long soundingUntil = Long.MIN_VALUE;
        for (int i = 0; i < notes.size(); i++) {
            Note n = notes.get(i);
            if (i > 0 && n.start() - soundingUntil > gap) from = i;
            if (i == 0 || n.end() - soundingUntil > 0) soundingUntil = n.end();
        }
        return notes.subList(from, notes.size());
    }

    /**
     * With the transport going round the arranger loop: the notes of the most recent complete pass
     * over the loop, and nothing else (no earlier passes stacked, nothing before the loop start).
     * Times are in nanoTime, the loop in song seconds: {@code nowSeconds} is the play position,
     * inside [{@code loopStart}, {@code loopEnd}). The pass playing now isn't complete, so the one
     * before it is kept: the notes that started in it. They are moved one pass later in time, so that
     * counting back from the play position puts them in the loop range (each pass plays the same song
     * seconds, so tempo changes inside the loop are kept). A note held past the loop end is cut there.
     * Notes from before the play started can't be told apart from a pass, so they count as one.
     */
    static List<Note> lastLoopPass(List<Note> notes, long now, double nowSeconds, double loopStart, double loopEnd) {
        double passSeconds = loopEnd - loopStart;
        if (!(passSeconds > 0) || nowSeconds < loopStart || nowSeconds >= loopEnd) return List.of();
        long pass = Math.round(passSeconds * 1e9);
        long currentStarted = now - Math.round((nowSeconds - loopStart) * 1e9); // this pass began then
        long from = currentStarted - pass, to = currentStarted; // the last complete pass
        long loopEndNow = now + Math.round((loopEnd - nowSeconds) * 1e9); // the loop end of this pass
        List<Note> out = new ArrayList<>();
        for (Note n : notes) {
            if (n.start() - from < 0 || n.start() - to >= 0) continue;
            long start = n.start() + pass;
            long end = Math.min(n.end() + pass, loopEndNow);
            out.add(new Note(n.channel(), n.pitch(), n.velocity(), start, Math.max(start, end)));
        }
        return out;
    }

    /**
     * Places the notes. While playing, every note keeps the beat it was played on (counted back from
     * the play position at the current tempo) and the clip spans the whole bars around them. While
     * stopped there is no beat to keep: the first note becomes the clip's downbeat, at the tempo, and
     * the clip goes at the bar of the play position. {@code bar} is a bar's length in beats.
     * Playing, the notes are counted back at the constant tempo {@code bpm}.
     */
    static Clip place(List<Note> notes, long now, double bpm, double bar, boolean playing, double playhead) {
        double beatsPerSecond = bpm / 60;
        return place(notes, now, bpm, bar, playing, playhead, secondsAgo -> playhead - secondsAgo * beatsPerSecond);
    }

    /**
     * As {@link #place(List, long, double, double, boolean, double)}, with {@code beatAgo} giving the
     * song beat that played a number of seconds before {@code now} (the project's tempo map, so tempo
     * changes during the phrase are followed). Used only while playing.
     */
    static Clip place(List<Note> notes, long now, double bpm, double bar, boolean playing, double playhead,
                      DoubleUnaryOperator beatAgo) {
        if (notes.isEmpty() || !(bpm > 0) || !(bar > 0)) return null;
        double beatsPerNano = bpm / 60e9;
        long origin = notes.get(0).start();
        double first = Double.MAX_VALUE, lastStart = -Double.MAX_VALUE, lastEnd = -Double.MAX_VALUE;
        double[] starts = new double[notes.size()], ends = new double[notes.size()];
        for (int i = 0; i < notes.size(); i++) {
            Note n = notes.get(i);
            starts[i] = playing ? beatAgo.applyAsDouble((now - n.start()) / 1e9) : (n.start() - origin) * beatsPerNano;
            ends[i] = playing ? beatAgo.applyAsDouble((now - n.end()) / 1e9) : (n.end() - origin) * beatsPerNano;
            first = Math.min(first, starts[i]);
            lastStart = Math.max(lastStart, starts[i]);
            lastEnd = Math.max(lastEnd, ends[i]);
        }
        double clipStart = playing ? Math.floor((first + EARLY) / bar) * bar : 0;
        if (playing && clipStart < 0) clipStart = 0;
        // Whole bars up to the last note; a note held a little past a bar line doesn't add a bar.
        double clipEnd = Math.ceil(Math.max(lastStart + EARLY, lastEnd - 0.25) / bar - 1e-9) * bar;
        double length = Math.max(bar, clipEnd - clipStart);
        double where = playing ? clipStart : Math.floor(Math.max(0, playhead) / bar + 1e-9) * bar;
        List<Placed> placed = new ArrayList<>();
        for (int i = 0; i < notes.size(); i++) {
            Note n = notes.get(i);
            double s = starts[i] - clipStart, e = ends[i] - clipStart;
            if (s < 0) {
                if (s < -EARLY && e <= 0) continue; // before the song's start
                s = 0;
            }
            if (s >= length) continue;
            placed.add(new Placed(n.channel(), n.pitch(), n.velocity() / 127.0, s, Math.max(SHORTEST, e - s)));
        }
        if (placed.isEmpty()) return null;
        return new Clip(where, length, placed);
    }
}
