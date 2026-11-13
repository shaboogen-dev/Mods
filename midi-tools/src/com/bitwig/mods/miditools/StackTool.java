package com.bitwig.mods.miditools;

import bml.inspector.BlockContext;
import bml.inspector.Rows;
import bml.pianoroll.Edit;
import bml.pianoroll.KeySignature;
import bml.pianoroll.Note;
import bml.pianoroll.Refused;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/** Stack Chords in the block: its values and rows. The musical logic is in {@link StackLogic}. */
final class StackTool implements MidiTool {
    private static final String[] MODES = {"Chord", "Degrees"};

    private int mode;
    private int chord;
    private int degrees;
    private int inversion = 3; // -3 to 3, kept from 0: the row shows inversion - 3
    private int octave = 2; // -2 to 2, kept from 0: the row shows octave - 2

    private static List<List<Note>> oneEach(List<Note> notes) {
        List<List<Note>> each = new ArrayList<>();
        notes.forEach(note -> each.add(List.of(note)));
        return each;
    }

    @Override
    public void plan(List<Note> roots, Edit edit) {
        for (Note root : roots) {
            int[] from = intervals(root);
            if (from.length == 0) {
                throw new Refused("the note isn't in the key signature's scale");
            }
            int[] keys = StackLogic.voicing(root.key(), from, inversion - 3, octave - 2);
            if (!StackLogic.inRange(keys)) {
                throw new Refused("the chord goes outside the MIDI range");
            }
            StackLogic.Plan plan = StackLogic.plan(root.key(), keys);
            for (int key : plan.copies()) {
                edit.copyIfAbsent(root, key, root.start(), root.end()); // copies first: they take the root as it is
            }
            edit.setKey(root, plan.rootKey());
        }
    }

    private int[] intervals(Note root) {
        if (mode == 1) {
            KeySignature key = keyOf(root);
            return StackLogic.degreeIntervals(root.key(), key.root(), scaleOf(key), StackLogic.Degrees.values()[degrees].steps);
        }
        StackLogic.Chord type = StackLogic.Chord.values()[chord];
        if (type != StackLogic.Chord.SCALE_TRIAD) {
            return type.intervals;
        }
        KeySignature key = keyOf(root);
        return StackLogic.scaleTriad(root.key(), key.root(), scaleOf(key));
    }

    private static KeySignature keyOf(Note root) {
        if (root.clip() == null) {
            throw new Refused("the note's key signature can't be read");
        }
        return root.clip().keySignatureAt(root.start());
    }

    /** The scale as semitones above its root, as the logic takes it. */
    private static BitSet scaleOf(KeySignature key) {
        BitSet scale = new BitSet(12);
        for (int i = 0; i < 12; i++) {
            scale.set(i, key.inScale(i));
        }
        return scale;
    }

    @Override
    public String label() {
        return "Stack Chords";
    }

    @Override
    public List<Object> rows(BlockContext context) {
        String[] chords = Arrays.stream(StackLogic.Chord.values()).map(c -> c.label).toArray(String[]::new);
        String[] patterns = Arrays.stream(StackLogic.Degrees.values()).map(d -> d.label).toArray(String[]::new);
        return List.of(
                Rows.choice(context, "Mode", MODES, () -> mode, value -> {
                    mode = value;
                }).row(),
                Rows.when(context, () -> mode == 0,
                        Rows.choice(context, "Chord", chords, () -> chord, value -> {
                            chord = value;
                        }).row()),
                Rows.when(context, () -> mode == 1,
                        Rows.choice(context, "Degrees", patterns, () -> degrees, value -> {
                            degrees = value;
                        }).row()),
                Rows.number(context, "Inversion", -3, 3, 0, "", true, () -> inversion - 3, value -> {
                    inversion = (int) value + 3;
                }),
                Rows.number(context, "Octave", -2, 2, 0, "", true, () -> octave - 2, value -> {
                    octave = (int) value + 2;
                }));
    }
}
