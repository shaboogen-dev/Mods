package com.bitwig.mods.miditools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Voice Lead Chords' logic, in plain values. It re-voices the selected chords: every note keeps its pitch class and
 * its timing and only changes octave, so that each chord moves as little as it can from the one before. It never adds
 * or removes a note.
 */
final class VoiceLeadLogic {
    /** What happens to each chord's lowest note. */
    enum Bass {
        FREE("Free"), KEEP("Keep Lowest"), ROOT("Root"), OCTAVE_DOWN("Lowest -1 Oct");

        final String label;

        Bass(String label) {
            this.label = label;
        }
    }

    /** Octave-only layouts after the leading. */
    enum Voicing {
        KEEP("Keep"), CLOSE("Close"), OPEN("Open"), DROP_2("Drop 2"), DROP_3("Drop 3"), DROP_4("Drop 4"),
        DROP_2_3("Drop 2 & 3"), DROP_2_4("Drop 2 & 4");

        final String label;

        Voicing(String label) {
            this.label = label;
        }
    }

    /** A note: its clip (group), channel, key and times. */
    record In(int index, int group, int channel, int key, double start, double end) {
    }

    /** Each note's new key (by index), and how many chords there were. */
    record Result(int[] keys, int chords) {
    }

    /** Notes of one channel starting within this many beats of each other are one chord. */
    static final double ONSET_TOLERANCE = 0.02;
    private static final double EPSILON = 1.0e-6;
    static final int MAX_VOICES = 10;
    static final int MAX_NOTES = 2048;

    private VoiceLeadLogic() {
    }

    /**
     * smooth: each chord as close as it can be to the one before (otherwise each chord only follows the bass,
     * inversion and voicing options). inversion: null keeps it; otherwise which of the chord's notes, counted from
     * the lowest, goes to the bass, -3 to 3. Refused (IllegalArgumentException) where the options can't be met.
     */
    static Result plan(List<In> selected, boolean smooth, Bass bass, Integer inversion, Voicing voicing) {
        int[] keys = new int[selected.size()];
        for (In note : selected) {
            keys[note.index()] = note.key();
        }
        if (selected.isEmpty()) {
            return new Result(keys, 0);
        }
        if (selected.size() > MAX_NOTES) {
            throw new IllegalArgumentException("select at most " + MAX_NOTES + " notes");
        }
        Map<Integer, List<In>> groups = new TreeMap<>();
        for (In note : selected) {
            groups.computeIfAbsent(note.group(), g -> new ArrayList<>()).add(note);
        }
        Map<In, Integer> targets = new IdentityHashMap<>();
        int chords = 0;
        for (List<In> group : groups.values()) {
            List<In> ordered = group.stream().sorted(Comparator.comparingInt(In::channel)
                    .thenComparingDouble(In::start).thenComparingInt(In::key)).toList();
            int[] previous = null;
            int previousChannel = -1;
            for (int index = 0; index < ordered.size();) {
                In first = ordered.get(index);
                if (first.channel() != previousChannel) {
                    previous = null;
                }
                double time = first.start();
                List<In> fresh = new ArrayList<>();
                do {
                    fresh.add(ordered.get(index++));
                } while (index < ordered.size() && ordered.get(index).channel() == first.channel()
                        && ordered.get(index).start() - time <= ONSET_TOLERANCE
                        && ordered.get(index).start() < first.end() - EPSILON);
                // Notes still sounding from an earlier chord keep the key they were given: a held note can't change.
                List<In> sounding = sounding(ordered, fresh, first.channel(), time);
                sounding.sort(Comparator.comparingInt(n -> targets.getOrDefault(n, n.key())));
                if (sounding.stream().mapToInt(n -> n.key() % 12).distinct().count() < 2) {
                    for (In note : fresh) {
                        targets.put(note, note.key());
                    }
                    previousChannel = first.channel();
                    continue;
                }
                if (sounding.size() > MAX_VOICES) {
                    throw new IllegalArgumentException("a chord can have at most " + MAX_VOICES + " notes sounding at once");
                }
                int[] pitches = sounding.stream().mapToInt(n -> targets.getOrDefault(n, n.key())).toArray();
                int bassClass = -1;
                int fixedBass = -1;
                if (inversion != null) {
                    bassClass = Math.floorMod(pitches[Math.floorMod(inversion, pitches.length)], 12);
                }
                if (bass == Bass.KEEP) {
                    bassClass = pitches[0] % 12;
                    fixedBass = pitches[0];
                }
                if (bass == Bass.ROOT) {
                    bassClass = rootPitchClass(pitches);
                    if (bassClass < 0) { // no chord it knows: its lowest note stays
                        bassClass = pitches[0] % 12;
                        fixedBass = pitches[0];
                    }
                }
                int[] voiced = pitches;
                if (smooth || bass == Bass.KEEP || bass == Bass.ROOT || inversion != null) {
                    int[] reference = smooth && previous != null ? previous : pitches;
                    boolean[] held = new boolean[sounding.size()];
                    for (int i = 0; i < held.length; i++) {
                        held[i] = !fresh.contains(sounding.get(i));
                    }
                    voiced = new Search(pitches, reference, held, bassClass, fixedBass).solve();
                }
                for (int i = 0; i < sounding.size(); i++) {
                    targets.put(sounding.get(i), voiced[i]);
                }
                previous = Arrays.stream(voiced).sorted().toArray();
                previousChannel = first.channel();
                chords++;
            }
        }
        arrange(groups, targets, voicing, bass);
        for (In note : selected) {
            keys[note.index()] = targets.getOrDefault(note, note.key());
        }
        return new Result(keys, chords);
    }

