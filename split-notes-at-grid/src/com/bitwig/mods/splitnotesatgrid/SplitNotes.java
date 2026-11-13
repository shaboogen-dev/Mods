package com.bitwig.mods.splitnotesatgrid;

import bitwig.obf.bii; // a note editor's model
import bml.api.Bml;
import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import bml.pianoroll.NoteSelection;
import bml.pianoroll.PianoRoll;
import bml.pianoroll.Refused;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Cuts the selected notes on the grid lines they cross. */
final class SplitNotes {
    private static final Bml.Mod MOD = Bml.mod("split-notes-at-grid");
    private static final double TOLERANCE = 1.0E-5;

    private SplitNotes() {
    }

    static void split(bii editor) {
        Object selection = editor.FzB(); // its selection
        double step = PianoRoll.gridStep(selection);
        if (!(step > 1.0E-6)) {
            return;
        }
        NoteSelection selected = PianoRoll.selection(selection);
        try {
            Edit edit = PianoRoll.edit("Split Notes at Grid");
            for (Note note : new LinkedHashSet<>(selected.notes())) {
                List<Double> lines = linesInside(note.start(), note.end(), step);
                if (!lines.isEmpty()) {
                    cut(edit, note, lines, !selected.hasOtherItems());
                }
            }
            edit.apply();
        } catch (Refused e) {
            MOD.log("nothing changed: " + e.getMessage());
        }
    }

    /** Grid lines inside the note. Nothing for a note no longer than one step, even when it crosses a line. */
    private static List<Double> linesInside(double start, double end, double step) {
        List<Double> lines = new ArrayList<>();
        if (end - start <= step + TOLERANCE) {
            return lines;
        }
        for (long i = (long) Math.floor((start + TOLERANCE) / step) + 1; i * step < end - TOLERANCE; i++) {
            if (i * step > start + TOLERANCE) {
                lines.add(i * step);
            }
        }
        return lines;
    }

    /**
     * Copies before shortening the note: a copy is made from the note as it is then. Each piece is a whole
     * copy, because expressions are per note: they play from their beginning in every piece.
     */
    private static void cut(Edit edit, Note note, List<Double> lines, boolean selectPieces) {
        for (int i = 0; i < lines.size(); i++) {
            Note piece = edit.copy(note, lines.get(i), i + 1 < lines.size() ? lines.get(i + 1) : note.end());
            if (selectPieces) {
                edit.selectLike(piece, note);
            }
        }
        edit.setEnd(note, lines.get(0));
    }
}
