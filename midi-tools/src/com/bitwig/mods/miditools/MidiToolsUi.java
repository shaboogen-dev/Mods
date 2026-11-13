package com.bitwig.mods.miditools;

import bml.api.Bml;
import bml.inspector.BlockContext;
import bml.inspector.Blocks;
import bml.inspector.Context;
import bml.inspector.InspectorProvider;
import bml.inspector.Rows;
import bml.notifications.Notifications;
import bml.pianoroll.Edit;
import bml.pianoroll.Listening;
import bml.pianoroll.Note;
import bml.pianoroll.PianoRoll;
import bml.pianoroll.Preview;
import bml.pianoroll.Refused;
import bml.pianoroll.Stripes;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The one block, its Tool dropdown, the preview and Apply, and what Apply does. Each tool brings its own
 * rows.
 *
 * The block starts on None. Choosing a tool, or changing one of its values, shows what Apply would do: every note it
 * would make or change is drawn as it would end up, as Bitwig's preview notes with Bitwig's eraser stripes, and the
 * notes it would remove or replace are hidden, as Bitwig hides the notes under a drag. Nothing in the project changes
 * until Apply. Selecting other notes, deselecting them, editing notes, Undo and Redo take the
 * preview away and go back to None. Apply prints it as one Undo step, keeps the notes selected and goes back to None.
 * With "Play the preview" on (Settings > Mods), the clip plays the outcome in place of its notes while the preview is
 * shown, whenever it plays.
 * With None chosen, nothing runs.
 */
public final class MidiToolsUi implements InspectorProvider {
    private static final Bml.Mod MOD = Bml.mod("midi-tools");

    /** The tools, in the dropdown's order after None. Add a tool here. */
    private static final List<MidiTool> TOOLS = List.of(
            new StackTool(), new ArpeggiateTool(), new StrumTool(), new ChopTool(), new ChopRhythmTool(), new QuantizeTool(),
            new TimeWarpTool(), new ArticulationTool(), new VoiceLeadTool(), new ExtractBassTool());
    private static final int NONE = 0; // the dropdown's first entry; TOOLS.get(i) is entry i + 1

    private static int chosen = NONE;
    private static final List<WeakReference<BlockContext>> built = new ArrayList<>(); // the blocks built, to refresh them

    // While a tool is chosen: the notes it was chosen on, what is shown, and what ends it.
    private static final Stripes STRIPES = new Stripes();
    private static Set<Object> chosenOn = Set.of();
    private static Preview preview;
    private static Edit previewed; // the plan the preview shows: Apply applies this same plan
    private static Listening selectionWatch;
    private static Listening changeWatch;
    private static boolean previewPosted;
    private static String saidWhileShowing; // the refusal last told while values change: said once, not per update

    /**
     * Apply, as one Undo step named after the tool: for a tool that clears its range, every other note of the clip
     * that overlaps a selected note's range, on any key, is removed; then the tool works on the selected notes.
     * Nothing stays open afterwards.
     */
    static void apply(MidiTool tool, Object bitwigSelection) {
        try {
            List<Note> notes = distinct(PianoRoll.selection(bitwigSelection).notes());
            if (notes.isEmpty()) {
                throw new Refused("select notes first");
            }
            plan(tool, notes, bitwigSelection).apply();
        } catch (Refused e) {
            refused(tool, e);
        } catch (RuntimeException e) {
            MOD.log(tool.label() + " failed", e);
        }
    }

    /** A refused plan, told as a toast: "MIDI Tools: Strum: needs a chord". */
    private static void refused(MidiTool tool, Refused e) {
        Notifications.toast("MIDI Tools: " + tool.label() + ": " + e.getMessage());
    }

    /** What Apply does, as a plan: the same plan is previewed and applied. */
    private static Edit plan(MidiTool tool, List<Note> notes, Object bitwigSelection) {
        Edit edit = PianoRoll.edit(tool.label());
        if (tool.clearsRange()) {
            for (Note other : cleared(notes)) {
                edit.remove(other);
            }
        }
        tool.plan(notes, edit, bitwigSelection);
        return edit;
    }

    /** The notes Apply clears: each selected note's range, in its clip, on every key, but no selected note. */
    private static Set<Note> cleared(List<Note> notes) {
        Set<Note> selected = new HashSet<>(notes);
        Set<Note> cleared = new LinkedHashSet<>();
        for (Note note : notes) {
            if (note.clip() == null) {
                continue;
            }
            for (Note other : note.clip().notesBetween(note.start(), note.end())) {
                if (!selected.contains(other) && ClearLogic.clears(note.start(), note.end(), other.start(), other.end())) {
                    cleared.add(other);
                }
            }
        }
        return cleared;
    }

    /** A note selected in several places, such as each repeat of a clip, counts once. */
    private static List<Note> distinct(List<Note> notes) {
        return new ArrayList<>(new LinkedHashSet<>(notes));
    }

    // The block.

