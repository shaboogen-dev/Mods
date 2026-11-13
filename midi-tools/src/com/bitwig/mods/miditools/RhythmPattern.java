package com.bitwig.mods.miditools;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A named one-bar, 16th-note rhythm and its optional per-hit accent metadata. */
record RhythmPattern(String family, String name, int mask, List<Integer> accents) {
    public static final int STEPS = 16;
    private static final List<RhythmPattern> CATALOG = buildCatalog();

    public RhythmPattern {
        if (family == null || family.isBlank() || name == null || name.isBlank()
                || (mask & ~0xffff) != 0 || mask == 0 || accents == null
                || accents.size() != STEPS) {
            throw new IllegalArgumentException("A rhythm needs a name, a nonempty 16-step mask and 16 accents");
        }
        accents = List.copyOf(accents);
        for (int step = 0; step < STEPS; step++) {
            int accent = accents.get(step);
            if (accent < 0 || accent > 127 || (!hitAt(mask, step) && accent != 0)) {
                throw new IllegalArgumentException("Accents must be MIDI-range values on active steps only");
            }
        }
    }

    /** Immutable catalog: eight musical families, each with eight unique masks. */
    public static List<RhythmPattern> catalog() {
        return CATALOG;
    }

    public static List<String> families() {
        return List.of("Pop", "House", "Funk", "Syncopated", "IDM", "Breaks",
            "Latin-inspired", "Ambient/Half Time");
    }

    public boolean hitAt(int step) {
        checkStep(step);
        return hitAt(mask, step);
    }

    public int accentAt(int step) {
        checkStep(step);
        return accents.get(step);
    }

