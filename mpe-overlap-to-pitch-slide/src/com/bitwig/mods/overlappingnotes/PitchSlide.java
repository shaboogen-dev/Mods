package com.bitwig.mods.overlappingnotes;

import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import bml.pianoroll.Overlap;
import bml.pianoroll.PianoRoll;
import bml.pianoroll.PitchPoint;
import bml.pianoroll.Refused;
import java.util.ArrayList;
import java.util.List;

/**
 * Convert Overlap to Pitch Slide: for each pair of overlapping notes, the earlier note gets a pitch slide
 * across the overlap to the later note's pitch, and the later note is trimmed to start where the earlier
 * one ends. Both notes are kept.
 *
 * The earlier note keeps its own pitch points before the overlap; points from the overlap on are replaced
 * by the slide. The later note keeps its points after the trim, at the same times.
 *
 * Public for other mods: {@link #LABEL} and {@link #plan}.
 */
public final class PitchSlide {
    public static final String LABEL = "Convert Overlap to Pitch Slide";

    private PitchSlide() {
    }

    static String apply(List<Note> notes) {
        Edit edit = PianoRoll.edit(LABEL);
        String result = plan(notes, edit);
        edit.apply();
        return result;
    }

    /**
     * Adds this edit of {@code notes}, sorted by start, to {@code edit}; returns what it will do, for the
     * log. Refused when it can't be done on these notes, with nothing changed.
     */
    public static String plan(List<Note> notes, Edit edit) {
        Overlap[] overlaps = OverlappingNotes.overlaps(notes);
        for (Overlap overlap : overlaps) {
            if (overlap != null) {
                edit.trimStart(overlap.later(), overlap.end());
            }
        }
        int slides = 0;
        for (int i = 0; i < notes.size(); i++) {
            Overlap incoming = i > 0 ? overlaps[i - 1] : null;
            Overlap outgoing = i < overlaps.length ? overlaps[i] : null;
            if (incoming == null && outgoing == null) {
                continue;
            }
            Note note = notes.get(i);
            List<PitchPoint> points = OverlappingNotes.straightPitchPoints(note);
            Double trimEnd = incoming != null ? note.curveTime(incoming.end()) : null;
            Double slideStart = outgoing != null ? note.curveTime(outgoing.start()) : null;
            Double slideEnd = outgoing != null ? note.curveTime(outgoing.end()) : null;
            int semitones = outgoing != null ? outgoing.later().key() - note.key() : 0;
            if (outgoing != null) {
                slides++;
            }
            if (outgoing != null || !points.isEmpty()) {
                edit.setPitchCurve(note, rebuild(points, trimEnd, slideStart, slideEnd, semitones));
            }
        }
        return notes.size() + " notes, " + slides + " slides";
    }

    /**
     * Drops the points up to {@code trimEnd} (the part the trim cuts off) and from {@code slideStart} on,
     * then adds the slide: 0 at {@code slideStart} to {@code semitones} at {@code slideEnd}. Null bounds
     * don't apply.
     */
    private static List<PitchPoint> rebuild(List<PitchPoint> points, Double trimEnd, Double slideStart, Double slideEnd,
                                            int semitones) {
        double eps = OverlappingNotes.EPS;
        if (slideStart != null && !(slideStart + eps < slideEnd)) {
            throw new Refused("a slide would have no length");
        }
        if (trimEnd != null && slideStart != null && trimEnd > slideStart + eps) {
            throw new Refused("a note's trim would cut into its own slide");
        }
        List<PitchPoint> result = new ArrayList<>();
        for (PitchPoint point : points) {
            if (trimEnd != null && point.time() <= trimEnd + eps) {
                continue;
            }
            if (slideStart != null && point.time() >= slideStart - eps) {
                continue;
            }
            result.add(point);
        }
        if (slideStart != null) {
            result.add(PitchPoint.of(slideStart, 0.0));
            result.add(PitchPoint.of(slideEnd, semitones));
        }
        return List.copyOf(result);
    }
}
