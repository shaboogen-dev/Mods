package com.bitwig.mods.miditools;

import bml.inspector.BlockContext;
import bml.inspector.Rows;
import bml.pianoroll.Edit;
import bml.pianoroll.KeySignature;
import bml.pianoroll.Note;
import bml.pianoroll.Refused;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/** Extract Bass in the block: its values and rows. The logic is in {@link BassLogic}. */
final class ExtractBassTool implements MidiTool {
    private static final String[] OUTPUTS = {"Add Below", "Replace Chords"};

    private int output; // Add Below
    private int octave = 1; // -2 to 0, kept from 0: the row shows octave - 2, so -1

    @Override
    public void plan(List<Note> selected, Edit edit) {
        List<Note> notes = NoteEdits.withNeighbours(selected);
        List<LandLogic.N> plain = NoteEdits.plain(notes);
        List<BassLogic.In> in = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            LandLogic.N note = plain.get(i);
            in.add(new BassLogic.In(i, note.group(), note.channel(), note.key(), note.start(), note.end()));
        }
        List<BassLogic.Chord> chords;
        try {
            chords = BassLogic.plan(in, scale(selected), octave - 2);
        } catch (IllegalArgumentException e) {
            throw new Refused(e.getMessage());
        }
        if (chords.stream().noneMatch(chord -> chord.keys().stream().map(k -> k % 12).distinct().count() > 1)) {
            throw new Refused("select the chords of a progression: notes starting together, on at least two different keys");
        }
        BassLogic.Output out = BassLogic.output(chords, plain, output == 1);
        if (out.isEmpty()) {
            throw new Refused("every chord already has its bass note there");
        }
        try {
            LandLogic.write(notes, plain, out.removed(), out.newKeys(), out.copies(),
                    LandLogic.plan(plain, out.removed(), out.newKeys(), out.copies()), new NoteEdits(edit));
        } catch (LandLogic.Collision e) {
            throw new Refused(e.getMessage());
        }
    }

    /** The key signature at the earliest selected note; none when it can't be read: then only the chords decide. */
    private static BassLogic.Scale scale(List<Note> notes) {
        Note first = notes.stream().filter(n -> n.clip() != null).min((a, b) -> Double.compare(a.start(), b.start())).orElse(null);
        if (first == null) {
            return null;
        }
        try {
            KeySignature key = first.clip().keySignatureAt(first.start());
            BitSet mask = new BitSet(12);
            for (int i = 0; i < 12; i++) {
                mask.set(i, key.inScale(i));
            }
            return new BassLogic.Scale(key.root(), mask);
        } catch (Refused e) {
            return null;
        }
    }

    @Override
    public boolean clearsRange() {
        return false; // the chords stay, or become the bass; only what the bass lands on, on its own key, is cut
    }

    @Override
    public String label() {
        return "Extract Bass";
    }

    @Override
    public List<Object> rows(BlockContext context) {
        return List.of(
                Rows.choice(context, "Output", OUTPUTS, () -> output, value -> {
                    output = value;
                }).row(),
                Rows.number(context, "Octave", -2, 0, -1, "", true, () -> octave - 2, value -> {
                    octave = (int) value + 2;
                }));
    }
}
