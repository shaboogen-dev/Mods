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
 * Merge Overlapping Notes with Pitch Slide: each run of overlapping notes becomes its first note, lengthened
 * to the run's last end, with a pitch slide across each overlap to the next note's pitch, held until the
 * next slide. The first note keeps its own pitch points before the first overlap; the other notes are
 * removed with their expressions.
 *
 * Public for other mods: {@link #LABEL} and {@link #plan}.
 */
public final class Merge {
    public static final String LABEL = "Merge Overlapping Notes with Pitch Slide";

    private Merge() {
    }

    /** One run of overlapping notes, notes[first..last], and the first note's new pitch curve. */
    private record Run(int first, int last, List<PitchPoint> curve) {
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
        List<Run> runs = runs(notes, overlaps);
        for (Run run : runs) {
            Note first = notes.get(run.first());
            edit.setPitchCurve(first, run.curve());
            edit.setEnd(first, notes.get(run.last()).end());
            for (int k = run.first() + 1; k <= run.last(); k++) {
                edit.remove(notes.get(k));
            }
        }
        return notes.size() + " notes, " + runs.size() + " merged";
    }

    private static List<Run> runs(List<Note> notes, Overlap[] overlaps) {
        double eps = OverlappingNotes.EPS;
        List<Run> runs = new ArrayList<>();
        int i = 0;
        while (i < overlaps.length) {
            if (overlaps[i] == null) {
                i++;
                continue;
            }
            int first = i;
            int last = i + 1;
            while (last < overlaps.length && overlaps[last] != null) {
                last++;
            }
            Note base = notes.get(first);
            List<PitchPoint> curve = new ArrayList<>();
            for (PitchPoint point : OverlappingNotes.straightPitchPoints(base)) {
                if (point.time() < base.curveTime(overlaps[first].start()) - eps) {
                    curve.add(point);
                }
            }
            for (int k = first; k < last; k++) {
                Overlap overlap = overlaps[k];
                int from = overlap.earlier().key() - base.key();
                int to = overlap.later().key() - base.key();
                if (Math.abs(from) > 96 || Math.abs(to) > 96) {
                    throw new Refused("the merged note would span more than 96 semitones");
                }
                double slideStart = base.curveTime(overlap.start());
                double slideEnd = base.curveTime(overlap.end());
                if (slideStart < -eps || slideStart + eps >= slideEnd) {
                    throw new Refused("a slide would have no length");
                }
                curve.add(PitchPoint.of(slideStart, from));
                curve.add(PitchPoint.of(slideEnd, to));
            }
            double previous = Double.NEGATIVE_INFINITY;
            for (PitchPoint point : curve) {
                if (point.time() < previous - eps) {
                    throw new Refused("the merged pitch curve would be out of order");
                }
                previous = point.time();
            }
            runs.add(new Run(first, last, List.copyOf(curve)));
            i = last;
        }
        return runs;
    }
}
