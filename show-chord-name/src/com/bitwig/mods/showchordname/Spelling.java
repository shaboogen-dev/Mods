package com.bitwig.mods.showchordname;

/**
 * How note names are spelled: sharps, flats, or the names chord charts use most when no key says otherwise (Db, Eb,
 * F#, Ab, Bb). Plain Java, no Bitwig names.
 */
public enum Spelling {
    SHARPS("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"),
    FLATS("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"),
    COMMON("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B");

    private final String[] names;

    Spelling(String... names) {
        this.names = names;
    }

    /** A pitch class's name, 0 for C to 11 for B. */
    public String name(int pitchClass) {
        return names[Math.floorMod(pitchClass, 12)];
    }

    private static final int[] MAJOR = {0, 2, 4, 5, 7, 9, 11};

    /**
     * The spelling a key signature asks for. {@code inScale[i]} says whether the scale holds the note i semitones
     * above {@code root}. A scale with the major scale's notes (any mode: Dorian, Aeolian, ...) is spelled as that
     * major key: F, Bb, Eb, Ab and Db with flats, G, D, A, E, B and F# with sharps, C as {@link #COMMON}. Any other
     * scale is spelled as the major key of its root, or, when it has a minor third and no major third, as the
     * relative major of its root.
     */
    public static Spelling forKey(int root, boolean[] inScale) {
        return forMajorTonic(majorTonic(Math.floorMod(root, 12), inScale));
    }

    static int majorTonic(int root, boolean[] inScale) {
        int count = 0;
        for (boolean in : inScale) {
            count += in ? 1 : 0;
        }
        if (count == 7) {
            for (int tonic = 0; tonic < 12; tonic++) { // the major key, relative to the root, with these notes
                boolean same = true;
                for (int i = 0; i < 12 && same; i++) {
                    same = inScale[i] == isMajor(Math.floorMod(i - tonic, 12));
                }
                if (same) {
                    return Math.floorMod(root + tonic, 12);
                }
            }
        }
        boolean minorThird = inScale[3] && !inScale[4];
        return Math.floorMod(root + (minorThird ? 3 : 0), 12);
    }

    private static boolean isMajor(int interval) {
        for (int step : MAJOR) {
            if (step == interval) {
                return true;
            }
        }
        return false;
    }

    static Spelling forMajorTonic(int tonic) {
        switch (tonic) {
            case 5: case 10: case 3: case 8: case 1:
                return FLATS;
            case 7: case 2: case 9: case 4: case 11: case 6:
                return SHARPS;
            default:
                return COMMON;
        }
    }
}
