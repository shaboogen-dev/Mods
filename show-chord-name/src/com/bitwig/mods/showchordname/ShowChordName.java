package com.bitwig.mods.showchordname;

import bml.api.Bml;
import bml.inspector.BlockContext;
import bml.inspector.Blocks;
import bml.inspector.Context;
import bml.inspector.InspectorProvider;
import bml.inspector.Rows;
import bml.pianoroll.Clip;
import bml.pianoroll.KeySignature;
import bml.pianoroll.Listening;
import bml.pianoroll.Note;
import bml.pianoroll.PianoRoll;
import bml.pianoroll.Refused;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * A Chord block after Bitwig's NOTE block in the Note Inspector, with one read-only row: the name of the chord the
 * selected notes make. Read only: nothing in the project changes.
 *
 * The name is worked out when the block is built and again only when the framework says the selected notes changed
 * or something Undo can undo changed (a note moved to another key, the key signature, Undo, Redo). While no Chord
 * block is on screen, nothing listens.
 */
public final class ShowChordName implements InspectorProvider {
    private static final Bml.Mod MOD = Bml.mod("show-chord-name");

    private static final List<WeakReference<BlockContext>> built = new ArrayList<>(); // blocks on screen, to refresh
    private static Listening selectionWatch;
    private static Listening changeWatch;

    @Override
    public void declare(Blocks blocks) {
        blocks.block("chord", "Chord")
              .in(Context.NOTE_SECTION)
              .bottom() // right after Bitwig's NOTE block
              .content(context -> {
                  remember(context);
                  Shown shown = new Shown(context);
                  return Rows.column(Rows.text(context, "Name", shown::name));
              });
    }

    /** The name one block shows, worked out once per refresh. */
    private static final class Shown {
        private final BlockContext context;
        private int readFor = -1;
        private String name = ChordNamer.NONE;

        Shown(BlockContext context) {
            this.context = context;
        }

        String name() {
            if (readFor != generation) { // the framework asks more than once per refresh
                readFor = generation;
                name = nameOf(context.panel());
            }
            return name;
        }
    }

    private static int generation;

    /** The chord the panel's selected notes make. Never throws: a failure shows "-" and is logged. */
    static String nameOf(Object panel) {
        try {
            List<Note> notes = PianoRoll.selection(panel).notes();
            if (notes.isEmpty()) {
                return ChordNamer.NONE;
            }
            List<Note> once = new ArrayList<>(new LinkedHashSet<>(notes)); // a note selected in several places counts once
            int[] keys = new int[once.size()];
            Note first = once.get(0);
            for (int i = 0; i < keys.length; i++) {
                Note note = once.get(i);
                keys[i] = note.key();
                if (note.start() < first.start()) {
                    first = note;
                }
            }
            return ChordNamer.name(keys, spellingAt(first));
        } catch (Refused e) {
            return ChordNamer.NONE; // no notes to read here, or several editors
        } catch (RuntimeException e) {
            MOD.log("the chord name couldn't be worked out", e);
            return ChordNamer.NONE;
        }
    }

    /** The spelling of Bitwig's key signature where the earliest note starts; the common names without one. */
    private static Spelling spellingAt(Note note) {
        Clip clip = note.clip();
        if (clip == null) {
            return Spelling.COMMON;
        }
        try {
            KeySignature key = clip.keySignatureAt(note.start());
            boolean[] inScale = new boolean[12];
            for (int i = 0; i < 12; i++) {
                inScale[i] = key.inScale(i);
            }
            return Spelling.forKey(key.root(), inScale);
        } catch (RuntimeException | LinkageError e) {
            return Spelling.COMMON; // no key signature to read (Refused), or none on this Bitwig version
        }
    }

    // Keeping the blocks up to date.

    private static void remember(BlockContext context) {
        built.removeIf(block -> block.get() == null);
        built.add(new WeakReference<>(context));
        generation++;
        if (selectionWatch == null) {
            selectionWatch = PianoRoll.onSelectionChange(selection -> refreshAll());
            changeWatch = PianoRoll.onChange(ShowChordName::refreshAll);
        }
    }

    private static void refreshAll() {
        generation++;
        built.removeIf(block -> block.get() == null);
        if (built.isEmpty()) { // no Chord block left: stop listening until one is built again
            selectionWatch.stop();
            changeWatch.stop();
            selectionWatch = null;
            changeWatch = null;
            return;
        }
        for (WeakReference<BlockContext> block : built) {
            BlockContext context = block.get();
            if (context != null) {
                try {
                    Rows.refresh(context);
                } catch (RuntimeException e) {
                    MOD.log("the Chord block couldn't be refreshed", e);
                }
            }
        }
    }
}
