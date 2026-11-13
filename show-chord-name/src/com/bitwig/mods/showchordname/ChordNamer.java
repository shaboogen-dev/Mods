package com.bitwig.mods.showchordname;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Names the chord that a set of keys makes, as a lead-sheet chord symbol: C, Cm, C7, Cmaj7, Cm7b5, F/C. Plain Java,
 * no Bitwig names.
 *
 * Only which pitch classes sound, and which is lowest, count; timing, octaves and doubled notes don't. The chord is
 * looked up by its interval set, so every inversion is found:
 * <ol>
 * <li>a complete chord on the lowest note (root position);</li>
 * <li>else a complete chord on another note, the most common one, with the lowest note after a slash (Am7/E);</li>
 * <li>else the same two steps for a chord whose perfect fifth is left out, a common voicing (C E Bb is C7).</li>
 * </ol>
 * One pitch class shows the note name for octaves and "-" for a single note; two show the interval from the lower.
 * Anything else that isn't a known chord is "-".
 */
public final class ChordNamer {
    /** What every case that isn't a chord shows. */
    public static final String NONE = "-";

    private static final String[] INTERVALS = {
            "Minor 2nd", "Major 2nd", "Minor 3rd", "Major 3rd", "Perfect 4th", "Tritone",
            "Perfect 5th", "Minor 6th", "Major 6th", "Minor 7th", "Major 7th"};

    private record Quality(String suffix, int rank) {
    }

    /** Interval set (sorted, comma-separated) to quality; rank: lower is more common. */
    private static final Map<String, Quality> FULL = new HashMap<>();
    private static final Map<String, Quality> NO_FIFTH = new HashMap<>();

    static {
        String[][] table = {
                {"0,4,7", ""}, {"0,3,7", "m"}, {"0,4,7,10", "7"}, {"0,4,7,11", "maj7"}, {"0,3,7,10", "m7"},
                {"0,3,6", "dim"}, {"0,4,8", "aug"}, {"0,5,7", "sus4"}, {"0,2,7", "sus2"}, {"0,4,7,9", "6"},
                {"0,3,7,9", "m6"}, {"0,3,6,10", "m7b5"}, {"0,3,6,9", "dim7"}, {"0,2,4,7,10", "9"},
                {"0,2,4,7,11", "maj9"}, {"0,2,3,7,10", "m9"}, {"0,2,4,7", "add9"}, {"0,2,3,7", "m(add9)"},
                {"0,3,7,11", "m(maj7)"}, {"0,5,7,10", "7sus4"}, {"0,2,7,10", "7sus2"}, {"0,2,4,7,9", "6/9"},
                {"0,2,3,7,9", "m6/9"}, {"0,4,8,10", "7#5"}, {"0,4,8,11", "maj7#5"}, {"0,4,6,10", "7b5"},
                {"0,2,5,7,10", "9sus4"}, {"0,2,4,5,7,10", "11"}, {"0,2,3,5,7,10", "m11"},
                {"0,2,4,7,9,10", "13"}, {"0,2,4,7,9,11", "maj13"}, {"0,2,3,7,9,10", "m13"},
                {"0,1,4,7,10", "7b9"}, {"0,3,4,7,10", "7#9"}, {"0,4,6,7,10", "7#11"}, {"0,4,6,7,11", "maj7#11"},
                {"0,2,4,6,7,11", "maj9#11"}, {"0,4,5,7", "add11"}, {"0,3,5,7", "m(add11)"},
                {"0,4,7,8,10", "7b13"}, {"0,1,4,7,8,10", "7b9b13"}};
        for (int rank = 0; rank < table.length; rank++) {
            FULL.put(table[rank][0], new Quality(table[rank][1], rank));
        }
        // The same chords with the perfect fifth left out, where that leaves three notes or more and isn't
        // another chord already. Ranked after every complete chord.
        for (int rank = 0; rank < table.length; rank++) {
            List<Integer> intervals = new ArrayList<>();
            for (String part : table[rank][0].split(",")) {
                intervals.add(Integer.parseInt(part));
            }
            if (!intervals.remove(Integer.valueOf(7)) || intervals.size() < 3) {
                continue;
            }
            String key = join(intervals);
            if (!FULL.containsKey(key) && !NO_FIFTH.containsKey(key)) {
                NO_FIFTH.put(key, new Quality(table[rank][1], rank));
            }
        }
    }

    private ChordNamer() {
    }

    /** The name of the chord these keys (0-127, any order, repeats allowed) make, spelled as {@code spelling} says. */
    public static String name(int[] keys, Spelling spelling) {
        if (keys == null || keys.length == 0) {
            return NONE;
        }
        int lowest = Integer.MAX_VALUE;
        TreeSet<Integer> classes = new TreeSet<>();
        for (int key : keys) {
            lowest = Math.min(lowest, key);
            classes.add(Math.floorMod(key, 12));
        }
        int bass = Math.floorMod(lowest, 12);
        if (classes.size() == 1) {
            return keys.length == 1 ? NONE : spelling.name(bass) + " octaves";
        }
        if (classes.size() == 2) {
            int other = classes.first() == bass ? classes.last() : classes.first();
            return INTERVALS[Math.floorMod(other - bass, 12) - 1];
        }
        String named = find(FULL, classes, bass, spelling);
        if (named == null) {
            named = find(NO_FIFTH, classes, bass, spelling);
        }
        return named == null ? NONE : named;
    }

    /** Root position if the table has it, else the most common chord on another note, over the bass. */
    private static String find(Map<String, Quality> table, TreeSet<Integer> classes, int bass, Spelling spelling) {
        Quality onBass = table.get(intervalsFrom(classes, bass));
        if (onBass != null) {
            return spelling.name(bass) + onBass.suffix();
        }
        Quality best = null;
        int bestRoot = -1;
        for (int root : classes) {
            Quality quality = table.get(intervalsFrom(classes, root));
            if (quality != null && (best == null || quality.rank() < best.rank())) {
                best = quality;
                bestRoot = root;
            }
        }
        return best == null ? null : spelling.name(bestRoot) + best.suffix() + "/" + spelling.name(bass);
    }

    private static String intervalsFrom(TreeSet<Integer> classes, int root) {
        List<Integer> intervals = new ArrayList<>();
        for (int pitchClass : classes) {
            intervals.add(Math.floorMod(pitchClass - root, 12));
        }
        intervals.sort(null);
        return join(intervals);
    }

    private static String join(List<Integer> intervals) {
        StringBuilder text = new StringBuilder();
        for (int interval : intervals) {
            if (text.length() > 0) {
                text.append(',');
            }
            text.append(interval);
        }
        return text.toString();
    }
}