    private static List<RhythmPattern> buildCatalog() {
        List<RhythmPattern> patterns = new ArrayList<>();
        add(patterns, "Pop", "Pulse Pickup", "x...x...x...x.x.", 110);
        add(patterns, "Pop", "Backbeat Lift", "x...x...x.x...x.", 108);
        add(patterns, "Pop", "Offbeat Hook", "x.x...x.x...x.x.", 106);
        add(patterns, "Pop", "Anthem Push", "x...x.x...x.x...", 112);
        add(patterns, "Pop", "Verse Skip", "x...x...x..x..x.", 104);
        add(patterns, "Pop", "Chorus Eight", "x.x.x.x.x.x.x.x.", 102);
        add(patterns, "Pop", "Pickup Pop", "x...x.x.x...x.x.", 110);
        add(patterns, "Pop", "Wide Clap", "x.....x.x.....x.", 108);

        add(patterns, "House", "Four-to-the-Floor", "x...x...x...x...", 112);
        add(patterns, "House", "Standard Offbeat", "..x...x...x...x.", 106);
        add(patterns, "House", "Piano Push", ".x..x..x..x..x..", 104);
        add(patterns, "House", "Garage Lift", "x..x...x.x..x...", 108);
        add(patterns, "House", "Open Hat Drive", "x.x.x.x.x.x.x...", 103);
        add(patterns, "House", "Fifth Pulse", "x...x...x.x.x...", 109);
        add(patterns, "House", "Sunrise Stab", "x...x..x.x...x..", 105);
        add(patterns, "House", "Filter Rise", "x...x.x.x.x...x.", 107);

        add(patterns, "Funk", "Pocket One", "x..x.x..x.x...x.", 109);
        add(patterns, "Funk", "Pocket Two", "x.x..x.x..x.x...", 106);
        add(patterns, "Funk", "Ghost Step", "x..xx...x.x..x..", 104);
        add(patterns, "Funk", "Clav Pull", "x.x...xx.x..x.x.", 107);
        add(patterns, "Funk", "Late Sixteenth", "x...x.xx..x.x...", 108);
        add(patterns, "Funk", "Double Down", "xx..x...x.xx..x.", 110);
        add(patterns, "Funk", "Turnaround", "x..x.x.x..x..xx.", 105);
        add(patterns, "Funk", "Loose Pocket", "x.x..x...x.xx.x.", 106);

        add(patterns, "Syncopated", "3-3-2", "x..x..x.x..x..x.", 110);
        add(patterns, "Syncopated", "Skank", ".x.x...x.x.x...x", 104);
        add(patterns, "Syncopated", "Push Pull", "x..x.x..x..x.x..", 108);
        add(patterns, "Syncopated", "Neon Stab", "x.x..x...x.x..x.", 106);
        add(patterns, "Syncopated", "Late Hook", "x...x..x.x..x...", 107);
        add(patterns, "Syncopated", "Double Pickup", "x..x...x..x.x.x.", 109);
        add(patterns, "Syncopated", "Broken Chorus", "x.x...x..x...x.x", 105);
        add(patterns, "Syncopated", "Tight Sync", "x..x.x.x..x.x...", 108);

        add(patterns, "IDM", "Microfracture", "x.x..xx...x.x.x.", 106);
        add(patterns, "IDM", "Glass Orbit", "x...xx.x..x..xx.", 104);
        add(patterns, "IDM", "Binary Dust", "xx..x...x.x..x.x", 105);
        add(patterns, "IDM", "Stutter Arc", "x.xx...x.x..xx..", 107);
        add(patterns, "IDM", "Lopsided Grid", "x..xx.x...xx.x..", 106);
        add(patterns, "IDM", "Pixel Current", "x.x.x..xx...x.x.", 104);
        add(patterns, "IDM", "Circuit Skip", "x...x.xx.x.x...x", 108);
        add(patterns, "IDM", "Broken Halo", "x.xx..x...x.xx..", 105);

        add(patterns, "Breaks", "Amen Lean", "x.x.x..x.x.x.x..", 108);
        add(patterns, "Breaks", "Rushed Snare", "x..x.x.x.x..x.x.", 106);
        add(patterns, "Breaks", "Jungle Space", "x...x.x.x.x...xx", 109);
        add(patterns, "Breaks", "Torn Funk", "x.x..x.x..xx.x..", 105);
        add(patterns, "Breaks", "Roller", "x..xx..x.x.x..x.", 107);
        add(patterns, "Breaks", "Dub Cut", "x...x.xx..x.x.x.", 106);
        add(patterns, "Breaks", "Cracked Beat", "xx.x...x..x.x.x.", 108);
        add(patterns, "Breaks", "Rim Scatter", "x..x.xx...x..x.x", 104);

        add(patterns, "Latin-inspired", "Clave Lift", "x..x..x...x.x...", 110);
        add(patterns, "Latin-inspired", "Bell Cycle", "x..x..x..x..x...", 108);
        add(patterns, "Latin-inspired", "Bossa-inspired Lift", "x...x.x...x..x..", 106);
        add(patterns, "Latin-inspired", "Cáscara-inspired", "x.x.x.x..x.x.x..", 105);
        add(patterns, "Latin-inspired", "Samba-inspired Spark", "x.x..x.x.x..x.x.", 107);
        add(patterns, "Latin-inspired", "Mambo-inspired Bell", "x..x.x.x..x.x.x.", 109);
        add(patterns, "Latin-inspired", "Afro-inspired Six", "x..x...x.x..x.x.", 108);
        add(patterns, "Latin-inspired", "Tumbao-inspired", "x...x..x..x.x...", 106);

        add(patterns, "Ambient/Half Time", "Heavy One", "x.......x.......", 112);
        add(patterns, "Ambient/Half Time", "Trailing One", "x.....x.x.......", 108);
        add(patterns, "Ambient/Half Time", "Slow Push", "x.......x...x...", 110);
        add(patterns, "Ambient/Half Time", "Wide Drift", "x....x..x....x..", 106);
        add(patterns, "Ambient/Half Time", "Hollow Backbeat", "x...x...x.......", 107);
        add(patterns, "Ambient/Half Time", "Sub Pulse", "x.......x.x.....", 109);
        add(patterns, "Ambient/Half Time", "Slow Answer", "x.....x...x.....", 105);
        add(patterns, "Ambient/Half Time", "Tail Light", "x.......x.....x.", 108);

        if (patterns.size() != 64 || new HashSet<>(patterns.stream().map(RhythmPattern::mask).toList()).size() != 64) {
            throw new IllegalStateException("Rhythm catalog must contain 64 unique masks");
        }
        return List.copyOf(patterns);
    }

    private static void add(List<RhythmPattern> patterns, String family, String name, String steps,
                            int baseAccent) {
        if (steps.length() != STEPS) throw new IllegalArgumentException("Rhythm strings need 16 steps");
        int mask = 0;
        List<Integer> accents = new ArrayList<>(STEPS);
        for (int step = 0; step < STEPS; step++) {
            char symbol = steps.charAt(step);
            if (symbol == 'x') {
                mask |= 1 << step;
                // Downbeats lead; the remaining hits alternate gently around the pattern's base energy.
                accents.add(Math.min(127, baseAccent + (step % 4 == 0 ? 8 : (step % 2 == 0 ? 2 : -4))));
            } else if (symbol == '.') accents.add(0);
            else throw new IllegalArgumentException("Use x or . for rhythm steps");
        }
        patterns.add(new RhythmPattern(family, name, mask, accents));
    }

    private static boolean hitAt(int mask, int step) {
        return (mask & (1 << step)) != 0;
    }

    private static void checkStep(int step) {
        if (step < 0 || step >= STEPS) throw new IllegalArgumentException("Step must be between 0 and 15");
    }
}
