import com.bitwig.mods.showchordname.ChordNamer;
import com.bitwig.mods.showchordname.Spelling;

/** Run: javac -d out src/.../ChordNamer.java src/.../Spelling.java test/ChordNamerTest.java; java -cp out ChordNamerTest */
public final class ChordNamerTest {
    private static int failures;
    private static int cases;

    public static void main(String[] args) {
        Spelling c = Spelling.COMMON;
        // Nothing, one note, octaves, intervals.
        is("-", ChordNamer.name(new int[] {}, c));
        is("-", ChordNamer.name(new int[] {60}, c));
        is("C octaves", ChordNamer.name(new int[] {48, 60, 72}, c));
        is("Minor 3rd", ChordNamer.name(new int[] {60, 63}, c));
        is("Major 6th", ChordNamer.name(new int[] {63, 72}, c));
        is("Perfect 5th", ChordNamer.name(new int[] {48, 67}, c)); // compound: a 12th reads as a 5th
        is("Tritone", ChordNamer.name(new int[] {60, 66}, c));
        // Triads and sevenths, root position.
        is("C", ChordNamer.name(new int[] {60, 64, 67}, c));
        is("Cm", ChordNamer.name(new int[] {60, 63, 67}, c));
        is("Cdim", ChordNamer.name(new int[] {60, 63, 66}, c));
        is("Caug", ChordNamer.name(new int[] {60, 64, 68}, c));
        is("Csus4", ChordNamer.name(new int[] {60, 65, 67}, c));
        is("Csus2", ChordNamer.name(new int[] {60, 62, 67}, c));
        is("C7", ChordNamer.name(new int[] {60, 64, 67, 70}, c));
        is("Cmaj7", ChordNamer.name(new int[] {60, 64, 67, 71}, c));
        is("Cm7", ChordNamer.name(new int[] {60, 63, 67, 70}, c));
        is("Cm7b5", ChordNamer.name(new int[] {60, 63, 66, 70}, c));
        is("Cdim7", ChordNamer.name(new int[] {60, 63, 66, 69}, c));
        is("C6", ChordNamer.name(new int[] {60, 64, 67, 69}, c));
        is("Cmaj9", ChordNamer.name(new int[] {60, 62, 64, 67, 71}, c));
        is("C9", ChordNamer.name(new int[] {48, 64, 67, 70, 74}, c));
        is("C13", ChordNamer.name(new int[] {48, 62, 64, 67, 69, 70}, c));
        is("C7#11", ChordNamer.name(new int[] {60, 64, 66, 67, 70}, c));
        // Inversions: slash chords, the lowest note after the slash.
        is("C/E", ChordNamer.name(new int[] {64, 67, 72}, c));
        is("F/C", ChordNamer.name(new int[] {60, 65, 69}, c));
        is("Am7/E", ChordNamer.name(new int[] {64, 67, 69, 72}, c));
        is("G7sus4/C", ChordNamer.name(new int[] {60, 62, 65, 67}, c));
        is("G7/F", ChordNamer.name(new int[] {53, 59, 62, 67}, c));
        // Order, doubling and octaves don't matter, only the lowest note.
        is("C6", ChordNamer.name(new int[] {69, 60, 64, 67, 76, 60}, c));
        // No fifth: a complete chord elsewhere comes first.
        is("C7", ChordNamer.name(new int[] {48, 64, 70}, c));
        is("Cmaj7", ChordNamer.name(new int[] {48, 64, 71}, c));
        is("Cm7", ChordNamer.name(new int[] {48, 63, 70}, c));
        is("C9", ChordNamer.name(new int[] {48, 62, 64, 70}, c));
        is("Am/C", ChordNamer.name(new int[] {60, 64, 69}, c));
        is("C7/E", ChordNamer.name(new int[] {52, 60, 70}, c));
        // Not a chord.
        is("-", ChordNamer.name(new int[] {60, 61, 62, 63}, c));
        // Spelling.
        is("Bb7", ChordNamer.name(new int[] {58, 62, 65, 68}, c));
        is("A#7", ChordNamer.name(new int[] {58, 62, 65, 68}, Spelling.SHARPS));
        is("F#m", ChordNamer.name(new int[] {54, 57, 61}, c));
        is("Gbm", ChordNamer.name(new int[] {54, 57, 61}, Spelling.FLATS));
        is("Ebmaj9#11/D", ChordNamer.name(new int[] {62, 63, 65, 67, 69, 70}, c));
        // Key signatures.
        is(Spelling.FLATS, Spelling.forKey(5, scale(0, 2, 4, 5, 7, 9, 11))); // F major
        is(Spelling.FLATS, Spelling.forKey(2, scale(0, 2, 3, 5, 7, 8, 10))); // D minor: F major's notes
        is(Spelling.FLATS, Spelling.forKey(7, scale(0, 2, 3, 5, 7, 9, 10))); // G Dorian: F major's notes
        is(Spelling.SHARPS, Spelling.forKey(4, scale(0, 2, 3, 5, 7, 8, 10))); // E minor: G major's notes
        is(Spelling.SHARPS, Spelling.forKey(9, scale(0, 2, 4, 5, 7, 9, 11))); // A major
        is(Spelling.COMMON, Spelling.forKey(9, scale(0, 2, 3, 5, 7, 8, 10))); // A minor: C major's notes
        is(Spelling.COMMON, Spelling.forKey(0, scale(0, 2, 4, 5, 7, 9, 11))); // C major
        is(Spelling.FLATS, Spelling.forKey(0, scale(0, 2, 3, 5, 7, 8, 11))); // C harmonic minor: as Eb
        is(Spelling.SHARPS, Spelling.forKey(4, scale(0, 2, 3, 5, 7, 8, 11))); // E harmonic minor: as G
        is(Spelling.FLATS, Spelling.forKey(10, scale(0, 2, 4, 7, 9))); // Bb major pentatonic
        is(Spelling.COMMON, Spelling.forKey(9, scale(0, 3, 5, 7, 10))); // A minor pentatonic: as C
        is(Spelling.SHARPS, Spelling.forKey(4, scale(0, 3, 5, 7, 10))); // E minor pentatonic: as G
        System.out.println(failures == 0 ? "ChordNamerTest OK " + cases + " cases" : "FAILED " + failures + " of " + cases);
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static boolean[] scale(int... intervals) {
        boolean[] in = new boolean[12];
        for (int i : intervals) {
            in[i] = true;
        }
        return in;
    }

    private static void is(Object expected, Object actual) {
        cases++;
        if (!expected.equals(actual)) {
            failures++;
            System.out.println("expected " + expected + ", got " + actual);
        }
    }
}
