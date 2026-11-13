package com.bitwig.mods.commanderfirstresult;

import bitwig.obf.cNz; // the Commander panel
import bitwig.obf.cNB; // the Commander's text-filter factory
import bitwig.obf.cNC; // the listener that runs the selected result when Enter is pressed
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.Return;
import bml.api.This;

/**
 * Commander: Enter Selects First Result.
 *
 * When the Commander query is non-empty and no result is selected, Enter first runs
 * Bitwig's own select-next-result routine, so the native Enter handler executes the
 * first visible result. An explicit selection is left alone.
 */
public final class CommanderFirstResult {
    private static final Bml.Mod MOD = Bml.mod("commander-first-result");
    private static volatile String lastCommanderQuery = "";

    private CommanderFirstResult() {
    }

    /** Remembers the Commander query each time Bitwig builds a filter for it; the filter is returned unchanged. */
    @Hook(target = cNB.class, method = "createFilter", at = At.WRAP_RETURN, id = "capture-commander-query")
    public static Object captureQuery(@Return Object filter, @Arg(0) String query) {
        lastCommanderQuery = query == null ? "" : query;
        return filter;
    }

    /** Before Enter runs the selected result: with a query and no selection, select the first result. */
    @Hook(target = cNC.class, method = "eventOccurred", at = At.ENTRY, id = "enter-select-first-result")
    public static void ensureFirstSelection(@This cNC listener) {
        if (lastCommanderQuery.isBlank()) {
            return;
        }
        try {
            cNz commander = listener.cfM; // the panel this listener belongs to
            if (commander == null || commander.wGc != null) { // the selected result
                return;
            }
            commander.MYI(); // select the next result, i.e. the first one
        } catch (Throwable t) {
            MOD.log("COMMANDER_FIRST_RESULT_ERROR", t);
        }
    }
}
