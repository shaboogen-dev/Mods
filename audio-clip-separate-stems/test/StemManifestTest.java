import com.bitwig.mods.separatestems.StemManifest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Offline checks of Separate Stems' result parsing: a manifest as the engine writes it (json.dumps
 * with sort_keys, Windows backslashes escaped, a non-ASCII folder), missing or empty stems, the
 * stem choice and order, and the progress file.
 */
public final class StemManifestTest {
    static int checks;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("stems Ä");
        StringBuilder stems = new StringBuilder();
        for (String name : List.of("bass", "drums", "other", "vocals")) { // the engine's file order is irrelevant
            Path wav = dir.resolve("htdemucs").resolve("track").resolve(name + ".wav");
            Files.createDirectories(wav.getParent());
            Files.write(wav, new byte[] {1, 2, 3});
        }
        for (String name : StemManifest.ORDER) {
            if (stems.length() > 0) stems.append(",\n");
            String path = dir.resolve("htdemucs").resolve("track").resolve(name + ".wav").toString().replace("\\", "\\\\");
            stems.append("    {\n      \"name\": \"").append(name).append("\",\n      \"wav_path\": \"").append(path).append("\"\n    }");
        }
        String json = "{\n  \"cache_key\": \"abc\",\n  \"manifest_version\": 1,\n  \"model\": \"htdemucs\",\n"
            + "  \"source\": {\"path\": \"C:\\\\a\\\\b.wav\"},\n  \"stems\": [\n" + stems + "\n  ]\n}\n";
        Path manifest = dir.resolve("k.stems.json");
        Files.writeString(manifest, json);

        Map<String, Path> wavs = StemManifest.read(manifest);
        yes(wavs != null);
        eq(List.copyOf(wavs.keySet()), StemManifest.ORDER);
        for (String name : StemManifest.ORDER) eq(wavs.get(name), dir.resolve("htdemucs").resolve("track").resolve(name + ".wav"));

        Set<String> some = new LinkedHashSet<>(List.of("other", "vocals")); // asked in another order
        eq(StemManifest.choose(wavs, some), List.of(wavs.get("vocals"), wavs.get("other")));
        eq(StemManifest.choose(wavs, Set.of()), List.of());
        eq(StemManifest.choose(null, some), List.of());

        // an empty stem file makes the manifest unusable (separate again)
        Files.write(wavs.get("bass"), new byte[0]);
        yes(StemManifest.read(manifest) == null);
        yes(StemManifest.parse(json, false) != null);
        // a missing stem entry
        yes(StemManifest.parse(json.replace("\"drums\"", "\"drumz\""), false) == null);
        yes(StemManifest.read(dir.resolve("missing.json")) == null);
        yes(StemManifest.parse(null, false) == null);

        // JSON escapes
        eq(StemManifest.parse("{\"name\": \"vocals\", \"wav_path\": \"\\/a\\/\\u00e9q.wav\"}"
            + "{\"name\": \"drums\", \"wav_path\": \"/d.wav\"}{\"name\": \"bass\", \"wav_path\": \"/b.wav\"}"
            + "{\"name\": \"other\", \"wav_path\": \"/o.wav\"}", false).get("vocals"), Path.of("/a/\u00e9q.wav"));
        // a path Windows can't hold: not usable, no exception
        yes(StemManifest.parse(json.replace("vocals.wav", "voc\\\"als.wav"), false) == null);

        eq(StemManifest.phase("{\"phase\":\"separating\"}\n"), "separating");
        eq(StemManifest.phase("{\"phase\": \"complete\"}"), "complete");
        eq(StemManifest.phase("garbage"), "");
        eq(StemManifest.phase(null), "");
        System.out.println("StemManifestTest OK (" + checks + " checks)");
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
