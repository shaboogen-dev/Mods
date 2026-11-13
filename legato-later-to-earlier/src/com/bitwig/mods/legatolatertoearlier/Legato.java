package com.bitwig.mods.legatolatertoearlier;

import bml.api.Bml;
import bml.notifications.Notifications;
import bml.pianoroll.Clip;
import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import bml.pianoroll.NoteSelection;
import bml.pianoroll.Overlap;
import bml.pianoroll.PianoRoll;
import bml.pianoroll.Refused;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Make Legato (Later → Earlier): taking the selected notes in order of their starts, each note after the
 * first starts where the note before it ends, whether they overlap or have a gap between them; its end
 * stays. Pitch expression is left as it is, at the same times. One Undo step. (Bitwig's own Make Legato
 * goes the other way: it moves earlier notes' ends to the next note's start.)
 */
final class Legato {
    static final String LABEL = "Make Legato (Later → Earlier)";
    static final Bml.Mod MOD = Bml.mod("legato-later-to-earlier");
    private static final double EPS = 1.0E-9;

    private Legato() {
    }

    /**
     * Runs the edit on {@code menuSelection} (null: the one note editor with notes selected) and logs what it
     * did. A refusal is also told to the user as a toast: "Make Legato (Later → Earlier): select two or more notes".
     */
    static void run(Object menuSelection) {
        try {
            MOD.log(LABEL + ": " + apply(selectedNotes(menuSelection)));
        } catch (Refused e) {
            MOD.log(LABEL + ": nothing changed: " + e.getMessage());
            Notifications.toast(LABEL + ": " + e.getMessage());
        } catch (Throwable t) {
            MOD.log(LABEL + ": failed", t);
        }
    }

    static String apply(List<Note> notes) {
        boolean[] overlapping = overlapping(notes);
        Edit edit = PianoRoll.edit(LABEL);
        int count = 0;
        for (int i = 0; i + 1 < notes.size(); i++) {
            Note earlier = notes.get(i);
            Note later = notes.get(i + 1);
            if (overlapping[i] || later.start() > earlier.end() + EPS) {
                edit.trimStart(later, earlier.end());
                count++;
            }
        }
        if (count == 0) {
            throw new Refused("the selected notes already meet");
        }
        edit.apply();
        return notes.size() + " notes, " + count + " moved";
    }

    /** The selected notes, sorted by start: two or more notes of one clip and nothing else. */
    private static List<Note> selectedNotes(Object menuSelection) {
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
            if (!Double.isFinite(note.start()) || !Double.isFinite(note.end()) || !(note.start() + EPS < note.end())) {
                throw new Refused("a note has an invalid time");
            }
        }
        List<Note> notes = new ArrayList<>(selection.notes());
        notes.sort(Comparator.comparingDouble(Note::start));
        return List.copyOf(notes);
    }

    /**
     * overlapping[i]: note i overlaps note i+1. Refused: notes starting together, a note inside another,
     * three notes overlapping at once. {@code notes} is sorted by start, so a note's first overlap is with
     * the next note, and a further overlap of the same note is with the note after that.
     */
    private static boolean[] overlapping(List<Note> notes) {
        boolean[] overlapping = new boolean[notes.size() - 1];
        List<Overlap> found = PianoRoll.overlaps(notes);
        for (int k = 0; k < found.size(); k++) {
            Note a = found.get(k).earlier();
            Note b = found.get(k).later();
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
            overlapping[i] = true;
        }
        return overlapping;
    }
}
