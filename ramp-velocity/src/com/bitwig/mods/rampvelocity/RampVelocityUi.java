package com.bitwig.mods.rampvelocity;

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
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Velocity Ramp block in the Note Inspector, its preview and Apply.
 *
 * Nothing shows until the user changes From, To or Curve. Then the ramp's outcome is drawn as Bitwig's preview notes
 * with Bitwig's eraser stripes, each note it changes hidden in its old form, as MIDI Tools does. Selecting other notes,
 * editing notes, Undo and Redo take the preview away. Apply prints it as one Undo step and keeps the notes selected.
 * With "Play the preview" on (Settings > Mods), the clip plays the outcome in place of its notes while the preview is
 * shown, whenever it plays.
 */
public final class RampVelocityUi implements InspectorProvider {
    private static final Bml.Mod MOD = Bml.mod("ramp-velocity");
    private static final String LABEL = "Ramp Velocity";

    // The values, kept until Bitwig closes. The original's defaults: a crescendo over the whole range.
    private static int from = 1;
    private static int to = 127;
    private static int curve = 0;

    private static final List<WeakReference<BlockContext>> built = new ArrayList<>();

    // While a preview shows: the notes it was drawn for, the plan, and what ends it.
    private static final Stripes STRIPES = new Stripes();
    private static Set<Object> shownOn = Set.of();
    private static Preview preview;
    private static Edit previewed;
    private static Listening selectionWatch;
    private static Listening changeWatch;
    private static boolean previewPosted;
    private static String saidWhileShowing; // the refusal last told while values change: said once, not per update

    /** The ramp on these selected notes, as a plan. Notes already at their velocity aren't edited. */
    private static Edit plan(Object bitwigSelection) {
        List<Note> notes = distinct(PianoRoll.selection(bitwigSelection).notes());
        if (notes.size() < 2) {
            throw new Refused("select at least 2 notes");
        }
        List<RampLogic.In> in = new ArrayList<>(notes.size());
        for (int i = 0; i < notes.size(); i++) {
            in.add(new RampLogic.In(i, notes.get(i).clip(), notes.get(i).start()));
        }
        Edit edit = PianoRoll.edit(LABEL);
        for (RampLogic.Out out : RampLogic.plan(in, from, to, curve)) {
            Note note = notes.get(out.index());
            double velocity = out.velocity() / 127.0;
            if (Math.abs(note.velocity() - velocity) > 1e-9) {
                edit.setVelocity(note, velocity);
            }
        }
        return edit;
    }

    /** Apply from the block when no preview of the current values is on screen. */
    private static void applyNow(Object bitwigSelection) {
        try {
            plan(bitwigSelection).apply();
        } catch (Refused e) {
            Notifications.toast(LABEL + ": " + e.getMessage());
        } catch (RuntimeException e) {
            MOD.log(LABEL + " failed", e);
        }
    }

    /** A note selected in several places, such as each repeat of a clip, counts once. */
    private static List<Note> distinct(List<Note> notes) {
        return new ArrayList<>(new LinkedHashSet<>(notes));
    }

    // The block.

    /** The user changed From, To or Curve, during Bitwig's handling of that change. */
    private static void userChanged(BlockContext context) {
        if (selectionWatch == null) {
            shownOn = selectedNotes(context.panel());
            selectionWatch = PianoRoll.onSelectionChange(selection -> {
                if (!notesOf(selection.notes()).equals(shownOn)) {
                    endAll(); // another selection, or none
                }
            });
            changeWatch = PianoRoll.onChange(RampVelocityUi::endAll); // an edit, Undo or Redo
        }
        if (!previewPosted) { // values changing quickly while dragging: the latest wins
            previewPosted = true;
            PianoRoll.later(() -> {
                previewPosted = false;
                showPreview(context);
            });
        }
    }

    private static void showPreview(BlockContext context) {
        endPreview();
        try {
            Edit edit = plan(context.panel());
            // With "Play the preview" on, the clip plays as Apply would leave it while it is shown.
            Preview shown = edit.preview(true, true, RampVelocitySettings.playPreview());
            preview = shown;
            previewed = edit;
            shown.onEnded(() -> endedElsewhere(shown));
            STRIPES.show(shown.drawn());
            saidWhileShowing = null;
        } catch (Refused e) {
            if (!e.getMessage().equals(saidWhileShowing)) { // dragging a value: once, not on every step
                saidWhileShowing = e.getMessage();
                Notifications.toast(LABEL + ": " + e.getMessage());
            }
        } catch (RuntimeException e) {
            MOD.log(LABEL + ": the preview failed", e);
        }
    }

    /** Apply in the block: the plan on screen, unless a value changed and its preview hasn't been drawn yet. */
    private static void applyFromBlock(BlockContext context) {
        Edit shown = preview != null && preview.isShown() && !previewPosted ? previewed : null;
        endAll();
        if (shown != null) {
            try {
                shown.apply();
            } catch (RuntimeException e) {
                MOD.log(LABEL + " failed", e);
            }
        } else {
            applyNow(context.panel());
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
        PianoRoll.later(RampVelocityUi::endAll);
    }

    /** Nothing shown, nothing listening. */
    private static void endAll() {
        endPreview();
        if (selectionWatch != null) {
            selectionWatch.stop();
            changeWatch.stop();
            selectionWatch = null;
            changeWatch = null;
        }
        shownOn = Set.of();
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
        blocks.block("ramp_velocity", "Velocity Ramp")
              .in(Context.NOTE)
              .content(context -> {
                  built.removeIf(block -> block.get() == null);
                  built.add(new WeakReference<>(context));
                  Object rows = Rows.column(
                          Rows.number(context, "From", 1, 127, 1, "", false, () -> from, value -> from = (int) value),
                          Rows.number(context, "To", 1, 127, 127, "", false, () -> to, value -> to = (int) value),
                          Rows.number(context, "Curve", -100, 100, 0, " %", true, () -> curve,
                                  value -> curve = (int) value, () -> from != to),
                          Rows.button(context, "Apply", () -> applyFromBlock(context),
                                  () -> selectedNotes(context.panel()).size() >= 2));
                  Rows.onUserChange(context, () -> userChanged(context));
                  return rows;
              });
    }
}
