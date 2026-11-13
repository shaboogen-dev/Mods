package com.bitwig.mods.miditools;

import bml.pianoroll.internal.PreviewRule;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The guidance step must match precisely what the outcome will be (user, 8 Nov). For every tool and every setting
 * reachable here, the notes Apply would leave are exactly the notes the preview shows: real notes it leaves alone,
 * plus its preview notes (the outcome's new and changed notes, and plain stand-ins for kept notes a hiding range
 * reaches). The tools' plans are written here as the edits each tool's plan() adds, in the same order, and the hiding
 * uses the framework's own PreviewRule. Compile with the logic classes and the framework's PreviewRule.java.
 */
public final class PreviewMatchTest {
    private static int cases;
    // Copies that would have landed on a note of their lane had they gone in when made (Piano Roll dev.9 and before):
    // Bitwig's insert cuts such a note, which is how Apply came to differ from the preview (user test, 8 Nov).
    private static int landedOnANoteIfPutInAtOnce;

    /** A note's place and velocity, as Apply's result and the preview are compared. */
    record Shape(int channel, int key, double start, double end, double velocity) {
        String lane() {
            return channel + ":" + key;
        }
    }

    /** One note of the plan: where it starts (null for a new one) and where it is after the edits so far. */
    static final class Entry {
        final Shape original;
        Shape now;
        boolean removed;

        Entry(Shape original, Shape now) {
            this.original = original;
            this.now = now;
        }
    }

    /** The clip's notes and a plan's edits on them, as Edit carries them out. */
    static final class Clip implements LandLogic.Edits<Entry> {
        final List<Entry> notes = new ArrayList<>();

        Entry add(int key, double start, double end) {
            Shape shape = new Shape(0, key, start, end, 0.75);
            Entry entry = new Entry(shape, shape);
            notes.add(entry);
            return entry;
        }

        @Override
        public Entry copy(Entry note, int key, double start, double end) {
            Shape from = note.now;
            Entry copy = new Entry(null, new Shape(from.channel, key, start, end, from.velocity));
            if (overlapsALiveNote(copy)) {
                landedOnANoteIfPutInAtOnce++;
            }
            notes.add(copy);
            return copy;
        }

