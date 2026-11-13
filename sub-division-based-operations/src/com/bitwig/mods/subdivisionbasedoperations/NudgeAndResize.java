package com.bitwig.mods.subdivisionbasedoperations;

import bitwig.obf.bii; // a note editor's model
import bml.api.Bml;
import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import bml.pianoroll.PianoRoll;
import bml.pianoroll.Refused;
import java.util.LinkedHashSet;
import java.util.Set;

/** Moves the selected notes and changes their length by the grid step. */
final class NudgeAndResize {
    private static final Bml.Mod MOD = Bml.mod("sub-division-based-operations");
    private static final double TOLERANCE = 1.0E-8;

    /** The step of the last nudge. A nudge on a different step is the first on that grid. */
    private static Double lastNudgeStep;

    private NudgeAndResize() {
    }

    static void nudge(bii editor, int direction) {
        double step = PianoRoll.gridStep(editor.FzB()); // FzB: its selection
        if (!(step > 1.0E-6) || editor.UNj()) { // UNj: the editor's time selection is what's active
            return;
        }
        boolean gridChanged = lastNudgeStep != null && lastNudgeStep != step;
        try {
            Edit edit = PianoRoll.edit("Nudge Notes by Grid");
            boolean any = false;
            for (Note note : selected(editor)) {
                double start = note.start();
                if (!(note.end() > start + TOLERANCE)) {
                    continue;
                }
                edit.move(note, GridRule.newStart(start, step, direction, gridChanged));
                any = true;
            }
            if (any) {
                edit.apply();
                lastNudgeStep = step;
            }
        } catch (Refused e) {
            MOD.log("nothing changed: " + e.getMessage());
        }
    }

    static void resize(bii editor, int direction) {
        double step = PianoRoll.gridStep(editor.FzB());
        if (!(step > 1.0E-6) || editor.UNj()) { // UNj: the editor's time selection is what's active
            return;
        }
        try {
            Edit edit = PianoRoll.edit("Resize Notes by Grid");
            for (Note note : selected(editor)) {
                double start = note.start();
                double end = note.end();
                if (!(end > start + TOLERANCE)) {
                    continue;
                }
                edit.setEnd(note, GridRule.newEnd(start, end, step, direction));
            }
            edit.apply();
        } catch (Refused e) {
            MOD.log("nothing changed: " + e.getMessage());
        }
    }

    /** Each selected note once. */
    private static Set<Note> selected(bii editor) {
        return new LinkedHashSet<>(PianoRoll.selection(editor.FzB()).notes()); // FzB: its selection
    }
}
