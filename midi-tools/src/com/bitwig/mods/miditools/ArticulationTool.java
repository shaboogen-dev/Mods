package com.bitwig.mods.miditools;

import bml.inspector.BlockContext;
import bml.inspector.Rows;
import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import bml.pianoroll.PianoRoll;
import bml.pianoroll.Refused;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/** Note Articulation in the block: its values and rows. The logic is in {@link ArticulationLogic}. */
final class ArticulationTool implements MidiTool {
    private static final int EDITOR = 0; // the Grid dropdown's first entry: the note editor's own grid
    private static final ArpLogic.Rate[] SIZES = ArpLogic.Rate.values(); // entry i + 1

    private int style; // Legato
    private int grid = EDITOR;
    private int offset;
    private int variation;

    @Override
    public void plan(List<Note> notes, Edit edit) {
        plan(notes, edit, null);
    }

    @Override
    public void plan(List<Note> notes, Edit edit, Object bitwigSelection) {
        double step = 0;
        if (offset != 0 || variation != 0) { // the grid only matters to these two
            step = grid == EDITOR ? PianoRoll.gridStep(bitwigSelection) : SIZES[grid - 1].beats;
            if (!(step > 0)) {
                throw new Refused("the editor shows no grid; choose a grid size");
            }
        }
        int[] groups = NoteEdits.groups(notes);
        List<ArticulationLogic.In> in = new ArrayList<>();
        for (int i = 0; i < notes.size(); i++) {
            Note note = notes.get(i);
            in.add(new ArticulationLogic.In(i, groups[i], note.channel(), note.key(), note.start(), note.end()));
        }
        for (ArticulationLogic.Out out : ArticulationLogic.plan(in, ArticulationLogic.Style.values()[style], step, offset, variation)) {
            edit.setEnd(notes.get(out.index()), out.end());
        }
    }

    @Override
    public boolean clearsRange() {
        return false; // it only changes the selected notes' lengths: the notes around them stay
    }

    @Override
    public String label() {
        return "Note Articulation";
    }

    @Override
    public List<Object> rows(BlockContext context) {
        String[] styles = Arrays.stream(ArticulationLogic.Style.values()).map(s -> s.label).toArray(String[]::new);
        String[] grids = Stream.concat(Stream.of("Editor"), Arrays.stream(SIZES).map(r -> r.label)).toArray(String[]::new);
        return List.of(
                Rows.choice(context, "Style", styles, () -> style, value -> {
                    style = value;
                }).row(),
                Rows.number(context, "Offset", -100, 100, 0, " %", true, () -> offset, value -> {
                    offset = (int) value;
                }),
                Rows.number(context, "Variation", -100, 100, 0, " %", true, () -> variation, value -> {
                    variation = (int) value;
                }),
                Rows.choice(context, "Grid", grids, () -> grid, value -> {
                    grid = value;
                }, () -> offset != 0 || variation != 0).row());
    }
}
