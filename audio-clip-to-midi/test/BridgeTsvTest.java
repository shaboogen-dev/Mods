import com.bitwig.mods.audioanalysis.Insert;
import com.bitwig.mods.audiotomidi.BridgeTsv;
import com.bitwig.mods.audiotomidi.Target;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Offline checks of Audio to MIDI's result parsing: a sample note file as the engine writes it
 * (ADNA 3, every target; and the older single-target ADNA 1), invalid rows, and the move of
 * the notes from the audio's seconds onto the clip's beats.
 */
public final class BridgeTsvTest {
    static int checks;

    static final String SAMPLE = String.join("\n",
        "ADNA\t3",
        "META\tbpm\t120.000000",
        "META\tduration\t4.000000",
        "META\tbpm_confidence\t0.900000",
        "META\tkey\tA minor",
        "META\tkey_confidence\t0.700000",
        "NOTE\tmelody\t0.000000\t0.500000\t69\t100",
        "NOTE\tmelody\t0.500000\t1.000000\t72\t90",
        "NOTE\tmelody\t1.000000\t0.900000\t74\t90",   // ends before it starts: skipped
        "NOTE\tmelody\t1.000000\t1.500000\t128\t90",  // key out of range: skipped
        "NOTE\tmelody\t1.000000\t1.500000\t60\t0",    // silent: skipped
        "NOTE\tchords\t0.000000\t2.000000\t57\t80",
        "NOTE\tchords\t0.000000\t2.000000\t60\t80",
        "NOTE\tchords\t0.000000\t2.000000\t64\t80",
        "NOTE\tgroove\t0.000000\t0.100000\t36\t127",
        "NOTE\tgroove\t1.000000\t1.100000\t38\t110",
        "NOTE\tgroove\t3.900000\t4.500000\t42\t60",   // runs past the end: cut at the end
        "NOTE\tgroove\tx\t1\t42\t60",                 // not a number: skipped
        "") + "\n";

    public static void main(String[] args) throws Exception {
        List<String> lines = List.of(SAMPLE.split("\n"));
        BridgeTsv.Analysis melody = BridgeTsv.parse(lines, Target.MELODY);
        eq(melody.duration(), 4.0);
        eq(melody.notes().size(), 2);
        eq(melody.notes().get(1), new BridgeTsv.Note(0.5, 1.0, 72, 90));
        eq(BridgeTsv.parse(lines, Target.HARMONY).notes().size(), 3);
        eq(BridgeTsv.parse(lines, Target.DRUMS).notes().size(), 3);

        // the clip is 8 beats long (4 s at 120 BPM): 2 beats per second
        List<Insert.Note> beats = BridgeTsv.toBeats(melody, 8.0);
        eq(beats.size(), 2);
        eq(beats.get(0), new Insert.Note(0.0, 1.0, 69, 100 / 127.0));
        eq(beats.get(1), new Insert.Note(1.0, 1.0, 72, 90 / 127.0));
        List<Insert.Note> drums = BridgeTsv.toBeats(BridgeTsv.parse(lines, Target.DRUMS), 8.0);
        eq(drums.get(2).start(), 7.8);
        near(drums.get(2).length(), 0.2);
        // a clip twice as long stretches the notes with it
        eq(BridgeTsv.toBeats(melody, 16.0).get(1), new Insert.Note(2.0, 2.0, 72, 90 / 127.0));
        eq(BridgeTsv.toBeats(melody, 0).size(), 0);
        eq(BridgeTsv.toBeats(null, 8).size(), 0);

        // version 1: one target, no mode column; no duration: the last note's end
        List<String> v1 = List.of("ADNA\t1", "META\tmode\tmelody", "NOTE\t0.0\t1.0\t60\t64", "NOTE\t1.0\t2.0\t62\t64");
        BridgeTsv.Analysis one = BridgeTsv.parse(v1, Target.MELODY);
        eq(one.notes().size(), 2);
        eq(one.duration(), 0.0);
        eq(BridgeTsv.toBeats(one, 4.0).get(1), new Insert.Note(2.0, 2.0, 62, 64 / 127.0));

        eq(BridgeTsv.parse(List.of("NOT\tADNA"), Target.MELODY).notes().size(), 0);
        eq(BridgeTsv.parse(List.of(), Target.MELODY).notes().size(), 0);

        Path dir = Files.createTempDirectory("tsv");
        Path tsv = dir.resolve("k.s0.15.tsv");
        Files.writeString(tsv, SAMPLE);
        yes(BridgeTsv.complete(tsv));
        eq(BridgeTsv.read(tsv, Target.HARMONY).notes().size(), 3);
        Files.writeString(tsv, String.join("\n", v1));
        yes(!BridgeTsv.complete(tsv));
        yes(!BridgeTsv.complete(dir.resolve("missing.tsv")));
        eq(BridgeTsv.read(dir.resolve("missing.tsv"), Target.MELODY).notes().size(), 0);

        eq(BridgeTsv.mode(Target.HARMONY), "chords");
        eq(BridgeTsv.mode(Target.DRUMS), "groove");
        System.out.println("BridgeTsvTest OK (" + checks + " checks)");
    }

    static void near(double a, double b) {
        checks++;
        if (Math.abs(a - b) > 1e-9) throw new AssertionError("check " + checks + ": " + a + " != " + b);
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
