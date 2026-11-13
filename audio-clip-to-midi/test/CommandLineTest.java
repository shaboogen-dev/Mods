import com.bitwig.mods.audiotomidi.AudioToMidi;

import java.nio.file.Path;
import java.util.List;

/** Offline check of Audio to MIDI's engine command line: every output goes into the runtime's cache. */
public final class CommandLineTest {
    public static void main(String[] args) {
        Path cache = Path.of("C:\\h\\cache");
        Path tsv = cache.resolve("k.s0.15.tsv");
        List<String> plain = AudioToMidi.arguments(Path.of("C:\\Samples\\loop.wav"), cache, "k", "0.15", false, tsv);
        check(plain.equals(List.of("C:\\Samples\\loop.wav", "--mode", "both",
            "--midi", cache.resolve("k.s0.15.mid").toString(), "--json", cache.resolve("k.s0.15.json").toString(),
            "--bridge-tsv", tsv.toString(), "--sensitivity", "0.15", "--quiet")), plain.toString());
        List<String> studio = AudioToMidi.arguments(Path.of("C:\\Samples\\loop.wav"), cache, "k", "0.30", true, tsv);
        check(studio.get(3).equals("--studio"), studio.toString());
        check(studio.contains(cache.resolve("k.s0.30.studio.mid").toString()), studio.toString());
        // nothing is written next to the user's sample (the engine's default for --midi)
        for (String a : plain) check(!a.startsWith("C:\\Samples\\") || a.equals("C:\\Samples\\loop.wav"), a);
        check(AudioToMidi.sensitivity(15).equals("0.15"), AudioToMidi.sensitivity(15));
        check(AudioToMidi.sensitivity(5).equals("0.05"), AudioToMidi.sensitivity(5));
        check(AudioToMidi.sensitivity(50).equals("0.50"), AudioToMidi.sensitivity(50));
        System.out.println("CommandLineTest OK");
    }

    static void check(boolean b, String what) {
        if (!b) throw new AssertionError(what);
    }
}
