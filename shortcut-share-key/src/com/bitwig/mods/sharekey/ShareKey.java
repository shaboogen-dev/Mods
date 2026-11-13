package com.bitwig.mods.sharekey;

import bitwig.obf.dAa; // a dialog button
import bitwig.obf.dQU; // the dialog that assigns a shortcut to an action
import bitwig.obf.dRK; // an action's row in a shortcut table
import bitwig.obf.dRm; // an action's row in the keyboard shortcut table
import bitwig.obf.dRv; // one shortcut
import bitwig.obf.dpN; // Bitwig's application
import bitwig.obf.dtY; // the ways a dialog can close
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.This;

/**
 * The "Add" button of the shortcut dialog, for any action whose pressed key is taken: it gives the
 * action the key and leaves every other action as it is. It uses the dialog's spare Continue button,
 * which Bitwig builds in every dialog and places by platform with the others.
 */
public final class ShareKey {
    private static final Bml.Mod LOG = Bml.mod("shortcut-share-key");

    private ShareKey() {
    }

    /** The shortcut dialog has set its buttons for the key pressed (or for none yet). */
    @Hook(target = dQU.class, method = "Ktv()V", at = At.BEFORE_RETURN, id = "shortcut-dialog-buttons")
    public static void shortcutDialogButtons(@This dQU dialog) {
        try {
            update(dialog);
        } catch (Throwable t) {
            LOG.log("shortcut dialog failed", t);
        }
    }

    private static void update(dQU dialog) {
        // Controller shortcuts keep the dialog as it is; so does an action with no conflict.
        if (!(dialog.hUR instanceof dRm)) return; // hUR: the row being edited
        boolean conflict = dialog.UNj != null; // UNj: a shortcut of another action on the pressed key
        dialog.wZs(conflict); // show or hide the Continue button
        if (!conflict) return;
        dAa button = dialog.dnz; // the Continue button
        button.Vr("add_shortcut.add"); // its label: Bitwig's own text, as this dialog's OK button uses it
        button.Hnt((Runnable) null); // Bitwig's button refuses a new callback over an old one
        button.Hnt(() -> add(dialog)); // its callback
    }

    /** What Bitwig's OK does, without first taking the key from the actions that have it. */
    private static void add(dQU dialog) {
        try {
            dRK row = dialog.hUR;
            dRv shortcut = dialog.IwV; // the pressed key
            if (shortcut == null) return;
            row.cfM(shortcut); // add it to the row
            dpN.TVZ().cfM(row, shortcut); // tell the application
            dialog.cfM(dtY.cfM.ordinal()); // close as OK
        } catch (Throwable t) {
            LOG.log("adding a shortcut failed", t);
        }
    }
}
