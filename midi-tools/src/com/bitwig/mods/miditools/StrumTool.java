package com.bitwig.mods.miditools;

import bml.inspector.BlockContext;
import bml.inspector.Rows;
import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import bml.pianoroll.Refused;
import java.util.ArrayList;
import java.util.List;

/** Strum in the block: its values and rows. The musical logic is in {@link StrumLogic}. */
final class StrumTool implements MidiTool {
    private int low;
    private int high;
    private int tension;

    @Override
    public void plan(List<Note> notes, Edit edit) {
        List<StrumLogic.In> in = new ArrayList<>();
        for (int i = 0; i < notes.size(); i++) {
            Note note = notes.get(i);
            in.add(new StrumLogic.In(i, note.clip() == null ? 0 : note.clip().hashCode(), note.channel(), note.key(), note.start(), note.end()));
        }
        List<StrumLogic.Out> placed = StrumLogic.plan(in, low, high, tension);
        if (placed.isEmpty()) {
            throw new Refused("select at least 2 chord notes of different keys to strum");
        }
        for (StrumLogic.Out out : placed) {
            edit.move(notes.get(out.index()), out.start());
            edit.setEnd(notes.get(out.index()), out.end());
        }
    }

    @Override
    public String label() {
        return "Strum";
    }

    @Override
    public List<Object> rows(BlockContext context) {
        return List.of(
                Rows.number(context, "Low", -100, 100, 0, " %", true, () -> low, value -> {
                    low = (int) value;
                }),
                Rows.number(context, "High", -100, 100, 0, " %", true, () -> high, value -> {
                    high = (int) value;
                }),
                Rows.number(context, "Tension", -100, 100, 0, " %", true, () -> tension, value -> {
                    tension = (int) value;
                }, () -> low != high)); // greyed out with both the same: every note then moves the same way
    }
}
