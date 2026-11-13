package com.bitwig.mods.miditools;

import bml.inspector.BlockContext;
import bml.inspector.Rows;
import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import bml.pianoroll.Refused;
import java.util.List;

/** Chop in the block: its values and rows. The musical logic is in {@link ChopLogic}. */
final class ChopTool implements MidiTool {
    private int parts = 4;
    private int gaps;
    private int variation;

    @Override
    public void plan(List<Note> notes, Edit edit) {
        if ((long) notes.size() * parts > ChopLogic.MAX_SLICES) {
            throw new Refused("that would make too many notes for the editor");
        }
        ChopWriter.write(notes, ChopLogic.parts(ChopWriter.inputs(notes), parts, gaps, variation), edit);
    }

    @Override
    public String label() {
        return "Chop";
    }

    @Override
    public List<Object> rows(BlockContext context) {
        return List.of(
                Rows.number(context, "Parts", ChopLogic.MIN_PARTS, ChopLogic.MAX_PARTS, 4, "", false, () -> parts, value -> {
                    parts = (int) value;
                }),
                Rows.number(context, "Gaps", -15, 15, 0, "", true, () -> gaps, value -> {
                    gaps = (int) value;
                }),
                Rows.number(context, "Variation", 0, 100, 0, " %", false, () -> variation, value -> {
                    variation = (int) value;
                }));
    }
}
