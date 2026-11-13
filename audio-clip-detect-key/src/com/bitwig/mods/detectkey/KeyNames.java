package com.bitwig.mods.detectkey;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** The estimator's answer, key names and the clip name suffix. Pure; tested offline. */
public final class KeyNames {
    /** A key such as "A minor" and the estimator's confidence, 0-1. */
    public record Result(String name, double confidence) { }

    /** A suffix an earlier detection wrote, so a new one replaces it instead of stacking. */
    private static final Pattern SUFFIX = Pattern.compile("\\s*\\([A-G][#b]?(maj|min)\\)\\s*$");
    private static final List<String> SHARP = List.of("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B");
    private static final List<String> FLAT = List.of("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B");

    private KeyNames() { }

    /** The "KEY<tab>name<tab>confidence" line; library warnings and KEYINFO lines are ignored. */
    public static Result parse(List<String> lines) {
        if (lines == null) return null;
        for (String line : lines) {
            String[] f = line.strip().split("\t");
            if (f.length != 3 || !f[0].equals("KEY") || f[1].isBlank()) continue;
            try {
                double c = Double.parseDouble(f[2]);
                if (c >= 0 && c <= 1 && shortKey(f[1]) != null) return new Result(f[1].strip(), c);
            } catch (NumberFormatException ignored) {
                // not the result line
            }
        }
        return null;
    }

    /** "Eb major" is "Ebmaj", "C minor" is "Cmin"; null for anything else. */
    public static String shortKey(String keyName) {
        if (keyName == null) return null;
        String[] parts = keyName.strip().split("\\s+");
        if (parts.length != 2 || !parts[0].matches("[A-G][#b]?")) return null;
        return switch (parts[1]) {
            case "major" -> parts[0] + "maj";
            case "minor" -> parts[0] + "min";
            default -> null;
        };
    }

    /** "Chords" becomes "Chords (Ebmaj)"; an earlier suffix is replaced; an empty name is just the key. */
    public static String withKey(String name, String shortKey) {
        String base = SUFFIX.matcher(name == null ? "" : name).replaceFirst("").strip();
        return base.isEmpty() ? shortKey : base + " (" + shortKey + ")";
    }

    /**
     * The relative key, which has the same notes: A minor for C major, C major for A minor; spelt
     * as the estimator spells keys (Db, Eb, Ab, Bb major; C#, F#, G# minor). Null if not a key.
     */
    public static String relative(String keyName) {
        if (shortKey(keyName) == null) return null;
        String[] parts = keyName.strip().split("\\s+");
        int root = pitch(parts[0]);
        if (root < 0) return null;
        boolean major = parts[1].equals("major");
        int other = major ? (root + 9) % 12 : (root + 3) % 12;
        return name(other, major ? "minor" : "major");
    }

    /** The confidence as "72 %". */
    public static String percent(double confidence) {
        return String.format(Locale.ROOT, "%d %%", Math.round(confidence * 100.0));
    }

    static String name(int root, String scale) {
        if (scale.equals("minor")) return ((root == 1 || root == 6 || root == 8) ? SHARP : FLAT).get(root) + " minor";
        return (root == 6 ? SHARP : FLAT).get(root) + " major";
    }

    static int pitch(String note) {
        int i = SHARP.indexOf(note);
        return i >= 0 ? i : FLAT.indexOf(note);
    }
}
