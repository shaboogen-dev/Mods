package com.bitwig.mods.miditools;

import bml.inspector.BlockContext;
import bml.inspector.Rows;
import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import bml.pianoroll.PianoRoll;
import bml.pianoroll.Refused;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/** Quantize in the block: its values and rows. The logic is in {@link QuantizeLogic}. */
final class QuantizeTool implements MidiTool {
    private static final int EDITOR = 0; // the Grid dropdown's first entry: the note editor's own grid
    private static final ArpLogic.Rate[] SIZES = ArpLogic.Rate.values(); // entry i + 1

    private int grid = EDITOR;
    private int snap; // Start
    private int amount = 100;

    @Override
    public void plan(List<Note> notes, Edit edit) {
        plan(notes, edit, null);
    }

    @Override
    public void plan(List<Note> notes, Edit edit, Object bitwigSelection) {
        double step = grid == EDITOR ? PianoRoll.gridStep(bitwigSelection) : SIZES[grid - 1].beats;
        if (!(step > 0)) {
            throw new Refused("the editor shows no grid; choose a grid size");
        }
        QuantizeLogic.Snap how = QuantizeLogic.Snap.values()[snap];
        for (Note note : notes) {
            QuantizeLogic.Out out = QuantizeLogic.place(note.start(), note.end(), step, how, amount);
            if (Math.abs(out.start() - note.start()) > ClearLogic.TOLERANCE
                    || Math.abs(out.end() - note.end()) > ClearLogic.TOLERANCE) {
                edit.move(note, out.start());
                edit.setEnd(note, out.end());
            }
        }
    }

    @Override
    public boolean clearsRange() {
        return false; // it only moves the selected notes: the notes around them stay
    }

    @Override
    public String label() {
        return "Quantize";
    }

    @Override
    public List<Object> rows(BlockContext context) {
        String[] grids = Stream.concat(Stream.of("Editor"), Arrays.stream(SIZES).map(r -> r.label)).toArray(String[]::new);
        String[] snaps = Arrays.stream(QuantizeLogic.Snap.values()).map(s -> s.label).toArray(String[]::new);
        return List.of(
                Rows.choice(context, "Grid", grids, () -> grid, value -> {
                    grid = value;
                }).row(),
                Rows.choice(context, "Snap", snaps, () -> snap, value -> {
                    snap = value;
                }).row(),
                Rows.number(context, "Amount", 0, 100, 100, " %", false, () -> amount, value -> {
                    amount = (int) value;
                }));
    }
}
