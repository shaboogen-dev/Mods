package com.bitwig.hacks;

import bitwig.obf.cFV; // the docked Side Browser panel
import bitwig.obf.dve; // a widget
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.This;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * One action: focus the search field of the docked Side Browser in the window the action runs in.
 * While a browser view with its own content shows there (Framework: Browser Configuration), the
 * view's own search field when it declared one ({@link ViewSearchField}). It never toggles the
 * browser or changes the search query.
 */
public final class FocusSideBrowserSearch {
    static final String ACTION_ID = "bitwig_re_focus_side_browser_search";
    static final String LABEL = "Focus Side Browser Search Bar";
    private static final Bml.Mod MOD = Bml.mod("focus-side-browser-search");
    private static final Object LOCK = new Object();
    private static final List<WeakReference<cFV>> SIDE_BROWSERS = new ArrayList<>();

    private FocusSideBrowserSearch() { }

    /** A docked Side Browser is being set up: remember it (weakly); no UI access until the action runs. */
    @Hook(target = cFV.class, method = "MRY(Lcom/bitwig/flt/document/core/iface/pHd;LJp;Lcom/bitwig/flt/document_view_settings/hgq;)V",
          at = At.ENTRY, id = "capture-docked-browser")
    public static void rememberDockedBrowser(@This cFV sideBrowser) {
        if (sideBrowser == null) return;
        synchronized (LOCK) {
            for (int i = SIDE_BROWSERS.size() - 1; i >= 0; --i) {
                cFV existing = SIDE_BROWSERS.get(i).get();
                if (existing == null || existing == sideBrowser) SIDE_BROWSERS.remove(i);
            }
            SIDE_BROWSERS.add(new WeakReference<>(sideBrowser));
        }
    }

    /** The action: focus the search field of a docked Side Browser shown in this project window. */
    static void focus(Object projectWindow) {
        if (!(projectWindow instanceof dve window)) return;
        try {
            List<cFV> snapshots = new ArrayList<>();
            synchronized (LOCK) {
                for (int i = SIDE_BROWSERS.size() - 1; i >= 0; --i) {
                    cFV target = SIDE_BROWSERS.get(i).get();
                    if (target == null) SIDE_BROWSERS.remove(i);
                    else snapshots.add(target); // newest candidates first
                }
            }
            for (cFV sideBrowser : snapshots) {
                if (!sideBrowser.ADn()) continue; // not in a valid display hierarchy
                if (!sideBrowser.Pfd()) continue; // not shown: the browser is hidden, so do nothing
                if (!isDescendantOfWindow(sideBrowser, window)) continue;
                if (ViewSearchField.focus(sideBrowser)) return; // a browser view's own field, when one shows
                sideBrowser.Ktv(); // focus its own search field; no panel toggle
                return;
            }
            // No valid docked instance for this window: fail closed without changing UI.
        } catch (Throwable failure) {
            MOD.log("FOCUS_ERROR", failure);
        }
    }

    private static boolean isDescendantOfWindow(dve child, dve window) {
        dve cursor = child;
        // Identity comparison only: do not confuse distinct project windows or display roots.
        for (int hops = 0; cursor != null && hops < 256; hops++) {
            if (cursor == window) return true;
            dve next = cursor.RbC(); // the parent widget
            if (next == cursor) return false;
            cursor = next;
        }
        return false;
    }
}