    /** The user changed the tool or one of its values, during Bitwig's handling of that change. */
    private static void userChanged(BlockContext context) {
        if (chosen == NONE) {
            endTool();
            return;
        }
        if (selectionWatch == null) {
            chosenOn = selectedNotes(context.panel());
            selectionWatch = PianoRoll.onSelectionChange(selection -> {
                if (!notesOf(selection.notes()).equals(chosenOn)) {
                    backToNone(); // another selection, or none
                }
            });
            changeWatch = PianoRoll.onChange(MidiToolsUi::backToNone); // an edit, Undo or Redo
        }
        if (!previewPosted) { // values changing quickly: the latest wins
            previewPosted = true;
            PianoRoll.later(() -> {
                previewPosted = false;
                showPreview(context);
            });
        }
    }

    /** The chosen tool's outcome on the selected notes, in place of what was shown. */
    private static void showPreview(BlockContext context) {
        endPreview();
        if (chosen == NONE) {
            return;
        }
        MidiTool tool = TOOLS.get(chosen - 1);
        try {
            List<Note> notes = distinct(PianoRoll.selection(context.panel()).notes());
            if (notes.isEmpty()) {
                throw new Refused("select notes first");
            }
            Edit edit = plan(tool, notes, context.panel());
            // What goes, or changes, is hidden meanwhile; with "Play the preview" on, the clip plays as Apply would leave it.
            Preview shown = edit.preview(true, true, MidiToolsSettings.playPreview());
            preview = shown;
            previewed = edit;
            shown.onEnded(() -> endedElsewhere(shown));
            STRIPES.show(shown.drawn()); // the outcome, striped
            saidWhileShowing = null;
        } catch (Refused e) {
            String said = tool.label() + ": " + e.getMessage();
            if (!said.equals(saidWhileShowing)) { // dragging a value: once, not on every step
                saidWhileShowing = said;
                refused(tool, e);
            }
        } catch (RuntimeException e) {
            MOD.log(tool.label() + ": the preview failed", e);
        }
    }

    /** Apply in the block: the chosen tool; then back to None. */
    private static void applyChosen(BlockContext context) {
        if (chosen == NONE) {
            return;
        }
        MidiTool tool = TOOLS.get(chosen - 1);
        // The plan on screen, unless a value changed since and its preview hasn't been drawn yet.
        Edit shown = preview != null && preview.isShown() && !previewPosted ? previewed : null;
        endTool();
        if (shown != null) {
            try {
                shown.apply(); // exactly what the preview showed
            } catch (RuntimeException e) {
                MOD.log(tool.label() + " failed", e);
            }
        } else {
            apply(tool, context.panel());
        }
        refreshBlocks();
    }

    /** Bitwig ended the preview: its own preview or edit, a change, Undo or Redo. This runs inside what ended it. */
    private static void endedElsewhere(Preview shown) {
        if (preview != shown) {
            return;
        }
        STRIPES.clear();
        preview = null;
        PianoRoll.later(MidiToolsUi::backToNone);
    }

    private static void backToNone() {
        if (chosen == NONE && selectionWatch == null && preview == null) {
            return;
        }
        endTool();
        refreshBlocks();
    }

    /** None: nothing shown, nothing listening. */
    private static void endTool() {
        chosen = NONE;
        endPreview();
        if (selectionWatch != null) {
            selectionWatch.stop();
            changeWatch.stop();
            selectionWatch = null;
            changeWatch = null;
        }
        chosenOn = Set.of();
        saidWhileShowing = null;
    }

    private static void endPreview() {
        STRIPES.clear();
        previewed = null;
        if (preview != null) {
            Preview ending = preview;
            preview = null;
            ending.end();
        }
    }

    private static void refreshBlocks() {
        built.removeIf(block -> block.get() == null);
        for (WeakReference<BlockContext> block : built) {
            BlockContext context = block.get();
            if (context != null) {
                Rows.refresh(context);
            }
        }
    }

    /** The selected notes, by identity; none when there are none to read. */
    private static Set<Object> selectedNotes(Object bitwigSelection) {
        try {
            return notesOf(PianoRoll.selection(bitwigSelection).notes());
        } catch (Refused e) {
            return Set.of();
        }
    }

    private static Set<Object> notesOf(List<Note> notes) {
        Set<Object> set = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Note note : notes) {
            set.add(note.bitwigNote());
        }
        return set;
    }

    @Override
    public void declare(Blocks blocks) {
        String[] labels = new String[TOOLS.size() + 1];
        labels[NONE] = "None";
        for (int i = 0; i < TOOLS.size(); i++) {
            labels[i + 1] = TOOLS.get(i).label();
        }
        blocks.block("midi_tools", "MIDI Tools")
              .in(Context.NOTE)
              .content(context -> {
                  built.removeIf(block -> block.get() == null);
                  built.add(new WeakReference<>(context));
                  List<Object> rows = new ArrayList<>();
                  rows.add(Rows.choice(context, "Tool", labels, () -> chosen, value -> chosen = value).row());
                  for (int i = 0; i < TOOLS.size(); i++) {
                      int entry = i + 1;
                      rows.add(Rows.when(context, () -> chosen == entry, TOOLS.get(i).rows(context).toArray()));
                  }
                  rows.add(Rows.button(context, "Apply", () -> applyChosen(context),
                          () -> chosen != NONE && !selectedNotes(context.panel()).isEmpty()));
                  Rows.onUserChange(context, () -> userChanged(context));
                  return Rows.column(rows.toArray());
              });
    }
}
