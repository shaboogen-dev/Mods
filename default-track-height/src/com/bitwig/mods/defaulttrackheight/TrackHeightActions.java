package com.bitwig.mods.defaulttrackheight;

import bitwig.obf.aBF; // a project's window state
import bitwig.obf.aCp; // a project window
import bitwig.obf.Hea; // a track's or group's view state: its lane height
import bitwig.obf.xJo; // a track
import bml.actions.ActionProvider;
import bml.actions.ActionContext;
import bml.actions.Actions;
import bml.api.Bml;
import com.bitwig.flt.document.core.iface.pHd; // a project
import com.bitwig.flt.document.core.master.IOJ; // an undoable change
import com.bitwig.flt.document.core.master.g2L; // a project's document
import com.bitwig.flt.document.core.master.ruS; // a group of tracks
import com.bitwig.flt.document.core.master.zwV; // a track or group

import java.util.ArrayList;
import java.util.List;

/**
 * "Set All Tracks to Default Height": writes the setting's value to every instrument, audio, hybrid,
 * effect and group track of the project, nested ones included. The master keeps its height. Written
 * to the display profile in use, where a track's height lives. Bitwig's Undo history doesn't take
 * this, as with a track resized by dragging.
 */
public final class TrackHeightActions implements ActionProvider {
    private static final String LABEL = "Set All Tracks to Default Height";
    private static final Bml.Mod MOD = Bml.mod("default-track-height");

    @Override
    public void declare(Actions actions) {
        // The ID keeps the name it was released under: Bitwig stores it with the user's shortcuts.
        actions.action("compact_expanded_groups_set_all_tracks_height", LABEL)
               .onRun(TrackHeightActions::run);
    }

    private static void run(ActionContext context) {
        try {
            ruS tracks = mainTracks(context.widget());
            if (tracks == null) {
                MOD.log(LABEL + ": no project here");
                return;
            }
            setAll(tracks);
        } catch (Throwable failure) {
            MOD.log(LABEL + " failed", failure);
        }
    }

    private static ruS mainTracks(Object widget) {
        aBF state = widget instanceof aCp window ? window.iAj() : null; // a shortcut and the Commander run from the project window
        pHd project = state == null ? null : state.hUR();
        return project instanceof g2L document ? document.spd() : null;
    }

    private static void setAll(ruS root) {
        int height = DefaultTrackHeight.height();
        List<Hea> different = new ArrayList<>();
        collect(root, height, different);
        if (different.isEmpty()) return;
        IOJ change = new IOJ();
        if (!change.cfM(root, LABEL)) { // begin
            MOD.log(LABEL + ": Bitwig refused to start the change");
            return;
        }
        try {
            for (Hea view : different) {
                view.MRY(height);
            }
        } finally {
            change.MRY(); // finish
        }
    }

    /** As Bitwig walks a group (ruS.IwV), without each group's own master track (Lqt). */
    private static void collect(ruS group, int height, List<Hea> into) {
        for (Object child : group.uva()) { // its tracks and groups
            if (child instanceof xJo track) {
                add(track.yAk(), height, into);
            } else if (child instanceof ruS nested) {
                add(nested.SFz(), height, into);
                collect(nested, height, into);
            }
        }
        for (Object child : group.Guv()) { // its effect tracks
            if (child instanceof xJo track) add(track.yAk(), height, into);
        }
    }

    private static void add(Hea view, int height, List<Hea> into) {
        if (view.Ucl() != height) into.add(view);
    }
}
