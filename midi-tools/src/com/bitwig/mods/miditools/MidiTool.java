package com.bitwig.mods.miditools;

import bml.inspector.BlockContext;
import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import java.util.List;

/**
 * One entry of the block's Tool dropdown. A new tool implements this and takes one line in
 * {@link MidiToolsUi}'s list; nothing else changes.
 */
interface MidiTool {
    /** Its name in the dropdown and the Undo step. */
    String label();

    /** Its rows. Their values are read when the preview is drawn and when Apply runs. */
    List<Object> rows(BlockContext context);

    /**
     * The edits for these selected notes, with the current values. The same plan is shown as the preview and applied
     * by Apply. Refused leaves everything as it was.
     */
    void plan(List<Note> notes, Edit edit);

    /**
     * As {@link #plan(List, Edit)}, for a tool that reads the note editor too, such as its grid: bitwigSelection is
     * the block's panel, as PianoRoll reads it.
     */
    default void plan(List<Note> notes, Edit edit, Object bitwigSelection) {
        plan(notes, edit);
    }

    /**
     * Whether Apply first clears every other note in the selected notes' time ranges. A tool that writes new notes
     * there does; a tool that only moves the selected notes leaves the notes around them alone.
     */
    default boolean clearsRange() {
        return true;
    }
}
