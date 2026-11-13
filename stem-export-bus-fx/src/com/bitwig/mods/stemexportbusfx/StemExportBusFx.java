package com.bitwig.mods.stemexportbusfx;

import bitwig.obf.Yhp; // the render settings an export task runs with (formats, tap point, real-time)
import bitwig.obf.cJp; // the Export Audio dialog
import bitwig.obf.dWb; // a vertical or horizontal group of widgets
import bitwig.obf.dve; // a widget
import bitwig.obf.dzv; // a checkbox
import bitwig.obf.kd; // the Export Audio task (one render target per exported track)
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.This;
import com.bitwig.flt.document.core.master.g2L; // the project document

import java.util.List;

/**
 * Export: Stems with Bus Effects. The helper class: the three hooks and the option.
 *
 * Bitwig's Export Audio renders every selected track in one pass, each from its own output,
 * so a stem has none of the effects on the groups, effect tracks and master after it.
 * With the option on, the mod renders the stems one after another instead: for each, it
 * solos that track (and nothing else) and renders the master output to the stem's file,
 * then puts every solo back as it was.
 */
public final class StemExportBusFx {
    static final Bml.Mod MOD = Bml.mod("stem-export-bus-fx");

    static final String LABEL = "Stems include group, return and master effects";

    private static final bml.api.Settings STORE = MOD.settings();
    private static final String KEY_ENABLED = "include_bus_effects";

    private static volatile boolean enabled = readEnabled();

    /** The export request seen at the start of the document's export method, for the start hook below. */
    private static final ThreadLocal<Request> REQUEST = new ThreadLocal<>();

    private StemExportBusFx() {
    }

    static boolean enabled() {
        return enabled;
    }

    static void setEnabled(boolean value) {
        enabled = value;
        try {
            STORE.putBoolean(KEY_ENABLED, value);
        } catch (Throwable t) {
            MOD.log("could not save the option", t);
        }
    }

    private static boolean readEnabled() {
        try {
            return STORE.getBoolean(KEY_ENABLED, false);
        } catch (Throwable t) {
            MOD.log("could not read the option", t);
            return false;
        }
    }

    /**
     * Export Audio dialog: the call that adds Bitwig's last option checkbox (open the folder when
     * done) to the Options column. Adds that checkbox, then the mod's own below it.
     */
    @Hook(id = "add-dialog-option", target = cJp.class, method = "MRY(Lcom/bitwig/flt/document/core/iface/pHd;)V",
          at = At.REPLACE_CALL, callOwner = dWb.class, call = "HL(Ldve;)V", occurrence = 15)
    public static void addOption(dWb options, dve widget) {
        options.HL(widget); // what Bitwig adds here
        if (!(widget instanceof dzv)) { // not the options checkbox: the dialog has changed, add nothing
            MOD.log("Export Audio dialog layout not recognised; option not shown");
            return;
        }
        try {
            options.HL(dzv.cfM(LABEL, new OptionValue(enabled))); // a checkbox with its label
        } catch (Throwable t) {
            MOD.log("could not add the option to the Export Audio dialog", t);
        }
    }

    /** Document: note what is being exported (tracks, files, WAV or not, time range). */
    @Hook(id = "note-export-request", target = g2L.class,
          method = "MRY(Ljava/util/List;[Ljava/lang/String;ZZLbuR;ZLoXh;ZLpME;ZLoBq;DDLgx3;LRN1;ZZI)V",
          at = At.ENTRY)
    public static void noteRequest(@This g2L document, @Arg(0) List tracks, @Arg(1) String[] paths,
                                   @Arg(2) boolean wave, @Arg(11) double from, @Arg(12) double to) {
        try {
            REQUEST.set(new Request(document, tracks, paths, wave, from, to));
        } catch (Throwable t) {
            REQUEST.remove();
        }
    }

    /**
     * Document: the call that starts the Export Audio task. With the option off (or nothing to
     * do per stem), it starts as in Bitwig. With it on, the mod's stem queue runs instead.
     */
    @Hook(id = "start-export", target = g2L.class,
          method = "MRY(Ljava/util/List;[Ljava/lang/String;ZZLbuR;ZLoXh;ZLpME;ZLoBq;DDLgx3;LRN1;ZZI)V",
          at = At.REPLACE_CALL, callOwner = kd.class, call = "MRY(LYhp;)V", occurrence = 1)
    public static void startExport(kd task, Yhp settings) {
        Request request = REQUEST.get();
        REQUEST.remove();
        StemQueue queue = null;
        if (enabled && request != null) {
            try {
                queue = StemQueue.plan(request);
            } catch (Throwable t) {
                MOD.log("could not plan the stem export; exporting as Bitwig does", t);
                queue = null;
            }
        }
        if (queue == null) {
            task.MRY(settings); // Bitwig's own export
            return;
        }
        queue.start(settings);
    }

    /** What the document was asked to export. */
    record Request(g2L document, List tracks, String[] paths, boolean wave, double from, double to) {
    }
}
