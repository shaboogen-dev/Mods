package com.bitwig.mods.audiotomidi;

import com.bitwig.mods.audioanalysis.Insert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The engine's note file ("ADNA" TSV): its duration and the notes of each target, and their move
 * onto the clip's beats. Pure; tested offline with sample files.
 */
public final class BridgeTsv {
    /** One note in the audio's seconds. */
    public record Note(double start, double end, int pitch, int velocity) { }

    /** What the file holds for one target. {@code duration} is the analysed audio's length in seconds. */
    public record Analysis(double duration, List<Note> notes) { }

    private BridgeTsv() { }

    /** The engine's name for a target in the file. */
    public static String mode(Target target) {
        return switch (target) {
            case MELODY -> "melody";
            case HARMONY -> "chords";
            case DRUMS -> "groove";
        };
    }

    public static Analysis read(Path tsv, Target target) {
        try {
            if (tsv == null || !Files.isRegularFile(tsv)) return new Analysis(0, List.of());
            return parse(Files.readAllLines(tsv, StandardCharsets.UTF_8), target);
        } catch (IOException | RuntimeException e) {
            return new Analysis(0, List.of());
        }
    }

    /** Whether the file was written with every target (ADNA 2 or 3), so it can be reused for any. */
    public static boolean complete(Path tsv) {
        try {
            if (tsv == null || !Files.isRegularFile(tsv)) return false;
            List<String> lines = Files.readAllLines(tsv, StandardCharsets.UTF_8);
            return !lines.isEmpty() && (lines.get(0).equals("ADNA\t2") || lines.get(0).equals("ADNA\t3"));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Version 1 holds one target's notes ({@code NOTE start end pitch velocity}); versions 2 and 3
     * hold every target ({@code NOTE mode start end pitch velocity}). Invalid notes are skipped.
     */
    public static Analysis parse(List<String> lines, Target target) {
        if (lines == null || lines.isEmpty()) return new Analysis(0, List.of());
        String head = lines.get(0).strip();
        if (!head.equals("ADNA\t1") && !head.equals("ADNA\t2") && !head.equals("ADNA\t3")) return new Analysis(0, List.of());
        boolean bundled = !head.equals("ADNA\t1");
        String mode = mode(target);
        double duration = 0;
        List<Note> notes = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] row = line.strip().split("\t", -1);
            try {
                if (row.length == 3 && row[0].equals("META") && row[1].equals("duration")) {
                    duration = Double.parseDouble(row[2]);
                    continue;
                }
                int at;
                if (bundled && row.length == 6 && row[0].equals("NOTE") && row[1].equals(mode)) at = 2;
                else if (!bundled && row.length == 5 && row[0].equals("NOTE")) at = 1;
                else continue;
                double start = Double.parseDouble(row[at]);
                double end = Double.parseDouble(row[at + 1]);
                int pitch = Integer.parseInt(row[at + 2]);
                int velocity = Integer.parseInt(row[at + 3]);
                if (Double.isFinite(start) && Double.isFinite(end) && end > start && start >= 0
                        && pitch >= 0 && pitch <= 127 && velocity >= 1 && velocity <= 127)
                    notes.add(new Note(start, end, pitch, velocity));
            } catch (NumberFormatException ignored) {
                // not a note line
            }
        }
        return new Analysis(Double.isFinite(duration) && duration > 0 ? duration : 0, List.copyOf(notes));
    }

    /**
     * The notes on the clip's beats: the analysed audio's length is spread over the clip's
     * {@code clipBeats}, as the original did (the whole file is analysed, and the clip is taken to
     * play the whole file). Velocity 1-127 becomes 0-1.
     */
    public static List<Insert.Note> toBeats(Analysis analysis, double clipBeats) {
        List<Insert.Note> out = new ArrayList<>();
        if (analysis == null || analysis.notes().isEmpty() || !(clipBeats > 0)) return out;
        double duration = analysis.duration() > 0 ? analysis.duration()
            : analysis.notes().stream().mapToDouble(Note::end).max().orElse(0);
        if (!(duration > 0)) return out;
        double scale = clipBeats / duration;
        for (Note n : analysis.notes()) {
            double start = n.start() * scale;
            double end = Math.min(duration, n.end()) * scale;
            if (!(end > start) || start >= clipBeats) continue;
            out.add(new Insert.Note(start, end - start, n.pitch(), n.velocity() / 127.0));
        }
        return out;
    }
}
