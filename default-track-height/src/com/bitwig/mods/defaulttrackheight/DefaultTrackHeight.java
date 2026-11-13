package com.bitwig.mods.defaulttrackheight;

import bitwig.obf.FTq; // a track's view state: its lane height
import bitwig.obf.hG; // the new track actions
import bitwig.obf.xJo; // a track
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.Return;
import bml.api.Settings;
import com.bitwig.flt.document.core.master.ruS; // a group of tracks
import com.bitwig.flt.document.core.master.zwV; // a track or group

/**
 * The height new tracks and group tracks start at. It only touches one at the moment Bitwig creates
 * it from nothing, and writes its own lane height, which the project saves as usual. Duplicates,
 * track presets, templates and opened projects are read from saved data and never pass here.
 */
public final class DefaultTrackHeight {
    static final int MIN = 21, MAX = 600; // Bitwig's own limits when a track is dragged
    private static final int BITWIG_START = 43;
    private static final Bml.Mod MOD = Bml.mod("default-track-height");
    private static final Settings STORE = MOD.settings();
    private static final String HEIGHT_KEY = "default_track_height";

    private static volatile int height = clamp(STORE.getInt(HEIGHT_KEY, BITWIG_START));

    private DefaultTrackHeight() {
    }

    // ---------------------------------------------------------------------
    // Hooks
    // ---------------------------------------------------------------------

    /** The new track actions: the call that sets the new track's lane height. */
    @Hook(id = "new-track-height", target = hG.class, method = "MRY(LfP;)V", at = At.REPLACE_CALL,
          callOwner = FTq.class, call = "MRY(I)V", occurrence = 1)
    public static void newTrackHeight(final FTq trackView, final int bitwigHeight) {
        trackView.MRY(height);
    }

    /** Every other new track of a chosen type (a device dragged in, the controller API). */
    @Hook(id = "created-track-height", target = ruS.class, method = "MRY(LfP;)LxJo;", at = At.WRAP_RETURN)
    public static xJo createdTrackHeight(@Return final xJo track) {
        apply(track);
        return track;
    }

    /** A new effect track: its own route, with the height set once it is in its group. */
    @Hook(id = "created-effect-track-height", target = ruS.class, method = "cfM(ILBg1;)V", at = At.REPLACE_CALL,
          callOwner = ruS.class, call = "cfM(LxJo;I)V", occurrence = 1)
    public static void addEffectTrack(final ruS group, final xJo track, final int index) {
        group.cfM(track, index);
        apply(track);
    }

    /** A new group track, once it is in its parent. */
    @Hook(id = "new-group-height", target = ruS.class, method = "MRY(ILBg1;)V", at = At.REPLACE_CALL,
          callOwner = ruS.class, call = "cfM(Lcom/bitwig/flt/document/core/master/zwV;I)V", occurrence = 1)
    public static void addNewGroup(final ruS parent, final zwV child, final int index) {
        parent.cfM(child, index);
        if (child instanceof ruS group) apply(group);
    }

    /** The group made by grouping the selected tracks. */
    @Hook(id = "grouped-tracks-height", target = ruS.class, method = "iHb(Ljava/util/List;)V", at = At.REPLACE_CALL,
          callOwner = ruS.class, call = "cfM(Lcom/bitwig/flt/document/core/master/zwV;I)V", occurrence = 1)
    public static void addGroupOfTracks(final ruS parent, final zwV child, final int index) {
        addNewGroup(parent, child, index);
    }

    // ---------------------------------------------------------------------
    // The setting
    // ---------------------------------------------------------------------

    static int height() {
        return height;
    }

    static void setHeight(double proposed) {
        height = clamp((int) Math.round(proposed));
        STORE.putInt(HEIGHT_KEY, height);
    }

    /** Called once the track is in its group, as Bitwig's own new track action sets the height. */
    private static void apply(xJo track) {
        if (track == null) return;
        try {
            track.yAk().MRY(height);
        } catch (Throwable failure) {
            MOD.log("The new track keeps Bitwig's height", failure);
        }
    }

    /** Called once the group is in its parent: its view state needs the project. */
    private static void apply(ruS group) {
        if (group == null) return;
        try {
            group.SFz().MRY(height);
        } catch (Throwable failure) {
            MOD.log("The new group track keeps Bitwig's height", failure);
        }
    }

    private static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }
}
