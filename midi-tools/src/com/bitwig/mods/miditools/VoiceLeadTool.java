package com.bitwig.mods.miditools;

import bml.inspector.BlockContext;
import bml.inspector.Rows;
import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import bml.pianoroll.Refused;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Voice Lead Chords in the block: its values and rows. The logic is in {@link VoiceLeadLogic}. */
final class VoiceLeadTool implements MidiTool {
    private static final String[] LEADING = {"Smooth", "Off"};
    private static final String[] INVERSIONS = {"Keep", "-3", "-2", "-1", "0", "1", "2", "3"};

    private int leading; // Smooth
    private int bass; // Free
    private int inversion; // Keep
    private int voicing; // Keep

    @Override
    public void plan(List<Note> selected, Edit edit) {
        List<Note> notes = NoteEdits.withNeighbours(selected);
        List<LandLogic.N> plain = NoteEdits.plain(notes);
        List<VoiceLeadLogic.In> in = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            LandLogic.N note = plain.get(i);
            in.add(new VoiceLeadLogic.In(i, note.group(), note.channel(), note.key(), note.start(), note.end()));
        }
        VoiceLeadLogic.Result result;
        try {
            result = VoiceLeadLogic.plan(in, leading == 0, VoiceLeadLogic.Bass.values()[bass],
                    inversion == 0 ? null : inversion - 4, VoiceLeadLogic.Voicing.values()[voicing]);
        } catch (IllegalArgumentException e) {
            throw new Refused(e.getMessage());
        }
        if (result.chords() == 0) {
            throw new Refused("select the chords of a progression: notes starting together, on at least two different keys");
        }
        try {
            if (!LandLogic.moveKeys(notes, plain, result.keys(), new NoteEdits(edit))) {
                throw new Refused("the chords already move as little as they can with these values");
            }
        } catch (LandLogic.Collision e) {
            throw new Refused(e.getMessage());
        }
    }

    @Override
    public boolean clearsRange() {
        return false; // it only moves the selected notes to other octaves: the notes around them stay
    }

    @Override
    public String label() {
        return "Voice Lead Chords";
    }

    @Override
    public List<Object> rows(BlockContext context) {
        String[] basses = Arrays.stream(VoiceLeadLogic.Bass.values()).map(b -> b.label).toArray(String[]::new);
        String[] voicings = Arrays.stream(VoiceLeadLogic.Voicing.values()).map(v -> v.label).toArray(String[]::new);
        return List.of(
                Rows.choice(context, "Leading", LEADING, () -> leading, value -> {
                    leading = value;
                }).row(),
                Rows.choice(context, "Bass", basses, () -> bass, value -> {
                    bass = value;
                }).row(),
                Rows.choice(context, "Inversion", INVERSIONS, () -> inversion, value -> {
                    inversion = value;
                }).row(),
                Rows.choice(context, "Voicing", voicings, () -> voicing, value -> {
                    voicing = value;
                }).row());
    }
}