    private static List<In> sounding(List<In> ordered, List<In> fresh, int channel, double time) {
        List<In> sounding = new ArrayList<>();
        for (In note : ordered) {
            if (note.channel() == channel && note.start() < time - EPSILON && note.end() > time + EPSILON && !fresh.contains(note)) {
                sounding.add(note);
            }
        }
        sounding.addAll(fresh);
        return sounding;
    }

    /** The voicing after the leading, then the bass an octave down after every chord is laid out. */
    private static void arrange(Map<Integer, List<In>> groups, Map<In, Integer> targets, Voicing voicing, Bass bass) {
        if (voicing == Voicing.KEEP && bass != Bass.OCTAVE_DOWN) {
            return;
        }
        Set<In> lowered = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (List<In> group : groups.values()) {
            List<In> ordered = group.stream().sorted(Comparator.comparingInt(In::channel)
                    .thenComparingDouble(In::start).thenComparingInt(In::key)).toList();
            for (int index = 0; index < ordered.size();) {
                In first = ordered.get(index);
                double time = first.start();
                List<In> fresh = new ArrayList<>();
                do {
                    fresh.add(ordered.get(index++));
                } while (index < ordered.size() && ordered.get(index).channel() == first.channel()
                        && ordered.get(index).start() - time <= ONSET_TOLERANCE
                        && ordered.get(index).start() < first.end() - EPSILON);
                List<In> sounding = sounding(ordered, fresh, first.channel(), time);
                if (sounding.stream().mapToInt(n -> n.key() % 12).distinct().count() < 2) {
                    continue;
                }
                int[] pitches = sounding.stream().mapToInt(n -> targets.getOrDefault(n, n.key())).toArray();
                int minimum = Arrays.stream(pitches).min().orElseThrow();
                boolean[] fixed = new boolean[pitches.length];
                for (int i = 0; i < fixed.length; i++) {
                    fixed[i] = !fresh.contains(sounding.get(i)) || (bass == Bass.KEEP && pitches[i] == minimum);
                }
                int[] voiced = layout(pitches, fixed, voicing);
                int lowest = 0;
                for (int i = 0; i < voiced.length; i++) {
                    targets.put(sounding.get(i), voiced[i]);
                    if (voiced[i] < voiced[lowest]) {
                        lowest = i;
                    }
                }
                if (bass == Bass.OCTAVE_DOWN && fresh.contains(sounding.get(lowest))) {
                    lowered.add(sounding.get(lowest));
                }
            }
        }
        for (In note : lowered) {
            int pitch = targets.get(note);
            if (pitch < 12) {
                throw new IllegalArgumentException("the lowest note can't go an octave lower");
            }
            targets.put(note, pitch - 12);
        }
    }

