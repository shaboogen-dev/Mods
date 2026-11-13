package com.bitwig.mods.editclipsreliably;

import bitwig.obf.Ob; // a clip
import bitwig.obf.aBW; // a window's project view
import bitwig.obf.aZv; // the Detail Editor panel's widget
import bitwig.obf.bed; // a clip launcher slot
import bitwig.obf.bit; // how the Detail Editor scrolls to a clip and whether it opens
import bitwig.obf.bjv; // the same, for clip launcher clips
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import com.bitwig.flt.widget.core.timeline.TmB; // an arranger lane (where clips are drawn)
import java.util.List;

/**
 * Clips: Edit New Clips Reliably.
 *
 * With Bitwig's "Automatically open new clips in Detail Editor Panel" on, Bitwig takes the
 * Detail Editor of the view that is showing, gives it the new clip (it scrolls to the clip once
 * the editor's model has caught up), and only then opens the Detail Editor. When opening switches
 * to the Edit view (Bitwig's profiles that edit in the Edit view, or Streamlined Views' "open
 * maximised"), the Edit view has a Detail Editor of its own: the scroll goes to the one that was
 * hidden, and the one that opens stays where it was, often with no clip in sight. Bitwig's
 * double-click on a clip already opens first when it has no editor; this mod does the same for
 * new clips: open the Detail Editor, then give the new clip to the editor that is now showing.
 *
 * Only the two places where the setting opens a new clip are hooked (Bitwig schedules them only
 * when the setting is on), so with the setting off nothing changes.
 */
public final class EditClipsReliably {
    private static final Bml.Mod MOD = Bml.mod("edit-clips-reliably");

    private EditClipsReliably() {
    }

    /** A clip drawn or inserted in the arranger (TmB.cfM(aBW, pqE, double), run at the end of the event). */
    @Hook(target = TmB.class, method = "cfM(LaBW;LpqE;D)V", at = At.REPLACE_CALL, id = "arranger-new-clip",
          callOwner = aZv.class, call = "cfM(Ljava/util/List;LOb;DLbit;Z)V", occurrence = 1)
    public static void arrangerNewClip(aZv editor, List<?> clips, Ob clip, double position, bit scroll, boolean open) {
        aZv target = editor;
        boolean opened = false;
        try {
            if (open && scroll.MRY()) { // MRY(): scroll to the clip and open the editor
                target = openFirst(editor);
                opened = true;
            }
        } catch (Throwable failure) {
            MOD.log("Could not open the Detail Editor first; the new clip opens as Bitwig opens it.", failure);
            target = editor;
            opened = false;
        }
        // Bitwig's own hand-over; with the editor already open, its own opening is skipped
        target.cfM(clips, clip, position, scroll, open && !opened);
    }

    /** A clip created in a clip launcher slot (bed.cfM(aBW, Ob), run at the end of the event). */
    @Hook(target = bed.class, method = "cfM(LaBW;LOb;)V", at = At.REPLACE_CALL, id = "launcher-new-clip",
          callOwner = aZv.class, call = "cfM(Ljava/util/List;LOb;Lbjv;)V", occurrence = 1)
    public static void launcherNewClip(aZv editor, List<?> clips, Ob clip, bjv scroll) {
        aZv target = editor;
        try {
            if (scroll.MRY()) target = openFirst(editor); // MRY(): scroll to the clip and open the editor
        } catch (Throwable failure) {
            MOD.log("Could not open the Detail Editor first; the new clip opens as Bitwig opens it.", failure);
            target = editor;
        }
        // Bitwig's own hand-over; it opens the editor again, which leaves an open editor as it is
        target.cfM(clips, clip, scroll);
    }

    /**
     * Opens the Detail Editor as Bitwig does for a clip (aBW.jHi(), which Streamlined Views may
     * turn into the Edit view) and returns the Detail Editor showing now, or the one Bitwig chose
     * when the view has none.
     */
    private static aZv openFirst(aZv editor) {
        aBW window = editor.JJY(); // the window the editor belongs to
        window.jHi(); // open the Detail Editor
        aZv showing = window.kTh(); // the Detail Editor of the view showing now
        return showing == null ? editor : showing;
    }
}
