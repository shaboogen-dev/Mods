package com.bitwig.mods.commanderdispatchfix;

import bitwig.obf.cNz; // the Commander panel
import bitwig.obf.dmQ; // a Commander result
import bitwig.obf.dve; // what running a result returns
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;

/**
 * Commander: Run Order Crash Fix.
 *
 * Bitwig's Commander closes itself before it runs the chosen action. Closing first
 * leaves some actions (Cut Time in the arranger) acting on a view that is already
 * being torn down, and Bitwig can crash. This mod runs the action first and closes
 * the Commander afterwards.
 */
public final class CommanderDispatchFix {
    private static final Bml.Mod MOD = Bml.mod("commander-dispatch-fix");

    private CommanderDispatchFix() {
    }

    /** The Commander's early close: skipped, it runs after the action instead. */
    @Hook(target = cNz.class, method = "MRY(LdmQ;)V", at = At.REPLACE_CALL, id = "skip-early-dismiss",
          callOwner = cNz.class, call = "gfw", occurrence = 1)
    public static void skipEarlyDismiss(cNz commander) {
    }

    /** Runs the chosen result, then closes the Commander, even if the action fails. */
    @Hook(target = cNz.class, method = "MRY(LdmQ;)V", at = At.REPLACE_CALL, id = "run-action-then-dismiss",
          callOwner = cNz.class, call = "UNj", occurrence = 1)
    public static dve runThenDismiss(cNz commander, dmQ result) {
        try {
            return commander.UNj(result); // run the result's action
        } finally {
            try {
                commander.gfw(); // hide the Commander, restore focus, dispose it
            } catch (Throwable t) {
                MOD.log("closing the Commander failed", t);
            }
        }
    }
}