    /** The bass kept; the other pitch classes packed above it, then the drop voices an octave down. */
    static int[] layout(int[] pitches, boolean[] fixed, Voicing mode) {
        int[] result = pitches.clone();
        if (mode == Voicing.KEEP) {
            return result;
        }
        int required = switch (mode) {
            case DROP_2, DROP_3, DROP_2_3, OPEN -> 3;
            case DROP_4, DROP_2_4 -> 4;
            default -> 2;
        };
        if (pitches.length < required) {
            throw new IllegalArgumentException(mode.label + " needs chords of at least " + required + " notes");
        }
        Integer[] order = new Integer[pitches.length];
        Set<Integer> classes = new HashSet<>();
        for (int i = 0; i < pitches.length; i++) {
            if (!classes.add(pitches[i] % 12)) {
                throw new IllegalArgumentException(mode.label + " needs chords without octave doublings");
            }
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingInt(i -> pitches[i]));
        int bottom = pitches[order[0]];
        for (int i = 0; i < pitches.length; i++) {
            result[i] = bottom + Math.floorMod(pitches[i] - bottom, 12);
        }
        Arrays.sort(order, Comparator.comparingInt(i -> result[i]));
        for (int rank = 1; rank <= order.length; rank++) { // the highest voice is 1
            boolean drop = switch (mode) {
                case DROP_2 -> rank == 2;
                case DROP_3 -> rank == 3;
                case DROP_4 -> rank == 4;
                case DROP_2_3 -> rank == 2 || rank == 3;
                case DROP_2_4 -> rank == 2 || rank == 4;
                case OPEN -> rank % 2 == 0;
                default -> false;
            };
            if (drop) {
                result[order[order.length - rank]] -= 12;
            }
        }
        for (int i = 0; i < result.length; i++) {
            if (result[i] < 0 || result[i] > 127) {
                throw new IllegalArgumentException(mode.label + " goes outside the MIDI range");
            }
            if (fixed[i] && result[i] != pitches[i]) {
                throw new IllegalArgumentException(mode.label + " would move a held note or the kept lowest note");
            }
        }
        return result;
    }

    // The chord's root, as the original's ChordIdentifier finds it: chord types by interval set, commonest first.
    private static final Map<String, Integer> QUALITIES = new java.util.HashMap<>();

    static {
        String[] sets = {"0,4,7", "0,3,7", "0,4,7,10", "0,4,7,11", "0,3,7,10", "0,3,6", "0,4,8", "0,5,7", "0,2,7", "0,4,7,9",
            "0,3,7,9", "0,3,6,10", "0,3,6,9", "0,2,4,7,10", "0,2,4,7,11", "0,2,3,7,10", "0,2,4,7", "0,2,3,7", "0,3,7,11",
            "0,5,7,10", "0,2,4,7,9", "0,4,8,10", "0,4,6,10", "0,2,4,5,7,10", "0,2,3,5,7,10", "0,2,4,7,9,10", "0,2,4,7,9,11",
            "0,2,3,7,9,10", "0,1,4,7,10", "0,3,4,7,10", "0,4,6,7,10", "0,2,4,6,7,11", "0,1,3,5", "0,4,5,7", "0,2,3"};
        for (int rank = 0; rank < sets.length; rank++) {
            QUALITIES.put(sets[rank], rank);
        }
    }

    /** The chord's root as a pitch class, the lowest note's when it can be; -1 when no chord type matches. */
    static int rootPitchClass(int[] pitches) {
        if (pitches.length < 2) {
            return -1;
        }
        TreeSet<Integer> classes = new TreeSet<>();
        for (int pitch : pitches) {
            classes.add(Math.floorMod(pitch, 12));
        }
        if (classes.size() < 3) {
            return -1;
        }
        int bass = Math.floorMod(Arrays.stream(pitches).min().orElseThrow(), 12);
        if (QUALITIES.get(intervals(classes, bass)) != null) {
            return bass;
        }
        int best = -1;
        int rank = Integer.MAX_VALUE;
        for (int root : classes) {
            Integer quality = QUALITIES.get(intervals(classes, root));
            if (quality != null && quality < rank) {
                best = root;
                rank = quality;
            }
        }
        return best;
    }

