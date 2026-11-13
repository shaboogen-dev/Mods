package com.bitwig.mods.miditools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extract Bass's logic, in plain values: the root of each chord of a progression, read from the chord and the chords
 * around it in the key signature, as a bass line close to the chords and moving smoothly.
 */
final class BassLogic {
    /** A note: its clip (group), channel, key and times. */
    record In(int index, int group, int channel, int key, double start, double end) {
    }

    /** A key signature: its root as a pitch class, and the scale's semitones above it. */
    record Scale(int root, BitSet mask) {
    }

    /**
     * One chord: the note that carries its bass (the lowest note on the root, or failing that the lowest note), the
     * bass's key, and the chord's other notes starting with it.
     */
    record Chord(int keeper, int bassKey, List<Integer> others, List<Integer> keys) {
    }

    static final double ONSET_TOLERANCE = 0.02;
    static final int MAX_NOTES = 2048;
    private static final long INF = Long.MAX_VALUE / 8;
    private static final int[][] TEMPLATES = {
        {0, 4, 7}, {0, 3, 7}, {0, 3, 6}, {0, 4, 8}, {0, 2, 7}, {0, 5, 7},
        {0, 4, 7, 10}, {0, 4, 7, 11}, {0, 3, 7, 10}, {0, 3, 6, 10},
        {0, 3, 6, 9}, {0, 2, 4, 7}, {0, 2, 3, 7}, {0, 4, 7, 9},
        {0, 3, 7, 9}, {0, 2, 4, 7, 10}, {0, 2, 4, 7, 11},
        {0, 2, 3, 7, 10}, {0, 2, 3}, {0, 4, 5, 7}
    };

    private record Event(List<In> fresh, int[] pitchClasses) {
    }

    private BassLogic() {
    }

    /**
     * The chords, in each clip and channel in time order. octave moves the place the bass is looked for from the
     * chord's lowest note, in octaves (-1: an octave under it). scale may be null: then only the chords decide.
     */
    static List<Chord> plan(List<In> selected, Scale scale, int octave) {
        if (selected.size() > MAX_NOTES) {
            throw new IllegalArgumentException("select at most " + MAX_NOTES + " notes");
        }
        Map<String, List<In>> voices = new LinkedHashMap<>();
        selected.stream().sorted(Comparator.comparingInt(In::group).thenComparingInt(In::channel))
                .forEach(note -> voices.computeIfAbsent(note.group() + ":" + note.channel(), v -> new ArrayList<>()).add(note));
        List<Chord> chords = new ArrayList<>();
        for (List<In> notes : voices.values()) {
            List<Event> sequence = events(notes);
            int[] roots = roots(sequence, scale);
            int previousPitch = -1;
            for (int index = 0; index < sequence.size(); index++) {
                Event event = sequence.get(index);
                int root = roots[index];
                In keeper = event.fresh().stream().filter(note -> Math.floorMod(note.key(), 12) == root)
                                 .min(Comparator.comparingInt(In::key))
                                 .orElseGet(() -> event.fresh().stream().min(Comparator.comparingInt(In::key)).orElseThrow());
                int lowest = event.fresh().stream().mapToInt(In::key).min().orElseThrow();
                int target = bassPitch(root, lowest + 12 * octave, previousPitch);
                List<Integer> others = new ArrayList<>();
                List<Integer> keys = new ArrayList<>();
                for (In note : event.fresh()) {
                    keys.add(note.key());
                    if (note != keeper) {
                        others.add(note.index());
                    }
                }
                chords.add(new Chord(keeper.index(), target, others, keys));
                previousPitch = target;
            }
        }
        return chords;
    }

    private static List<Event> events(List<In> source) {
        List<In> ordered = source.stream().sorted(Comparator.comparingDouble(In::start).thenComparingInt(In::key)).toList();
        List<Event> result = new ArrayList<>();
        for (int index = 0; index < ordered.size();) {
            double start = ordered.get(index).start();
            List<In> fresh = new ArrayList<>();
            do {
                fresh.add(ordered.get(index++));
            } while (index < ordered.size() && ordered.get(index).start() - start <= ONSET_TOLERANCE);
            Set<Integer> classes = new HashSet<>();
            for (In note : ordered) {
                if (note.start() <= start + ONSET_TOLERANCE && note.end() > start + 1.0e-6) {
                    classes.add(Math.floorMod(note.key(), 12));
                }
            }
            result.add(new Event(List.copyOf(fresh), classes.stream().mapToInt(Integer::intValue).sorted().toArray()));
        }
        return result;
    }

    /** The likeliest root of each chord, chosen over the whole sequence (moves by a fourth and to the tonic score). */
    private static int[] roots(List<Event> events, Scale scale) {
        int count = events.size();
        long[][] cost = new long[count][12];
        int[][] previous = new int[count][12];
        int tonic = scale == null ? -1 : scale.root();
        for (long[] row : cost) {
            Arrays.fill(row, INF);
        }
        for (int root = 0; root < 12; root++) {
            cost[0][root] = -localScore(events.get(0), root, scale) - (root == tonic ? 12 : 0);
            previous[0][root] = -1;
        }
        for (int event = 1; event < count; event++) {
            for (int root = 0; root < 12; root++) {
                long local = -localScore(events.get(event), root, scale);
                for (int prior = 0; prior < 12; prior++) {
                    long candidate = cost[event - 1][prior] + local - transitionScore(prior, root, tonic);
                    if (candidate < cost[event][root]) {
                        cost[event][root] = candidate;
                        previous[event][root] = prior;
                    }
                }
            }
        }
        int last = 0;
        long best = INF;
        for (int root = 0; root < 12; root++) {
            long candidate = cost[count - 1][root] - (root == tonic ? 12 : 0);
            if (candidate < best) {
                best = candidate;
                last = root;
            }
        }
        int[] roots = new int[count];
        for (int event = count - 1; event >= 0; event--) {
            roots[event] = last;
            last = previous[event][last];
        }
        return roots;
    }

