package com.bitwig.mods.pencilmodifiers;

import bitwig.obf.bob; // what a note grid draws into: a clip, or a track's clips
import bitwig.obf.dJH; // a mouse press
import bitwig.obf.dnf; // a window's widget root; holds the Alt state
import bitwig.obf.n1; // a note
import bitwig.obf.wYc; // the drawing preview: a note waiting in a lane
import bml.pianoroll.Stripes;
import com.bitwig.flt.document.core.master.g2L; // the project, which holds the preview
import com.bitwig.flt.widget.core.timeline.interaction.Mld; // Bitwig's gesture that draws one note
import com.bitwig.flt.widget.core.timeline.kig; // the piano roll's note grid

/**
 * Bitwig's striped note look on the pencil's note, while releasing right now would open {@link OverlapMenu},
 * and for as long as that menu is open. The stripes are the note painter's own, for the eraser, shown through
 * Framework: Piano Roll's {@link Stripes}: the painter's question gets another answer for this one note, and the
 * note itself isn't changed, so the document isn't.
 *
 * The check runs when its answer can change: on Alt going down, and on a step that changes the note while Alt
 * is held. Painting only looks the note up among the striped ones.
 */
final class NoteLook {
    private static final Stripes STRIPES = new Stripes();

    // The note with the look; null for none.
    private static n1 striped;

    // The drawing under way, with the pencil in a note grid; null when none. The rest runs on the UI thread.
    private static Mld gesture;
    private static kig grid;
    private static dnf window;
    private static boolean alt;
    private static boolean wouldOpen;
    private static boolean menuOpen;

    // The note as of Bitwig's last preview of it. Velocity plays no part in what the menu offers.
    private static bob target;
    private static double start;
    private static double duration;
    private static int channel;
    private static int key;
    private static double velocity;

    private static boolean failureLogged;

    private NoteLook() {
    }

    static void began(Mld drawing, dJH press) {
        if (drawing.LaE() instanceof kig pressed && pressed.mA_().LaE()) { // mA_: tool, LaE: pencil
            gesture = drawing;
            grid = pressed;
            window = pressed.kI_();
            alt = press.E3().IwV(); // E3: modifiers, IwV: Alt
            wouldOpen = false;
            target = null;
        }
    }

    static void stepped(bob into, double noteStart, double noteDuration, int noteChannel, int noteKey, double noteVelocity) {
        if (gesture == null) {
            return;
        }
        boolean changed = into != target || noteStart != start || noteDuration != duration
                || noteChannel != channel || noteKey != key;
        target = into;
        start = noteStart;
        duration = noteDuration;
        channel = noteChannel;
        key = noteKey;
        velocity = noteVelocity;
        if (changed && alt) {
            check();
        }
        update();
    }

    /** Runs on every mouse event, in every window: out at once unless Alt changed during a drawing. */
    static void altChanged(dnf root, boolean held) {
        if (gesture == null || held == alt || root != window) {
            return;
        }
        alt = held;
        if (held && target != null) {
            check();
        }
        update();
    }

    static void ended(Mld drawing) {
        if (drawing == gesture) {
            gesture = null;
            if (!menuOpen) {
                show(null, grid);
            }
        }
    }

    /** The menu took the note over: the note is still Bitwig's preview. */
    static void menuOpened(kig menuGrid) {
        try {
            menuOpen = true;
            show(preview(menuGrid), menuGrid);
        } catch (Throwable t) {
            failed("menu open", t);
        }
    }

    /** Every way out of the menu: before the note goes in, so a real note never has the look. */
    static void menuClosed(kig menuGrid) {
        try {
            menuOpen = false;
            show(null, menuGrid);
        } catch (Throwable t) {
            failed("menu close", t);
        }
    }

    /** No look from here on, until the next drawing: the note and the menu carry on without it. */
    static void failed(String where, Throwable t) {
        gesture = null;
        menuOpen = false;
        striped = null;
        STRIPES.clear();
        if (!failureLogged) {
            failureLogged = true;
            PencilModifiers.MOD.log("note look: failed at " + where + "; no stripes", t);
        }
    }

    private static void check() {
        wouldOpen = OverlapMenu.wouldOffer(grid, target, start, duration, channel, key, velocity);
    }

    private static void update() {
        boolean on = menuOpen || (alt && wouldOpen && grid.mA_().LaE());
        show(on ? preview(grid) : null, grid); // read each time: a drawing that crosses into another lane gets a new note
    }

    private static void show(n1 note, kig on) {
        if (note != striped) {
            striped = note;
            if (note != null) {
                STRIPES.show(java.util.List.of(note));
            } else {
                STRIPES.clear();
            }
            on.jbi(); // repaint: Alt alone doesn't repaint the grid
        }
    }

    private static n1 preview(kig on) {
        return on.lq_() instanceof g2L project && project.Sxo() instanceof wYc drawn ? drawn.cfM() : null;
    }
}
