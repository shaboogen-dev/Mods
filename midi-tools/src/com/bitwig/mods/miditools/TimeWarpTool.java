package com.bitwig.mods.miditools;

import bml.inspector.BlockContext;
import bml.inspector.Rows;
import bml.pianoroll.Clip;
import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Time Warp in the block: its values and rows. The logic is in {@link TimeWarpLogic}. */
final class TimeWarpTool implements MidiTool {
    private int mode; // Speed Up
    private int amount = 50;
    private int lengths; // Keep

    @Override
    public void plan(List<Note> notes, Edit edit) {
        Map<Clip, Integer> groups = new HashMap<>(); // note times can only be compared within one clip
        List<TimeWarpLogic.In> in = new ArrayList<>();
        for (int i = 0; i < notes.size(); i++) {
            Note note = notes.get(i);
            int group = note.clip() == null ? -1 : groups.computeIfAbsent(note.clip(), c -> groups.size());
            in.add(new TimeWarpLogic.In(i, group, note.start(), note.end()));
        }
        for (TimeWarpLogic.Out out : TimeWarpLogic.plan(in, TimeWarpLogic.Mode.values()[mode], amount, lengths == 0)) {
            edit.move(notes.get(out.index()), out.start());
            edit.setEnd(notes.get(out.index()), out.end());
        }
    }

    @Override
    public boolean clearsRange() {
        return false; // it only moves the selected notes: the notes around them stay
    }

    @Override
    public String label() {
        return "Time Warp";
    }

    @Override
    public List<Object> rows(BlockContext context) {
        String[] modes = Arrays.stream(TimeWarpLogic.Mode.values()).map(m -> m.label).toArray(String[]::new);
        return List.of(
                Rows.choice(context, "Mode", modes, () -> mode, value -> {
                    mode = value;
                }).row(),
                Rows.number(context, "Amount", 0, 100, 50, " %", false, () -> amount, value -> {
                    amount = (int) value;
                }),
                Rows.choice(context, "Lengths", new String[] {"Keep", "Warp"}, () -> lengths, value -> {
                    lengths = value;
                }).row());
    }
}