        /** Another note of its lane, there now, overlapping it: Bitwig's insert would cut that note. */
        boolean overlapsALiveNote(Entry note) {
            for (Entry other : notes) {
                if (other != note && !other.removed && other.now.lane().equals(note.now.lane())
                        && other.now.start < note.now.end - PreviewRule.TOLERANCE
                        && other.now.end > note.now.start + PreviewRule.TOLERANCE) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void trimStart(Entry note, double start) {
            Shape s = note.now;
            note.now = new Shape(s.channel, s.key, start, s.end, s.velocity);
        }

        @Override
        public void remove(Entry note) {
            note.removed = true;
        }

        void copyIfAbsent(Entry note, int key, double start, double end) {
            for (Entry other : notes) {
                if (!other.removed && other.now.channel == note.now.channel && other.now.key == key
                        && Math.abs(other.now.start - start) <= PreviewRule.TOLERANCE
                        && Math.abs(other.now.end - end) <= PreviewRule.TOLERANCE) {
                    return;
                }
            }
            copy(note, key, start, end);
        }

        void move(Entry note, double start) {
            Shape s = note.now;
            note.now = new Shape(s.channel, s.key, start, start + (s.end - s.start), s.velocity);
        }

        @Override
        public void setEnd(Entry note, double end) {
            Shape s = note.now;
            note.now = new Shape(s.channel, s.key, s.start, end, s.velocity);
        }

        @Override
        public void setKey(Entry note, int key) {
            Shape s = note.now;
            note.now = new Shape(s.channel, key, s.start, s.end, s.velocity);
            // Edit.setKey puts the note in its new lane at once, where Bitwig's insert cuts a note it lands on: the
            // plans must have made room first, or Apply would differ from the preview.
            if (key != s.key) {
                check(!overlapsALiveNote(note), "a note moved to key " + key + " lands on a note there: " + note.now);
            }
        }

        void setVelocity(Entry note, double velocity) {
            Shape s = note.now;
            note.now = new Shape(s.channel, s.key, s.start, s.end, velocity);
        }

        /** Apply's range clear: every other note overlapping a selected note's range, on any key. */
        void clear(List<Entry> selected) {
            for (Entry note : selected) {
                for (Entry other : notes) {
                    if (!selected.contains(other) && other.original != null
                            && ClearLogic.clears(note.original.start, note.original.end, other.original.start, other.original.end)) {
                        other.removed = true;
                    }
                }
            }
        }
    }

    /** What Apply leaves. */
    static Map<Shape, Integer> outcome(Clip clip) {
        Map<Shape, Integer> left = new HashMap<>();
        for (Entry note : clip.notes) {
            if (!note.removed) {
                left.merge(note.now, 1, Integer::sum);
            }
        }
        return left;
    }

    /** What the preview shows, by the framework's rule: preview(hideChanged = true, hideRemoved = true). */
    static Map<Shape, Integer> preview(Clip clip) {
        Map<String, List<double[]>> removedByLane = new HashMap<>();
        for (Entry note : clip.notes) {
            if (note.original != null && note.removed) {
                removedByLane.computeIfAbsent(note.original.lane(), l -> new ArrayList<>())
                             .add(new double[] {note.original.start, note.original.end});
            }
        }
        Map<String, List<double[]>> ranges = new HashMap<>();
        removedByLane.forEach((lane, list) -> ranges.put(lane, PreviewRule.ranges(list)));
        Map<Shape, Integer> shown = new HashMap<>();
        for (Entry note : clip.notes) {
            boolean changed = note.original != null && !note.removed && !note.now.equals(note.original);
            if (!note.removed && (note.original == null || changed)) {
                shown.merge(note.now, 1, Integer::sum); // a preview note, striped, in its new place
            }
            if (note.original == null) {
                continue;
            }
            // The real note, as Bitwig draws it while the preview shows.
            List<double[]> lane = ranges.get(note.original.lane());
            boolean hiddenByRange = lane != null && PreviewRule.reached(note.original.start, note.original.end, lane);
            boolean hiddenByLink = changed; // its preview note stands in for it
            if (!hiddenByRange && !hiddenByLink) {
                shown.merge(note.original, 1, Integer::sum); // seen as it is, plain
            }
            if (hiddenByRange && !note.removed && !changed) {
                shown.merge(note.original, 1, Integer::sum); // the framework's plain stand-in for a kept note
            }
        }
        return shown;
    }

    static void same(Clip clip, String what) {
        cases++;
        // The copies go in after every other edit (Edit.insertCopies): then none may land on a note of its lane, or
        // Bitwig's insert would cut that note and Apply would differ from this model and from the preview.
        for (Entry note : clip.notes) {
            if (note.original == null && !note.removed) {
                check(!clip.overlapsALiveNote(note), what + ": a copy would land on a note of its lane: " + note.now);
            }
        }
        Map<Shape, Integer> left = outcome(clip);
        Map<Shape, Integer> shown = preview(clip);
        check(left.equals(shown), what + ": preview " + shown + " but Apply leaves " + left);
    }

    /** A selected chord at beat 0 to 1, with notes around it: one cleared, one same-key note touching the cleared one. */
    static Clip around(List<Entry> selected, int... chordKeys) {
        Clip clip = new Clip();
        for (int key : chordKeys) {
            selected.add(clip.add(key, 0, 1));
        }
        clip.add(65, 0.5, 1.5); // overlaps the selection: cleared
        clip.add(65, 1.5, 2.5); // touches the cleared note in its lane, outside the selection: kept, reached
        clip.add(60, 1, 2); // touches the selection: kept
        clip.add(72, 3, 4); // far away
        return clip;
    }

    public static void main(String[] args) {
        // Stack Chords: every chord, inversion and octave on C3.
        for (StackLogic.Chord chord : StackLogic.Chord.values()) {
            if (chord == StackLogic.Chord.SCALE_TRIAD) {
                continue;
            }
            for (int inversion = -3; inversion <= 3; inversion++) {
                for (int octave = -2; octave <= 2; octave++) {
                    List<Entry> selected = new ArrayList<>();
                    Clip clip = around(selected, 60);
                    clip.clear(selected);
                    Entry root = selected.get(0);
                    int[] keys = StackLogic.voicing(60, chord.intervals, inversion, octave);
                    StackLogic.Plan plan = StackLogic.plan(60, keys);
                    for (int key : plan.copies()) {
                        clip.copyIfAbsent(root, key, root.now.start, root.now.end);
                    }
                    clip.setKey(root, plan.rootKey());
                    same(clip, "Stack " + chord.label + " inversion " + inversion + " octave " + octave);
                }
            }
        }
        // Strum.
        int[] percents = {-100, -50, 0, 50, 100};
        for (int low : percents) {
            for (int high : percents) {
                for (int tension : percents) {
                    List<Entry> selected = new ArrayList<>();
                    Clip clip = around(selected, 60, 64, 67);
                    clip.clear(selected);
                    List<StrumLogic.In> in = new ArrayList<>();
                    for (int i = 0; i < selected.size(); i++) {
                        Shape s = selected.get(i).now;
                        in.add(new StrumLogic.In(i, 0, s.channel, s.key, s.start, s.end));
                    }
                    for (StrumLogic.Out out : StrumLogic.plan(in, low, high, tension)) {
                        clip.move(selected.get(out.index()), out.start());
                        clip.setEnd(selected.get(out.index()), out.end());
                    }
                    same(clip, "Strum " + low + " " + high + " " + tension);
                }
            }
        }
        // Arpeggiate, with chord notes dropped where a style has no step for them.
        for (ArpLogic.Style style : ArpLogic.Style.values()) {
            for (ArpLogic.Rate rate : ArpLogic.Rate.values()) {
                for (int steps = 1; steps <= 3; steps++) {
                    for (int gate : new int[] {10, 50, 100}) {
                        List<Entry> selected = new ArrayList<>();
                        Clip clip = around(selected, 60, 64, 67);
                        clip.clear(selected);
                        List<ArpLogic.In> in = new ArrayList<>();
                        for (int i = 0; i < selected.size(); i++) {
                            Shape s = selected.get(i).now;
                            in.add(new ArpLogic.In(i, s.key, s.start, s.end));
                        }
                        ArpLogic.Plan plan = ArpLogic.plan(in, style, rate, gate, steps, 12);
                        for (ArpLogic.Out out : plan.notes()) {
                            if (!out.sameNote()) {
                                clip.copy(selected.get(out.source()), out.key(), out.start(), out.end());
                            }
                        }
                        for (ArpLogic.Out out : plan.notes()) {
                            if (out.sameNote()) {
                                clip.move(selected.get(out.source()), out.start());
                                clip.setEnd(selected.get(out.source()), out.end());
                            }
                        }
                        for (int unused : plan.unused()) {
                            selected.get(unused).removed = true;
                        }
                        same(clip, "Arpeggiate " + style + " " + rate + " steps " + steps + " gate " + gate);
                    }
                }
            }
        }
        // Chop.
        for (int parts = ChopLogic.MIN_PARTS; parts <= 8; parts++) {
            for (int gaps = -3; gaps <= 3; gaps++) {
                for (int variation : new int[] {0, 40, 100}) {
                    List<Entry> selected = new ArrayList<>();
                    Clip clip = around(selected, 60, 64);
                    clip.clear(selected);
                    chop(clip, selected, ChopLogic.parts(inputs(selected), parts, gaps, variation));
                    same(clip, "Chop " + parts + " " + gaps + " " + variation);
                }
            }
        }
        // Chop Rhythm, with notes that have no hit removed.
        for (RhythmPattern rhythm : RhythmPattern.catalog()) {
            for (int gate : new int[] {0, 50, 100}) {
                for (int accent : new int[] {0, 100}) {
                    for (boolean tail : new boolean[] {false, true}) {
                        List<Entry> selected = new ArrayList<>();
                        Clip clip = new Clip();
                        selected.add(clip.add(60, 0, 4));
                        selected.add(clip.add(64, 0, 0.25)); // a short note a rhythm may leave without a hit
                        clip.add(67, 1, 2); // cleared
                        clip.add(67, 2, 3); // kept, reached through the cleared note's lane
                        clip.clear(selected);
                        chop(clip, selected, ChopLogic.rhythm(inputs(selected), rhythm, gate, 30, accent, tail));
                        same(clip, "Chop Rhythm " + rhythm.name() + " gate " + gate + " accent " + accent + " tail " + tail);
                    }
                }
            }
        }
        // Quantize and Time Warp: no range clear, only the selected notes move, onto and past the notes around them.
        double[][] timings = {{0.1, 0.9}, {0.55, 1.2}, {1.3, 1.35}, {2.05, 3.1}, {2.6, 2.9}};
        for (QuantizeLogic.Snap snap : QuantizeLogic.Snap.values()) {
            for (ArpLogic.Rate rate : ArpLogic.Rate.values()) {
                for (int amount : new int[] {0, 30, 50, 100}) {
                    Clip clip = new Clip();
                    List<Entry> selected = moving(clip, timings);
                    for (Entry note : selected) {
                        QuantizeLogic.Out out = QuantizeLogic.place(note.now.start, note.now.end, rate.beats, snap, amount);
                        if (Math.abs(out.start() - note.now.start) > ClearLogic.TOLERANCE
                                || Math.abs(out.end() - note.now.end) > ClearLogic.TOLERANCE) {
                            clip.move(note, out.start());
                            clip.setEnd(note, out.end());
                        }
                    }
                    same(clip, "Quantize " + snap + " " + rate + " " + amount);
                }
            }
        }
        for (TimeWarpLogic.Mode mode : TimeWarpLogic.Mode.values()) {
            for (int amount = 0; amount <= 100; amount += 10) {
                for (boolean keep : new boolean[] {true, false}) {
                    Clip clip = new Clip();
                    List<Entry> selected = moving(clip, timings);
                    List<TimeWarpLogic.In> in = new ArrayList<>();
                    for (int i = 0; i < selected.size(); i++) {
                        in.add(new TimeWarpLogic.In(i, 0, selected.get(i).now.start, selected.get(i).now.end));
                    }
                    for (TimeWarpLogic.Out out : TimeWarpLogic.plan(in, mode, amount, keep)) {
                        clip.move(selected.get(out.index()), out.start());
                        clip.setEnd(selected.get(out.index()), out.end());
                    }
                    same(clip, "Time Warp " + mode + " " + amount + " " + keep);
                }
            }
        }
        newTools();
        check(landedOnANoteIfPutInAtOnce > 0, "the old insertion order is seen to cut notes");
        System.out.println("preview matches Apply: PASS (" + cases + " cases; " + landedOnANoteIfPutInAtOnce
                + " copies would have cut a note if put in when made; " + refused + " settings refused, as the tools would say: " + REFUSALS + ")");
    }

    private static int refused;
    private static final Map<String, Integer> REFUSALS = new java.util.TreeMap<>();

    static void refused(String why) {
        refused++;
        REFUSALS.merge(why, 1, Integer::sum);
    }

    /** Note Articulation, Voice Lead Chords, Extract Bass and Chop Rhythm's Evolve (dev.11). */
    static void newTools() {
        // Note Articulation: no range clear, only the selected notes' ends change, onto and past the notes around them.
        double[][] timings = {{0.1, 0.9}, {0.55, 1.2}, {1.3, 1.35}, {2.05, 3.1}, {2.6, 2.9}};
        for (ArticulationLogic.Style style : ArticulationLogic.Style.values()) {
            for (ArpLogic.Rate rate : ArpLogic.Rate.values()) {
                for (int offset : new int[] {-100, -30, 0, 50, 100}) {
                    for (int variation : new int[] {-100, 0, 60}) {
                        Clip clip = new Clip();
                        List<Entry> selected = moving(clip, timings);
                        List<ArticulationLogic.In> in = new ArrayList<>();
                        for (int i = 0; i < selected.size(); i++) {
                            Shape s = selected.get(i).now;
                            in.add(new ArticulationLogic.In(i, 0, s.channel, s.key, s.start, s.end));
                        }
                        for (ArticulationLogic.Out out : ArticulationLogic.plan(in, style, rate.beats, offset, variation)) {
                            clip.setEnd(selected.get(out.index()), out.end());
                        }
                        same(clip, "Note Articulation " + style + " " + rate + " " + offset + " " + variation);
                    }
                }
            }
        }
        // Voice Lead Chords: every option, on a progression with and without a held note, among notes on the keys it
        // moves to. Notes it would land on are cut first (LandLogic), as the tool's own edits.
        int voiceLead = 0;
        for (boolean smooth : new boolean[] {true, false}) {
            for (VoiceLeadLogic.Bass bass : VoiceLeadLogic.Bass.values()) {
                for (int inversion = -4; inversion <= 3; inversion++) { // -4: Keep
                    for (VoiceLeadLogic.Voicing voicing : VoiceLeadLogic.Voicing.values()) {
                        for (boolean held : new boolean[] {false, true}) {
                            Clip clip = new Clip();
                            List<Entry> selected = progression(clip, held);
                            List<Entry> all = new ArrayList<>(clip.notes);
                            List<LandLogic.N> plain = plain(all);
                            List<VoiceLeadLogic.In> in = new ArrayList<>();
                            for (int i = 0; i < selected.size(); i++) {
                                LandLogic.N n = plain.get(i);
                                in.add(new VoiceLeadLogic.In(i, 0, n.channel(), n.key(), n.start(), n.end()));
                            }
                            String what = "Voice Lead " + smooth + " " + bass + " " + inversion + " " + voicing + " " + held;
                            try {
                                VoiceLeadLogic.Result result = VoiceLeadLogic.plan(in, smooth, bass, inversion < -3 ? null : inversion, voicing);
                                LandLogic.moveKeys(all, plain, result.keys(), clip);
                                voiceLead++;
                            } catch (IllegalArgumentException | LandLogic.Collision e) {
                                refused("Voice Lead: " + e.getMessage());
                                continue;
                            }
                            for (Entry note : selected) {
                                check(Math.floorMod(note.now.key - note.original.key, 12) == 0, what + ": a note changed pitch class");
                            }
                            same(clip, what);
                        }
                    }
                }
            }
        }
        check(voiceLead > 100, "Voice Lead mostly plans: " + voiceLead);
        // Extract Bass: both outputs and every octave, in C major and with no key, among notes on the bass's keys.
        BitSet major = new BitSet(12);
        for (int step : new int[] {0, 2, 4, 5, 7, 9, 11}) {
            major.set(step);
        }
        for (BassLogic.Scale scale : new BassLogic.Scale[] {new BassLogic.Scale(0, major), null}) {
            for (boolean replace : new boolean[] {false, true}) {
                for (int octave = -2; octave <= 0; octave++) {
                    for (boolean held : new boolean[] {false, true}) {
                        Clip clip = new Clip();
                        List<Entry> selected = progression(clip, held);
                        List<Entry> all = new ArrayList<>(clip.notes);
                        List<LandLogic.N> plain = plain(all);
                        List<BassLogic.In> in = new ArrayList<>();
                        for (int i = 0; i < selected.size(); i++) {
                            LandLogic.N n = plain.get(i);
                            in.add(new BassLogic.In(i, 0, n.channel(), n.key(), n.start(), n.end()));
                        }
                        String what = "Extract Bass " + (scale != null) + " " + replace + " " + octave + " " + held;
                        BassLogic.Output out = BassLogic.output(BassLogic.plan(in, scale, octave), plain, replace);
                        if (out.isEmpty()) { // Add Below at octave 0 when every chord has its root lowest: refused
                            check(!replace && octave == 0, what + ": does something");
                            refused("Extract Bass: every root already sounds");
                            continue;
                        }
                        LandLogic.write(all, plain, out.removed(), out.newKeys(), out.copies(),
                                LandLogic.plan(plain, out.removed(), out.newKeys(), out.copies()), clip);
                        same(clip, what);
                    }
                }
            }
        }
        // Chop Rhythm's Evolve: rhythms across the catalog and every option, with the range clear.
        int evolved = 0;
        List<RhythmPattern> catalog = RhythmPattern.catalog();
        for (int r = 0; r < catalog.size(); r += 8) {
            for (int variation : new int[] {0, 50, 100}) {
                for (int cycle = 0; cycle < EvolveLogic.CYCLES.length; cycle++) {
                    for (int roll = 0; roll < EvolveLogic.ROLLS.length; roll++) {
                        for (int harmony = 0; harmony < EvolveLogic.HARMONIES.length; harmony++) {
                            for (int phrase : new int[] {0, 2}) {
                                for (boolean tail : new boolean[] {false, true}) {
                                    Clip clip = new Clip();
                                    List<Entry> selected = new ArrayList<>();
                                    int[][] chords = {{48, 60, 64, 67}, {53, 60, 65, 69}, {55, 62, 67, 71}, {48, 60, 64, 67}};
                                    for (int c = 0; c < chords.length; c++) {
                                        for (int key : chords[c]) {
                                            selected.add(clip.add(key, c * 4, c * 4 + 4));
                                        }
                                    }
                                    clip.add(72, 2, 6); // cleared
                                    clip.add(72, 17, 18); // kept
                                    clip.add(60, 16, 17); // touches the last chord's end: kept
                                    clip.clear(selected);
                                    String what = "Evolve " + catalog.get(r).name() + " " + variation + " " + cycle + " " + roll
                                            + " " + harmony + " " + phrase + " " + tail;
                                    try {
                                        chop(clip, selected, EvolveLogic.plan(inputs(selected), catalog.get(r), 60, 20, 100, tail,
                                                new EvolveLogic.Options(phrase, variation, cycle, roll, harmony)));
                                        evolved++;
                                    } catch (EvolveLogic.Refusal e) {
                                        refused("Evolve: " + e.getMessage());
                                        continue;
                                    }
                                    same(clip, what);
                                }
                            }
                        }
                    }
                }
            }
        }
        check(evolved > 1000, "Evolve mostly plans: " + evolved);
    }

    /**
     * A selected progression (C, F, G, Am, a beat each; held: the F chord's top note held into the G chord), with
     * unselected notes on every other key overlapping it, where re-voiced notes and bass notes land.
     */
    static List<Entry> progression(Clip clip, boolean held) {
        List<Entry> selected = new ArrayList<>();
        int[][] chords = {{60, 64, 67}, {65, 69, 72}, {67, 71, 74}, {57, 60, 64}};
        for (int c = 0; c < chords.length; c++) {
            for (int key : chords[c]) {
                boolean hold = held && c == 1 && key == 72;
                selected.add(clip.add(key, c, hold ? c + 1.5 : c + 1));
            }
        }
        for (int key = 33; key <= 84; key += 2) {
            double start = key % 4 == 1 ? 0.5 : 1.75;
            clip.add(key, start, start + 1.5);
        }
        clip.add(36, 3.2, 3.6); // inside a chord's time, on a bass key
        return selected;
    }

    static List<LandLogic.N> plain(List<Entry> notes) {
        List<LandLogic.N> plain = new ArrayList<>();
        for (int i = 0; i < notes.size(); i++) {
            Shape s = notes.get(i).now;
            plain.add(new LandLogic.N(i, 0, s.channel, s.key, s.start, s.end));
        }
        return plain;
    }

    /** Selected notes on two keys at these times, with unselected notes on the same keys and others around them. */
    static List<Entry> moving(Clip clip, double[][] timings) {
        List<Entry> selected = new ArrayList<>();
        for (int i = 0; i < timings.length; i++) {
            selected.add(clip.add(i % 2 == 0 ? 60 : 64, timings[i][0], timings[i][1]));
        }
        clip.add(60, 1.0, 1.25); // same key, between selected notes: moved notes may land on it
        clip.add(64, 2.0, 2.5);
        clip.add(48, 0, 4); // a held bass under them all: kept
        clip.add(67, 3.5, 4);
        return selected;
    }

    static List<ChopLogic.In> inputs(List<Entry> selected) {
        List<ChopLogic.In> in = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            Shape s = selected.get(i).now;
            in.add(new ChopLogic.In(i, s.key, s.channel, s.start, s.end, s.velocity * 127.0));
        }
        return in;
    }

    /** As ChopWriter.write. */
    static void chop(Clip clip, List<Entry> notes, ChopLogic.Plan plan) {
        for (ChopLogic.Out out : plan.slices()) {
            if (!out.sameNote()) {
                Entry source = notes.get(out.source());
                if (out.velocity127() < 0) {
                    clip.copyIfAbsent(source, source.now.key, out.start(), out.end());
                } else {
                    clip.setVelocity(clip.copy(source, source.now.key, out.start(), out.end()), out.velocity127() / 127.0);
                }
            }
        }
        for (ChopLogic.Out out : plan.slices()) {
            if (out.sameNote()) {
                Entry note = notes.get(out.source());
                clip.move(note, out.start());
                clip.setEnd(note, out.end());
                if (out.velocity127() >= 0) {
                    clip.setVelocity(note, out.velocity127() / 127.0);
                }
            }
        }
        for (int silenced : plan.silenced()) {
            notes.get(silenced).removed = true;
        }
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }
}