    private static int localScore(Event event, int root, Scale scale) {
        Set<Integer> observed = new HashSet<>();
        for (int pitchClass : event.pitchClasses()) {
            observed.add(Math.floorMod(pitchClass - root, 12));
        }
        int bestTemplate = Integer.MIN_VALUE;
        for (int[] template : TEMPLATES) {
            Set<Integer> expected = new HashSet<>();
            for (int interval : template) {
                expected.add(interval);
            }
            int matches = 0;
            int extras = 0;
            int missing = 0;
            for (int interval : observed) {
                if (expected.contains(interval)) {
                    matches++;
                } else {
                    extras++;
                }
            }
            for (int interval : expected) {
                if (!observed.contains(interval)) {
                    missing++;
                }
            }
            int score = matches * 26 - extras * 22 - missing * 5;
            if (matches < 2) {
                score -= 45;
            }
            bestTemplate = Math.max(bestTemplate, score);
        }
        int score = bestTemplate + (observed.contains(0) ? 28 : -18);
        if (scale != null) {
            int relative = Math.floorMod(root - scale.root(), 12);
            score += scale.mask().get(relative) ? 16 : -28;
            for (int interval : triad(scale, root)) {
                score += observed.contains(Math.floorMod(interval, 12)) ? 8 : -2;
            }
        }
        int[] raw = event.fresh().stream().mapToInt(In::key).toArray();
        if (raw.length > 0 && VoiceLeadLogic.rootPitchClass(raw) == root) {
            score += 8;
        }
        return score;
    }

    /** The scale's triad on this root, as semitones above it; none when the root is outside the scale. */
    static int[] triad(Scale scale, int root) {
        int degree = Math.floorMod(root - scale.root(), 12);
        if (!scale.mask().get(degree)) {
            return new int[0];
        }
        int[] tones = new int[5];
        int count = 0;
        for (int semitones = 0; semitones <= 24 && count < tones.length; semitones++) {
            if (scale.mask().get(Math.floorMod(degree + semitones, 12))) {
                tones[count++] = semitones;
            }
        }
        return count < 5 ? new int[0] : new int[] {tones[0], tones[2], tones[4]};
    }

    private static int transitionScore(int from, int to, int tonic) {
        int movement = Math.floorMod(to - from, 12);
        int score = from == to ? 3 : (movement == 5 ? 7 : 0);
        if (tonic < 0) {
            return score;
        }
        int fromDegree = Math.floorMod(from - tonic, 12);
        int toDegree = Math.floorMod(to - tonic, 12);
        if (fromDegree == 7 && toDegree == 0) {
            score += 30; // V to I
        }
        if (fromDegree == 11 && toDegree == 0) {
            score += 18; // vii to I
        }
        if (fromDegree == 5 && toDegree == 7) {
            score += 10; // IV to V
        }
        if (fromDegree == 8 && toDegree == 10) {
            score += 6; // VI to VII
        }
        if (fromDegree == 10 && toDegree == 0) {
            score += 10; // VII to i
        }
        return score;
    }

    /** The root's key near the reference, preferring below it, with no big leaps from the bass before. */
    static int bassPitch(int root, int reference, int previous) {
        int best = -1;
        int bestCost = Integer.MAX_VALUE;
        for (int pitch = root; pitch <= 127; pitch += 12) {
            int cost = Math.abs(pitch - reference) * 4;
            if (pitch > reference) {
                cost += 12;
            }
            if (previous >= 0) {
                int leap = Math.abs(pitch - previous);
                cost += leap * 3 + Math.max(0, leap - 7) * 10;
            }
            if (cost < bestCost) {
                bestCost = cost;
                best = pitch;
            }
        }
        return best;
    }

    /** What the chords become: the edits LandLogic carries out. */
    record Output(Set<Integer> removed, Map<Integer, Integer> newKeys, List<LandLogic.Copy> copies) {
        boolean isEmpty() {
            return removed.isEmpty() && newKeys.isEmpty() && copies.isEmpty();
        }
    }

    /**
     * replace: each chord becomes its bass note, the note that carried it moved to the bass's key and the others
     * removed. Otherwise the chords stay and the bass is added: a copy of the note that carries it, at the bass's key,
     * unless the chord already has a note on that key.
     */
    static Output output(List<Chord> chords, List<LandLogic.N> plain, boolean replace) {
        Set<Integer> removed = new HashSet<>();
        Map<Integer, Integer> newKeys = new java.util.HashMap<>();
        List<LandLogic.Copy> copies = new ArrayList<>();
        for (Chord chord : chords) {
            LandLogic.N keeper = plain.get(chord.keeper());
            if (replace) {
                removed.addAll(chord.others());
                if (chord.bassKey() != keeper.key()) {
                    newKeys.put(chord.keeper(), chord.bassKey());
                }
            } else if (!chord.keys().contains(chord.bassKey())) {
                copies.add(new LandLogic.Copy(chord.keeper(), chord.bassKey(), keeper.start(), keeper.end()));
            }
        }
        return new Output(removed, newKeys, copies);
    }
}
