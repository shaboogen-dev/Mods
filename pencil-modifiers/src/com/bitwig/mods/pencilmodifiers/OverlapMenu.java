package com.bitwig.mods.pencilmodifiers;

import bitwig.obf.Ob; // something with a place on the timeline
import bitwig.obf.bob; // what a note grid draws into: a clip, or a track's clips
import bitwig.obf.bok; // a track's clips in the launcher
import bitwig.obf.bom; // a track's clips in the arranger
import bitwig.obf.bos; // one clip
import bitwig.obf.dJI; // a mouse release
import bitwig.obf.dKW; // a popup menu
import bitwig.obf.dZ; // timeline conversions
import bitwig.obf.dnf; // a window's widget root
import bitwig.obf.dpN; // the application: Bitwig's UI event loop
import bitwig.obf.drO; // a popup, as a window holds it
import bitwig.obf.duG; // a window
import bitwig.obf.g1d; // a clip that holds notes
import bml.pianoroll.Clip;
import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import bml.pianoroll.PianoRoll;
import bml.pianoroll.Refused;
import com.bitwig.base.geom.BZG; // a place in a window, in pixels
import com.bitwig.flt.widget.core.timeline.kig; // the piano roll's note grid
import com.bitwig.mods.overlappingnotes.Merge;
import com.bitwig.mods.overlappingnotes.PitchSlide;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Alt at the release of a note that overlaps another: a menu of what to do with the overlap.
 *
 * The note isn't in the document while the menu is open. Bitwig's drawing preview stays on screen, as nothing
 * has cleared it. A row adds the note and its edit as one Undo step ({@link Edit#add}); closing the menu
 * without a row runs Bitwig's own insert, as if there had been no menu. So there is no Undo step to hold open,
 * and at every moment the note is either in the document or still only drawn, with this class holding what
 * it takes to insert it.
 */
final class OverlapMenu {
    /** An edit of the overlapping notes, as Piano Roll: Overlaps to Pitch Slides offers them. */
    private record Operation(String label, Planner planner) {
    }

    private interface Planner {
        String plan(List<Note> notes, Edit edit);
    }

    private static final List<Operation> OPERATIONS = List.of(
            new Operation(PitchSlide.LABEL, PitchSlide::plan),
            new Operation(Merge.LABEL, Merge::plan));

    /** The note the gesture drew, in the grid's time and in its clip's. */
    private record Drawn(kig grid, bob target, g1d clip, double start, double clipStart, double duration,
                         int channel, int key, double velocity) {
    }

    // The note waiting for the menu; null when nothing is waiting. All of this runs on the UI thread.
    private static Drawn waiting;
    private static dKW openPopup;

    private OverlapMenu() {
    }

    /** Takes over the note and opens the menu, or returns false when the note overlaps nothing the edits accept. */
    static boolean offer(com.bitwig.flt.widget.core.timeline.wUk widget, dJI release, bob target, double start,
                         double duration, int channel, int key, double velocity) {
        Drawn drawn = drawn((kig) widget, target, start, duration, channel, key, velocity);
        if (drawn == null) {
            PencilModifiers.MOD.log("Alt at release: no clip holds notes at " + start + "; Bitwig inserts the note");
            return false;
        }
        List<Operation> offered = offered(drawn, true);
        if (offered.isEmpty()) {
            return false;
        }
        double x = release.hbP();
        double y = release.Grk();
        dpN.TVZ().asyncExec(() -> open(drawn, offered, x, y)); // once Bitwig has finished with the release
        waiting = drawn;
        openPopup = null;
        NoteLook.menuOpened(drawn.grid());
        return true;
    }

    /** Whether {@link #offer} would open the menu for this note right now. It logs nothing and changes nothing. */
    static boolean wouldOffer(kig grid, bob target, double start, double duration, int channel, int key, double velocity) {
        Drawn drawn = drawn(grid, target, start, duration, channel, key, velocity);
        return drawn != null && !offered(drawn, false).isEmpty();
    }

    /** The note in its clip's time, or null when no clip holds notes there. */
    private static Drawn drawn(kig grid, bob target, double start, double duration, int channel, int key, double velocity) {
        g1d clip = clipAt(target, start);
        if (clip == null) {
            return null;
        }
        double clipStart = target instanceof bos ? start : dZ.cfM(start, (Ob) clip); // a track's time as the clip's, as Bitwig converts it
        return new Drawn(grid, target, clip, start, clipStart, duration, channel, key, velocity);
    }

    /** The clip Bitwig would insert into, if it holds notes. */
    private static g1d clipAt(bob target, double start) {
        Object clip = target instanceof bos oneClip ? oneClip.mV_() // the clip
                : target instanceof bom || target instanceof bok ? target.kFU().LaE().g_(start) // the track's clip at this time
                : null;
        return clip instanceof g1d notes ? notes : null;
    }

    /** The edits that accept the note and everything it overlaps, tried with nothing applied. */
    private static List<Operation> offered(Drawn drawn, boolean log) {
        List<Operation> offered = new ArrayList<>();
        for (Operation operation : OPERATIONS) {
            try {
                String result = plan(drawn, operation, PianoRoll.edit(operation.label()));
                if (result == null) {
                    if (log) {
                        PencilModifiers.MOD.log("Alt at release: no other note overlaps; Bitwig inserts the note");
                    }
                    return List.of();
                }
                offered.add(operation);
            } catch (Refused e) {
                if (log) {
                    PencilModifiers.MOD.log("Alt at release: " + operation.label() + " not offered: " + e.getMessage());
                }
            }
        }
        return offered;
    }

    /**
     * Plans adding the note and this edit of it and everything it overlaps: the notes of other keys that sound
     * with it. Bitwig's insert cuts notes of its own key, so they don't count. The edit's own rules decide the
     * rest. Returns what it will do, or null when no other note overlaps.
     */
    private static String plan(Drawn drawn, Operation operation, Edit edit) {
        Clip clip = PianoRoll.clip(drawn.clip());
        List<Note> notes = new ArrayList<>();
        for (Note other : clip.notesBetween(drawn.clipStart(), drawn.clipStart() + drawn.duration())) {
            if (other.key() != drawn.key()) {
                notes.add(other);
            }
        }
        if (notes.isEmpty()) {
            return null;
        }
        notes.add(edit.add(clip, drawn.channel(), drawn.key(), drawn.clipStart(), drawn.duration(), drawn.velocity(),
                           drawn.target().mR_())); // mR_: the selection Bitwig's own insert uses
        notes.sort(Comparator.comparingDouble(Note::start));
        return operation.planner().plan(List.copyOf(notes), edit);
    }

    /** Bitwig's own popup at the mouse, opened the way a widget opens its right-click menu. */
    private static void open(Drawn drawn, List<Operation> offered, double x, double y) {
        if (waiting != drawn) {
            return;
        }
        try {
            kig grid = drawn.grid();
            dKW popup = dKW.FzB(grid);
            for (Operation operation : offered) {
                Runnable choose = () -> chosen(drawn, operation);
                popup.cfM(operation.label(), choose); // a row
            }
            dnf window = grid.kI_();
            if (grid.TMF(drO.class) == null) { // not inside a popup itself
                window.lYc(); // close any other popups
            }
            BZG place = grid.HJr(); // the grid's place in its window
            duG host = grid.jg_();
            openPopup = popup;
            window.cfM(popup, Math.round(place.FzB() + x), Math.round(place.hUR() + y), host != null && host.EYR(), true);
        } catch (Throwable t) {
            PencilModifiers.MOD.log("menu failed to open", t);
            openPopup = null;
            insertAsDrawn("the menu failed to open");
        }
    }

    /** A row was chosen. Bitwig runs this before it closes the popup. */
    private static void chosen(Drawn drawn, Operation operation) {
        if (waiting != drawn) {
            return;
        }
        waiting = null;
        openPopup = null;
        NoteLook.menuClosed(drawn.grid());
        boolean applying = false;
        try {
            Edit edit = PianoRoll.edit(operation.label());
            String result = plan(drawn, operation, edit); // the notes as they are now
            if (result == null) {
                throw new Refused("no other note overlaps any more");
            }
            drawn.grid().lq_().hQm(); // Bitwig clears its preview as its own insert starts
            applying = true;
            edit.apply();
            PencilModifiers.MOD.log(operation.label() + ": note and edit, " + result);
        } catch (Refused e) {
            PencilModifiers.MOD.log(operation.label() + " refused: " + e.getMessage());
            insert(drawn, "the edit was refused");
        } catch (Throwable t) {
            PencilModifiers.MOD.log(operation.label() + " failed; undo step closed", t);
            if (!applying || !isInserted(drawn)) {
                insert(drawn, "the edit failed before adding it");
            }
        }
    }

    /** Whether a note like the drawn one has reached its clip, for after a failure. */
    private static boolean isInserted(Drawn drawn) {
        for (Note note : PianoRoll.clip(drawn.clip()).notesBetween(drawn.clipStart(), drawn.clipStart() + drawn.duration())) {
            if (note.key() == drawn.key() && note.channel() == drawn.channel() && Math.abs(note.start() - drawn.clipStart()) < 1.0E-9) {
                return true;
            }
        }
        return false;
    }

    /** A popup closed. Ours, with its note still waiting: no row was chosen. */
    static void popupClosed(dKW popup) {
        if (popup == openPopup && waiting != null) {
            openPopup = null;
            insertAsDrawn("menu dismissed");
        }
    }

    /** A menu still open at the next drawn note, which can't normally be. */
    static void finishOpenMenu() {
        if (waiting != null) {
            openPopup = null;
            insertAsDrawn("a new note was drawn with the menu still open");
        }
    }

    private static void insertAsDrawn(String why) {
        Drawn drawn = waiting;
        waiting = null;
        if (drawn != null) {
            NoteLook.menuClosed(drawn.grid());
            insert(drawn, why);
        }
    }

    /** Bitwig's own insert: its own Undo step, its own preview clearing and selection. */
    private static void insert(Drawn drawn, String why) {
        try {
            drawn.target().cfM(drawn.start(), drawn.duration(), drawn.channel(), drawn.key(), drawn.velocity());
            PencilModifiers.MOD.log(why + ": Bitwig inserted the note as drawn");
        } catch (Throwable t) {
            PencilModifiers.MOD.log(why + ": Bitwig's insert failed", t);
        }
    }
}