    private static String intervals(TreeSet<Integer> classes, int root) {
        return classes.stream().map(c -> Math.floorMod(c - root, 12)).sorted()
                      .map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
    }

    /** The lowest-cost voicing, voice by voice from the bass up, with held notes fixed. */
    private static final class Search {
        private static final long IMPOSSIBLE = Long.MAX_VALUE / 4;
        final int[] sources;
        final int[] reference;
        final int[] base;
        final int[][] options;
        final int bassClass;
        final int fixedBass;
        final int full;
        final long[][] costs;
        final int[][] chosenNote;
        final int[][] chosenPitch;

        Search(int[] sources, int[] reference, boolean[] held, int bassClass, int fixedBass) {
            this.sources = sources;
            this.reference = reference.clone();
            Arrays.sort(this.reference);
            base = sources.clone();
            Arrays.sort(base);
            this.bassClass = bassClass;
            this.fixedBass = fixedBass;
            full = (1 << sources.length) - 1;
            options = new int[sources.length][];
            int low = Math.max(0, Math.min(base[0], this.reference[0]) - 12);
            int high = Math.min(127, Math.max(base[base.length - 1], this.reference[this.reference.length - 1]) + 12);
            for (int i = 0; i < sources.length; i++) {
                if (held[i]) {
                    options[i] = new int[] {sources[i]};
                    continue;
                }
                List<Integer> candidates = new ArrayList<>();
                for (int pitch = low; pitch <= high; pitch++) {
                    if (Math.floorMod(pitch - sources[i], 12) == 0) {
                        candidates.add(pitch);
                    }
                }
                options[i] = candidates.stream().mapToInt(Integer::intValue).toArray();
            }
            costs = new long[full + 1][129];
            chosenNote = new int[full + 1][129];
            chosenPitch = new int[full + 1][129];
            for (long[] row : costs) {
                Arrays.fill(row, -1L);
            }
        }

        int[] solve() {
            if (cost(0, -1) >= IMPOSSIBLE) {
                throw new IllegalArgumentException("that voicing would move a held note or leave the MIDI range");
            }
            int[] result = new int[sources.length];
            int mask = 0;
            int last = -1;
            while (mask != full) {
                int note = chosenNote[mask][last + 1];
                int pitch = chosenPitch[mask][last + 1];
                result[note] = pitch;
                mask |= 1 << note;
                last = pitch;
            }
            return result;
        }

        long cost(int mask, int last) {
            if (mask == full) {
                return 0;
            }
            if (costs[mask][last + 1] >= 0) {
                return costs[mask][last + 1];
            }
            long best = IMPOSSIBLE;
            int position = Integer.bitCount(mask);
            int refIndex = sources.length == 1 ? 0 : (int) Math.round((double) position * (reference.length - 1) / (sources.length - 1));
            for (int note = 0; note < sources.length; note++) {
                if ((mask & (1 << note)) != 0) {
                    continue;
                }
                for (int pitch : options[note]) {
                    if (pitch <= last) {
                        continue;
                    }
                    if (position == 0 && ((bassClass >= 0 && pitch % 12 != bassClass) || (fixedBass >= 0 && pitch != fixedBass))) {
                        continue;
                    }
                    long tail = cost(mask | (1 << note), pitch);
                    if (tail >= IMPOSSIBLE) {
                        continue;
                    }
                    long movement = Math.abs(pitch - reference[refIndex]);
                    long spacing = position == 0 ? 0 : Math.abs((pitch - last) - (base[position] - base[position - 1]));
                    long register = Math.abs(pitch - sources[note]);
                    long candidate = movement * 1_000_000L + spacing * 1_000L + register + tail;
                    if (candidate < best) {
                        best = candidate;
                        chosenNote[mask][last + 1] = note;
                        chosenPitch[mask][last + 1] = pitch;
                    }
                }
            }
            costs[mask][last + 1] = best;
            return best;
        }
    }
}
