package com.bitwig.mods.separatestems;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reading what the engine wrote: its stems manifest (the four WAVs) and its progress file. Pure
 * apart from file reads; tested offline with sample files.
 */
public final class StemManifest {
    /** The engine's four stems, in the order the tracks are added. */
    public static final List<String> ORDER = List.of("vocals", "drums", "bass", "other");
    private static final Pattern PHASE = Pattern.compile("\"phase\"\\s*:\\s*\"([^\"]+)\"");

    private StemManifest() { }

    /** The four stems' WAVs by name, in {@link #ORDER}, or null when the manifest is missing or incomplete. */
    public static Map<String, Path> read(Path manifest) {
        try {
            if (manifest == null || !Files.isRegularFile(manifest)) return null;
            return parse(Files.readString(manifest, StandardCharsets.UTF_8), true);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Parses the manifest's {@code "stems": [{"name": ..., "wav_path": ...}]}. With
     * {@code checkFiles}, every WAV must exist and not be empty. Null when any stem is missing.
     */
    public static Map<String, Path> parse(String json, boolean checkFiles) {
        if (json == null) return null;
        Map<String, Path> out = new LinkedHashMap<>();
        for (String name : ORDER) {
            Pattern entry = Pattern.compile("\"name\"\\s*:\\s*\"" + name + "\"\\s*,\\s*\"wav_path\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
            Matcher m = entry.matcher(json);
            if (!m.find()) return null;
            Path wav;
            try {
                wav = Path.of(unescape(m.group(1)));
            } catch (java.nio.file.InvalidPathException e) {
                return null;
            }
            if (checkFiles) {
                try {
                    if (!Files.isRegularFile(wav) || Files.size(wav) == 0) return null;
                } catch (IOException e) {
                    return null;
                }
            }
            out.put(name, wav);
        }
        return out;
    }

    /** The WAVs of the requested stems, in {@link #ORDER}. */
    public static List<Path> choose(Map<String, Path> wavs, Set<String> requested) {
        List<Path> out = new ArrayList<>();
        if (wavs == null || requested == null) return out;
        for (String name : ORDER) if (requested.contains(name) && wavs.containsKey(name)) out.add(wavs.get(name));
        return out;
    }

    /** The phase in the engine's progress file ("separating", "complete", "failed"), or "". */
    public static String phase(String progressJson) {
        if (progressJson == null) return "";
        Matcher m = PHASE.matcher(progressJson);
        return m.find() ? m.group(1) : "";
    }

    /** JSON string escapes the engine's json.dumps writes in paths (backslashes on Windows, \\uXXXX). */
    static String unescape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                b.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'n' -> b.append('\n');
                case 't' -> b.append('\t');
                case 'r' -> b.append('\r');
                case 'b' -> b.append('\b');
                case 'f' -> b.append('\f');
                case 'u' -> {
                    if (i + 4 < s.length()) {
                        b.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                }
                default -> b.append(n); // \\ \" \/
            }
        }
        return b.toString();
    }
}
