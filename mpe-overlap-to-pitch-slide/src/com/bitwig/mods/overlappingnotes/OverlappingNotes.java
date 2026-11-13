package com.bitwig.mods.overlappingnotes;

import bml.api.Bml;
import bml.notifications.Notifications;
import bml.pianoroll.Clip;
import bml.pianoroll.Note;
import bml.pianoroll.NoteSelection;
import bml.pianoroll.Overlap;
import bml.pianoroll.PianoRoll;
import bml.pianoroll.PitchPoint;
import bml.pianoroll.Refused;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Overlaps to Pitch Slides.
 *
 * Two edits for overlapping notes, each one Undo step: {@link PitchSlide} turns each overlap into a
 * pitch slide, and {@link Merge} merges each run of overlapping notes into one note with pitch slides. The
 * actions are declared through Framework: Actions and Menus ({@link OverlappingNotesActions}); the notes
 * are read and edited through Framework: Piano Roll.
 *
 * This class holds what the edits accept: which selections, which overlaps and which pitch curves.
 */
public final class OverlappingNotes {
    static final Bml.Mod MOD = Bml.mod("mpe-overlap-to-pitch-slide");
    static final String NAME = "Overlaps to Pitch Slides";
    static final double EPS = 1.0E-9;

    private OverlappingNotes() {
    }

    /** The selected notes, sorted by start: of {@code menuSelection}, or for a shortcut (null) of the one note editor with notes selected. */
    static List<Note> selectedNotes(Object menuSelection) {
        NoteSelection selection = PianoRoll.selection(menuSelection);
        if (selection.itemCount() < 2) {
            throw new Refused("select two or more notes");
        }
        if (selection.itemCount() > 1024) {
            throw new Refused("too many notes selected");
        }
        if (selection.hasOtherItems()) {
            throw new Refused("the selection holds something other than notes");
        }
        Set<Note> seen = new HashSet<>();
        Clip sharedClip = null;
        for (Note note : selection.notes()) {
            if (note.clip() == null || (sharedClip != null && !note.clip().equals(sharedClip))) {
                throw new Refused("the notes belong to different clips");
            }
            sharedClip = note.clip();
            if (!seen.add(note)) {
                throw new Refused("a note is selected twice");
            }
            double start = note.start();
            double end = note.end();
            if (!Double.isFinite(start) || !Double.isFinite(end) || !Double.isFinite(note.curveTime(start)) || !(start + EPS < end)) {
                throw new Refused("a note has an invalid time");
            }
        }
        List<Note> notes = new ArrayList<>(selection.notes());
        notes.sort(Comparator.comparingDouble(Note::start));
        return List.copyOf(notes);
    }

    /**
     * {@link #acceptedOverlaps}, refused when no notes overlap.
     */
    static Overlap[] overlaps(List<Note> notes) {
        Overlap[] overlaps = acceptedOverlaps(notes);
        for (Overlap overlap : overlaps) {
            if (overlap != null) {
                return overlaps;
            }
        }
        throw new Refused("no selected notes overlap");
    }

    /**
     * overlaps[i] is the overlap of note i and note i+1, or null when they don't overlap. Refused: notes
     * starting together, a note inside another, three notes overlapping at once, and two
     * overlapping notes of the same pitch or more than 96 semitones apart (a pitch slide can't cover that). {@code notes} is sorted by start, so a note's first overlap is with the next
     * note, and a further overlap of the same note is with the note after that.
     */
    private static Overlap[] acceptedOverlaps(List<Note> notes) {
        Overlap[] overlaps = new Overlap[notes.size() - 1];
        List<Overlap> found = PianoRoll.overlaps(notes);
        for (int k = 0; k < found.size(); k++) {
            Overlap overlap = found.get(k);
            Note a = overlap.earlier();
            Note b = overlap.later();
            int i = notes.indexOf(a);
            if (!(a.start() + EPS < b.start())) {
                throw new Refused("two notes start together (notes " + (i + 1) + " and " + (i + 2) + ")");
            }
            if (!(a.end() + EPS < b.end())) {
                throw new Refused("a note lies inside another (notes " + (i + 1) + " and " + (i + 2) + ")");
            }
            if (k + 1 < found.size() && found.get(k + 1).earlier().equals(a)) {
                throw new Refused("three notes overlap at once (from note " + (i + 1) + ")");
            }
            if (a.key() == b.key() || Math.abs(b.key() - a.key()) > 96) {
                throw new Refused("overlapping notes " + (i + 1) + " and " + (i + 2)
                        + " have the same pitch or are more than 96 semitones apart");
            }
            overlaps[i] = overlap;
        }
        return overlaps;
    }

    /**
     * The note's pitch expression points. Refused: more than 4,096 points, values outside ±96 semitones,
     * points out of order, and points that aren't straight lines to the next (other curve shapes would be
     * flattened).
     */
    static List<PitchPoint> straightPitchPoints(Note note) {
        List<PitchPoint> points = note.pitchCurve();
        if (points.size() > 4096) {
            throw new Refused("a note has too many pitch expression points");
        }
        double previous = Double.NEGATIVE_INFINITY;
        for (PitchPoint point : points) {
            double time = point.time();
            double semitones = point.semitones();
            if (!Double.isFinite(time) || !Double.isFinite(semitones) || Math.abs(semitones) > 96.000000001) {
                throw new Refused("a pitch expression point is out of range");
            }
            if (time < previous - EPS) {
                throw new Refused("pitch expression points are out of order");
            }
            previous = time;
            if (!point.straight()) {
                throw new Refused("a note's pitch expression has curves (only straight lines are supported)");
            }
        }
        return points;
    }

    /**
     * Runs one of the edits and logs what it did, why it refused (nothing changed) or how it failed. A
     * refusal is also told to the user as a toast: "Overlaps to Pitch Slides: no selected notes overlap".
     */
    static void run(String label, Object menuSelection, Operation operation) {
        try {
            String result = operation.apply(selectedNotes(menuSelection));
            MOD.log(label + ": " + result);
        } catch (Refused e) {
            MOD.log(label + ": nothing changed: " + e.getMessage());
            Notifications.toast(NAME + ": " + e.getMessage());
        } catch (Throwable t) {
            MOD.log(label + ": failed", t);
        }
    }

    interface Operation {
        /** Plans and applies the edit to the selected notes; returns what it did, for the log. */
        String apply(List<Note> notes) throws Exception;
    }
}
