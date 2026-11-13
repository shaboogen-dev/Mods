package com.bitwig.mods.pluginundo;

import java.util.Objects;

import bitwig.obf.aWv; // a plug-in's undo state
import bitwig.obf.dfI; // the document's undo recorder
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.Settings;
import com.bitwig.flt.document.core.master.device.HSu; // a device in the document
import com.bitwig.flt.document.core.master.device.jLS; // a plug-in's undo state, as the device stores it
import com.bitwig.ramona.core.IOJ; // a change recorded with an undo step

/**
 * Device: Optional Plug-in Undo Steps.
 *
 * On: Bitwig's behaviour, every change a plug-in reports becomes a step in the document's Undo
 * history. Off: the device still keeps its plug-in undo state up to date, but no recording or
 * Undo step is made for it.
 *
 * Each hook replaces one call in the device's plug-in undo handling: the recording scope's begin
 * and end, and in the commit the history step and the two state changes. On, each runs Bitwig's
 * call; off, the recorder and history calls are skipped and the two states are written directly,
 * followed by the same change notification Bitwig's own setter makes.
 */
public final class PluginUndo {
    private static final Bml.Mod MOD = Bml.mod("plugin-undo");
    private static final Settings STORE = MOD.settings();
    private static final String KEY = "vst_clap_plugin_undo";

    private static volatile boolean enabled = readEnabled();

    private PluginUndo() {
    }

    /** The setting "Plug-in changes make Undo steps" (default off). */
    static boolean enabled() {
        return enabled;
    }

    static void setEnabled(boolean value) {
        enabled = value;
        STORE.putBoolean(KEY, value);
    }

    private static boolean readEnabled() {
        return STORE.getBoolean(KEY, false);
    }

    /** The plug-in's recording scope begins. */
    @Hook(target = HSu.class, method = "kIh", at = At.REPLACE_CALL, id = "recorder-begin",
          callOwner = dfI.class, call = "qoU", occurrence = 1)
    public static void recorderBegin(dfI recorder) {
        if (enabled && recorder != null) recorder.qoU(); // begin recording
    }

    /** The plug-in's recording scope ends. */
    @Hook(target = HSu.class, method = "MYI", at = At.REPLACE_CALL, id = "recorder-end",
          callOwner = dfI.class, call = "LaE", occurrence = 1)
    public static void recorderEnd(dfI recorder) {
        if (enabled && recorder != null) recorder.LaE(); // end recording
    }

    /** The commit's own end of the recording scope. */
    @Hook(target = HSu.class, method = "cfM(Ljava/lang/String;II)V", at = At.REPLACE_CALL, id = "commit-recorder-end",
          callOwner = dfI.class, call = "LaE", occurrence = 1)
    public static void commitRecorderEnd(dfI recorder) {
        if (enabled && recorder != null) recorder.LaE(); // end recording
    }

    /** The commit begins an Undo step named after the plug-in's change. */
    @Hook(target = HSu.class, method = "cfM(Ljava/lang/String;II)V", at = At.REPLACE_CALL, id = "history-begin",
          callOwner = HSu.class, call = "cfM(Ljava/lang/String;[Lcom/bitwig/ramona/core/IOJ;)Z", occurrence = 1)
    public static boolean historyBegin(HSu device, String name, IOJ[] changes) {
        return enabled && device.cfM(name, changes); // begin the Undo step
    }

    /** The commit sets the plug-in's undo state. */
    @Hook(target = HSu.class, method = "cfM(Ljava/lang/String;II)V", at = At.REPLACE_CALL, id = "undo-state-raw",
          callOwner = HSu.class, call = "cfM(LaWv;)V", occurrence = 1)
    public static void setUndoState(HSu device, aWv next) {
        if (enabled) {
            device.cfM(next); // Bitwig's setter: records the change
            return;
        }
        jLS old = device.dEI; // the stored undo state
        if (Objects.equals(old, next)) return;
        device.dEI = (jLS) next;
        device.cfM(old); // the setter's change notification, with the previous state
    }

    /** The commit marks the plug-in state as changed since the last save. */
    @Hook(target = HSu.class, method = "cfM(Ljava/lang/String;II)V", at = At.REPLACE_CALL, id = "undo-recent-raw",
          callOwner = HSu.class, call = "xL", occurrence = 1)
    public static void setUndoRecent(HSu device, boolean next) {
        if (enabled) {
            device.xL(next); // Bitwig's setter: records the change
            return;
        }
        boolean old = device.Z1C; // changed since the last save
        if (old == next) return;
        device.Z1C = next;
        device.TMF(old); // the setter's change notification, with the previous value
    }

    /** The commit finishes the Undo step. */
    @Hook(target = HSu.class, method = "cfM(Ljava/lang/String;II)V", at = At.REPLACE_CALL, id = "history-finish",
          callOwner = HSu.class, call = "RpC", occurrence = 1)
    public static boolean historyFinish(HSu device) {
        return enabled && device.RpC(); // finish the Undo step
    }
}
