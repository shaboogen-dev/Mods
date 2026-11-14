package com.bitwig.mods.stepsequencer;

import bitwig.obf.LYu; // native MIDI note
import bml.pianoroll.Note;
import bml.pianoroll.Refused;
import com.bitwig.flt.document.api.document.ApiModificationProcessor;
import com.bitwig.flt.document.core.master.IoW; // native undoable change

record StepNote(Note note, int repeat, double chance) {
    static StepNote read(Note note) {
        LYu event = (LYu) note.bitwigNote();
        return new StepNote(note, event.znu() ? event.Xzi() : 0, event.Ynu() ? event.QJx() : 1.0);
    }

    int repeatCount() {
        if (repeat >= 0) {
            return repeat + 1;
        }
        // Negative native values express a beat interval instead of a division of the note.
        return Math.max(1, (int) Math.ceil((note.end() - note.start()) * (1 - repeat) / 4.0));
    }

    void setRepeatCount(int count) {
        edit("Set Step Repeat", event -> {
            event.iCL(count - 1);
            event.THY(count > 1);
        });
    }

    void setChance(int percent) {
        edit("Set Step Chance", event -> {
            event.THY(percent / 100.0);
            event.k31(percent < 100);
        });
    }

    private void edit(String label, java.util.function.Consumer<LYu> update) {
        LYu event = (LYu) note.bitwigNote();
        IoW change = new IoW();
        if (!change.vhT(event, label)) {
            throw new Refused("The note could not be edited.");
        }
        try {
            ApiModificationProcessor.modify(() -> update.accept(event));
        } finally {
            if (!change.E3I()) {
                StepSequencer.MOD.log(label + ": Bitwig could not close the undo step");
            }
        }
    }
}
