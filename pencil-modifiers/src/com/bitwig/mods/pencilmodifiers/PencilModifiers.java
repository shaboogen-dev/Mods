package com.bitwig.mods.pencilmodifiers;

import bitwig.obf.bob; // what a note grid draws into: a clip, or a track's clips
import bitwig.obf.dJH; // a mouse press
import bitwig.obf.dJI; // a mouse release
import bitwig.obf.dKW; // a popup menu
import bitwig.obf.dnf; // a window's widget root; holds the Alt state
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.This;
import com.bitwig.flt.widget.core.timeline.interaction.Mld; // Bitwig's gesture that draws one note
import com.bitwig.flt.widget.core.timeline.kig; // the piano roll's note grid

/**
 * Piano Roll: Pencil Pitch Slides.
 *
 * Decides, when the pencil's note is about to go in, whether Alt held at that moment opens
 * {@link OverlapMenu}. Otherwise, and whenever anything fails, Bitwig's own insert runs untouched. {@link NoteLook} shows on the drawn note when that modifier would act.
 */
public final class PencilModifiers {
    static final Bml.Mod MOD = Bml.mod("pencil-modifiers");

    // The release Bitwig is handling; the next hook runs inside it, on the same thread.
    private static Mld gesture;
    private static dJI release;

    private PencilModifiers() {
    }

    /** The drawing gesture is released: its event carries the modifiers held at this moment. */
    @Hook(target = Mld.class, method = "cfM(LdJI;)V", at = At.ENTRY, id = "pencil-release")
    public static void pencilRelease(@This Mld drawing, @Arg(0) dJI event) {
        gesture = drawing;
        release = event;
    }

    /** Bitwig inserts the note it has drawn. It stays out for now only when {@link OverlapMenu} takes it over. */
    @Hook(target = Mld.class, method = "cfM(LdJI;)V", at = At.REPLACE_CALL, id = "insert-drawn-note",
          callOwner = bob.class, call = "cfM(DDIID)V", occurrence = 1)
    public static void insertDrawnNote(bob target, double start, double duration, int channel, int key, double velocity) {
        boolean takenOver = false;
        try {
            OverlapMenu.finishOpenMenu(); // should already be closed: its note must never wait on a later one
            takenOver = modifierApplies() && OverlapMenu.offer(gesture.LaE(), release, target, start, duration, channel, key, velocity);
        } catch (Throwable t) {
            MOD.log("drawn note: failed to look at it; Bitwig inserts it", t);
        }
        gesture = null;
        release = null;
        if (!takenOver) {
            target.cfM(start, duration, channel, key, velocity); // Bitwig's own insert
        }
    }

    /** Alt at the release, the pencil tool, the piano roll's grid. A double-click with the pointer draws with this gesture too. */
    private static boolean modifierApplies() {
        return release.E3().IwV() && gesture.LaE() instanceof kig grid && grid.mA_().LaE(); // E3: modifiers, IwV: Alt, mA_: tool, LaE: pencil
    }

    /** The pencil's press: a drawing begins. */
    @Hook(target = Mld.class, method = "cfM(LdJH;)V", at = At.ENTRY, id = "pencil-press")
    public static void pencilPress(@This Mld drawing, @Arg(0) dJH event) {
        try {
            NoteLook.began(drawing, event);
        } catch (Throwable t) {
            NoteLook.failed("press", t);
        }
    }

    /** Each step of the drawing, the press's own included: Bitwig updates its preview of the note. */
    @Hook(target = Mld.class, method = "MRY(LdJK;)V", at = At.REPLACE_CALL, id = "drawing-step",
          callOwner = bob.class, call = "MRY(DDIID)V", occurrence = 1)
    public static void drawingStep(bob target, double start, double duration, int channel, int key, double velocity) {
        target.MRY(start, duration, channel, key, velocity); // Bitwig's own preview
        try {
            NoteLook.stepped(target, start, duration, channel, key, velocity);
        } catch (Throwable t) {
            NoteLook.failed("drawing step", t);
        }
    }

    /** The drawing's end, by release or cancelled. */
    @Hook(target = Mld.class, method = "UNj()V", at = At.ENTRY, id = "drawing-end")
    public static void drawingEnd(@This Mld drawing) {
        try {
            NoteLook.ended(drawing);
        } catch (Throwable t) {
            NoteLook.failed("drawing end", t);
        }
    }

    /** Bitwig stores whether Alt is held, on every mouse event and modifier key. */
    @Hook(target = dnf.class, method = "FzB(Z)V", at = At.ENTRY, id = "alt-state")
    public static void altState(@This dnf window, @Arg(0) boolean held) {
        try {
            NoteLook.altChanged(window, held);
        } catch (Throwable t) {
            NoteLook.failed("Alt", t);
        }
    }

    /** A popup closes: after a chosen row has run. */
    @Hook(target = dKW.class, method = "wGc()V", at = At.ENTRY, id = "popup-closed")
    public static void popupClosed(@This dKW popup) {
        try {
            OverlapMenu.popupClosed(popup);
        } catch (Throwable t) {
            MOD.log("popup closed: failed", t);
        }
    }
}
