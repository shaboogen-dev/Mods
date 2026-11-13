import com.bitwig.mods.detectkey.KeyNames;

import java.util.List;

/**
 * Offline checks of Detect Key's parsing and naming: a sample of the estimator's output (with
 * library warnings and KEYINFO lines), every key's short name and relative key, and the clip
 * name suffix (added, replaced, never stacked).
 */
public final class KeyNamesTest {
    static int checks;

    public static void main(String[] args) {
        List<String> output = List.of(
            "2026-11-08 10:00:00.000 W tensorflow/core/platform: some warning",
            "KEYINFO\ttranscription unavailable: no model",
            "KEY\tnot a key\t0.5",
            "KEY\tA minor\tnot-a-number",
            "KEYINFO\tengine C major, 12.5 s of notes, 4 chords, method notes",
            "KEY\tA minor\t0.725",
            "KEY\tC major\t0.9");
        KeyNames.Result r = KeyNames.parse(output);
        eq(r, new KeyNames.Result("A minor", 0.725));
        eq(KeyNames.percent(r.confidence()), "73 %");
        eq(KeyNames.parse(List.of("KEY\tC major\t1.5")), null);
        eq(KeyNames.parse(List.of("KEY\tC major\t0.5\r")), new KeyNames.Result("C major", 0.5));
        eq(KeyNames.parse(List.of()), null);
        eq(KeyNames.parse(null), null);

        eq(KeyNames.shortKey("Eb major"), "Ebmaj");
        eq(KeyNames.shortKey("C# minor"), "C#min");
        eq(KeyNames.shortKey("H minor"), null);
        eq(KeyNames.shortKey("C dorian"), null);

        eq(KeyNames.withKey("Chords", "Ebmaj"), "Chords (Ebmaj)");
        eq(KeyNames.withKey("Chords (Ebmaj)", "Cmin"), "Chords (Cmin)");
        eq(KeyNames.withKey("Chords (Ebmaj) ", "Cmin"), "Chords (Cmin)");
        eq(KeyNames.withKey("", "Amin"), "Amin");
        eq(KeyNames.withKey(null, "Amin"), "Amin");
        eq(KeyNames.withKey("Take (2)", "Amin"), "Take (2) (Amin)");

        eq(KeyNames.relative("C major"), "A minor");
        eq(KeyNames.relative("A minor"), "C major");
        eq(KeyNames.relative("Eb major"), "C minor");
        eq(KeyNames.relative("F# minor"), "A major");
        eq(KeyNames.relative("G# minor"), "B major");
        eq(KeyNames.relative("Db major"), "Bb minor");
        eq(KeyNames.relative("E major"), "C# minor");
        eq(KeyNames.relative("nothing"), null);
        // every key: the relative of the relative is the key itself
        for (String root : List.of("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")) {
            String major = root + " major";
            yes(KeyNames.shortKey(major) != null);
            eq(KeyNames.relative(KeyNames.relative(major)), major);
        }
        for (String root : List.of("C", "C#", "D", "Eb", "E", "F", "F#", "G", "G#", "A", "Bb", "B")) {
            String minor = root + " minor";
            eq(KeyNames.relative(KeyNames.relative(minor)), minor);
        }
        System.out.println("KeyNamesTest OK (" + checks + " checks)");
    }

    static void yes(boolean b) {
        checks++;
        if (!b) throw new AssertionError("expected true (check " + checks + ")");
    }

    static void eq(Object a, Object b) {
        checks++;
        if (a == null ? b != null : !a.equals(b)) throw new AssertionError("check " + checks + ": " + a + " != " + b);
    }
}
