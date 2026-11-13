package com.bitwig.mods.miditools;

import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import java.util.ArrayList;
import java.util.List;

/** What Chop and Chop Rhythm share: turning notes into the logic's input, and its plan into edits. */
final class ChopWriter {
    private ChopWriter() {
    }

    static List<ChopLogic.In> inputs(List<Note> notes) {
        List<ChopLogic.In> in = new ArrayList<>();
        for (int i = 0; i < notes.size(); i++) {
            Note note = notes.get(i);
            in.add(new ChopLogic.In(i, note.key(), note.channel(), note.start(), note.end(), note.velocity() * 127.0));
        }
        return in;
    }

    static void write(List<Note> notes, ChopLogic.Plan plan, Edit edit) {
        // Copies first, as a copy takes what its note holds when it is made.
        for (ChopLogic.Out out : plan.slices()) {
            if (!out.sameNote()) {
                Note source = notes.get(out.source());
                if (out.velocity127() < 0) {
                    edit.copyIfAbsent(source, source.key(), out.start(), out.end());
                } else {
                    edit.setVelocity(edit.copy(source, out.start(), out.end()), out.velocity127() / 127.0);
                }
            }
        }
        for (ChopLogic.Out out : plan.slices()) {
            if (out.sameNote()) {
                Note note = notes.get(out.source());
                edit.move(note, out.start());
                edit.setEnd(note, out.end());
                if (out.velocity127() >= 0) {
                    edit.setVelocity(note, out.velocity127() / 127.0);
                }
            }
        }
        for (int silenced : plan.silenced()) {
            edit.remove(notes.get(silenced));
        }
    }
}
