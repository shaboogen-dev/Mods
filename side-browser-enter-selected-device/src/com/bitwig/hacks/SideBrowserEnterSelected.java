package com.bitwig.hacks;

import bitwig.obf.FAu; // a device chain
import bitwig.obf.IkS; // files to insert: what a drag from the browser carries
import bitwig.obf.EqN; // a track's selected devices
import bitwig.obf.Jp;  // the browser's session
import bitwig.obf.aBW; // the window; also holds Bitwig's Launch action
import bitwig.obf.aYz; // the Device Panel's view
import bitwig.obf.ajS; // the Device Panel
import bitwig.obf.akw; // a panel
import bitwig.obf.an;  // an audio file's type
import bitwig.obf.arb; // an impulse file's type
import bitwig.obf.cCk; // the browser panel (base of the docked Side Browser)
import bitwig.obf.cFV; // the docked Side Browser panel
import bitwig.obf.cFt; // the browser's search field wrapper
import bitwig.obf.cFq; // the browser's result view
import bitwig.obf.dCS; // a list widget (the browser's result list)
import bitwig.obf.dmX; // an action
import bitwig.obf.dpl; // the keyboard event dispatcher
import bitwig.obf.dub; // holds Bitwig's Activate action
import bitwig.obf.ecH; // the inner text editor of a text field
import bitwig.obf.ecz; // a text field
import bitwig.obf.ivQ; // what a drag's session is stored under
import bitwig.obf.j2e; // an insert, ready to run
import bitwig.obf.jsd; // a device
import bitwig.obf.pL;  // a modulator file's type
import bitwig.obf.uGG; // where a drop lands
import bitwig.obf.eoR; // a keyboard event
import bitwig.obf.wt;  // a browser result
import com.bitwig.base.async.lBQ; // a completion listener
import com.bitwig.base.io.z2y; // a file path
import com.bitwig.flt.document.core.iface.RYd; // what the Device Panel shows
import com.bitwig.flt.document.core.iface.clipboard.device.DeviceChainInsertionPoint; // a place in a chain
import com.bitwig.flt.document.core.iface.clipboard.device.IntelligentDeviceInsertionPoint; // a chain, no position
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.This;

import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * In the docked Side Browser:
 * 1. Bitwig's Activate and Launch actions (Enter) on the result list insert the selected result.
 *    A result goes in as a drop on the track would; what a drop refuses keeps Bitwig's activation.
 *    Several selected samples go in together, as a drag of them does.
 * 2. Enter in the search field inserts the first result, just before Bitwig's own text commit runs;
 *    only a commit made from the keyboard counts, so clicking away from the field inserts nothing.
 */
public final class SideBrowserEnterSelected {
    private static final Bml.Mod MOD = Bml.mod("side-browser-enter-selected-device");
    /** The keyboard dispatcher's one method taking a keyboard event: dispatching a key press. */
    private static final MethodType KEY_DISPATCH = MethodType.methodType(void.class, eoR.class);
    private static final Map<Object, Boolean> LIST_INSTALLED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, SearchRunnable> SEARCH_INSTALLED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private SideBrowserEnterSelected() {}

    /** A docked Side Browser is being set up: bind Enter on its result list and register its search editor. */
    @Hook(target = cFV.class, method = "iA_", at = At.ENTRY, id = "bind-activate-to-docked-result-list")
    public static void bindBrowserListActivate(@This cFV sideBrowser) {
        if (sideBrowser == null) return;
        try {
            // Read both fields through cCk: cFV has a static field of the same name as the search field.
            cCk panel = sideBrowser;
            cFq resultView = panel.lYc; // the result view
            if (resultView == null) {
                log("SKIP: Browser result view unavailable");
                return;
            }
            dCS list = resultView.luv(); // the result list
            if (list == null) {
                log("SKIP: native Browser result list unavailable");
                return;
            }

            dmX activate = dub.ucQ; // Bitwig's Activate action
            dmX launch = aBW.gG;    // Bitwig's Launch action
            if (activate == null || launch == null || activate == launch) {
                throw new IllegalStateException("native action identities invalid");
            }

            synchronized (LIST_INSTALLED) {
                if (!LIST_INSTALLED.containsKey(list)) {
                    Runnable callback = new ActivateRunnable(list);
                    list.cfM(activate, callback); // bind the action on the list
                    list.cfM(launch, callback);
                    LIST_INSTALLED.put(list, Boolean.TRUE);
                    log("BOUND_LIST: native Activate + Launch on Browser results");
                }
            }

            cFt searchWrapper = panel.LaE; // the search field
            if (searchWrapper == null) {
                log("SEARCH_SKIPPED: Browser search field unavailable");
                return;
            }
            ecz textWidget = searchWrapper.ifN(); // its text field
            if (textWidget == null) {
                log("SEARCH_SKIPPED: native text widget unavailable");
                return;
            }

            // The keyboard-focused editor is the text field's inner editor; Bitwig's Enter
            // commits its text through it (and then moves focus to the results).
            ecH innerEditor = textWidget.xlY(); // the inner editor
            if (innerEditor == null) {
                log("SEARCH_SKIPPED: inner editor unavailable");
                return;
            }

            synchronized (SEARCH_INSTALLED) {
                if (SEARCH_INSTALLED.containsKey(innerEditor)) return;
                SEARCH_INSTALLED.put(innerEditor, new SearchRunnable(list));
                log("WIRED_INNER_EDITOR: scoped Stage 2 text-commit bridge");
            }
        } catch (Throwable problem) {
            log("BIND_ERROR: " + problem.getClass().getName() + ": "
                    + problem.getMessage());
        }
    }

    /**
     * Before an inner text editor commits its text. Only the docked Side Browser's search editor
     * is registered, and only a commit made while a key press is being dispatched counts: focus
     * loss from the mouse must not insert anything. Bitwig's own commit runs afterwards.
     */
    @Hook(target = ecH.class, method = "LzO", at = At.ENTRY, id = "stage2-inner-editor-keyboard-commit")
    public static void onInnerEditorCommit(@This ecH editor) {
        if (editor == null) return;
        SearchRunnable action;
        synchronized (SEARCH_INSTALLED) {
            action = SEARCH_INSTALLED.get(editor);
        }
        if (action == null) return;
        boolean isKeyboardDispatch;
        try {
            isKeyboardDispatch = StackWalker.getInstance(
                    StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames ->
                    frames.anyMatch(frame ->
                            frame.getDeclaringClass() == dpl.class
                            && KEY_DISPATCH.equals(frame.getMethodType())));
        } catch (RuntimeException problem) {
            // Fail closed: a stack-inspection failure must never
            // propagate through Bitwig's text commit.
            log("KEYBOARD_GUARD_ERROR: " + problem.getClass().getName()
                    + ": " + problem.getMessage());
            return;
        }
        if (!isKeyboardDispatch) {
            log("SKIP_INNER_COMMIT_NON_KEYBOARD");
            return;
        }
        log("INNER_COMMIT_KEYBOARD: running first-result native activation");
        action.run();
    }

    private static void log(String s) {
        MOD.log(s);
    }

    /**
     * Does what a drop of the file does, at Bitwig's own position for a drop without one: the same data, the
     * same choice of device and the same insert, on the chain Enter on a device uses. False if a drop is refused.
     */
    private static boolean insertAsDrop(dCS list, z2y file) {
        return insertAsDrop(list, List.of(file));
    }

    /** The same for one drop of these files, whose first file decides the kind. */
    private static boolean insertAsDrop(dCS list, List<z2y> files) {
        z2y file = files.get(0);
        if (file.MRY() == null) return false; // not a known file type
        aBW window = (aBW) list.TMF(aBW.class);
        ajS panel = window == null ? null : (ajS) window.cfM(ajS.class);
        aYz view = panel == null ? null : (aYz) window.MRY(panel);
        RYd shown = view == null ? null : view.luv();
        if (shown == null) return false;
        FAu chain = shown.Ucl();
        if (chain == null) return false; // no chain shown: Bitwig's own activation instead
        if (chain.UNj() || chain.E3() != null) return false; // the Device Panel takes no drop on these
        IkS data = new IkS(files, dragDetails(list));
        uGG point = file.MRY() instanceof pL ? ontoSelectedDevice(shown) : new IntelligentDeviceInsertionPoint(chain);
        if (point == null) {
            log("MODULATOR_SKIP: no selected device that takes modulators: " + file);
            return false;
        }
        j2e drop = point.cfM(data, point.UNj(data)); // the options of a drop with no keys held
        if (drop == null) return false;
        window.cfM((akw) panel); // bring the Device Panel forward
        String what = files.size() == 1 ? file.toString() : files.size() + " samples";
        drop.MRY(new Done(what));
        log("DROP: " + what);
        return true;
    }

    /**
     * The files of the rows selected in the list, in row order, if several are selected, the given row is one
     * of them and all are samples. Otherwise null. An impulse doesn't count: a drop refuses several with one.
     */
    private static List<z2y> selectedSamples(dCS list, Object row) {
        if (!(list.d3().TgI().getValue() instanceof List<?> selected)) return null; // TgI(): the selected rows
        if (selected.size() < 2 || !selected.contains(row)) return null;
        Set<Object> wanted = new HashSet<>(selected);
        List<z2y> files = new ArrayList<>();
        for (int i = 0, count = list.FFu(); i < count; i++) {
            Object item = list.Hnt(i);
            if (!wanted.contains(item)) continue;
            z2y file = item instanceof wt result ? result.tp2() : null;
            if (file == null || !(file.MRY() instanceof an) || file.MRY() instanceof arb) return null;
            files.add(file);
        }
        return files.size() == wanted.size() ? files : null;
    }

    /** A drop on the face of the track's selected device: Bitwig sends a modulator to that device's modulators. */
    private static uGG ontoSelectedDevice(RYd shown) {
        EqN selection = shown.tp2().fFM().wGc();
        jsd device = selection.cfM();
        if (device == null || device.tSS() == null) return null;
        FAu chain = device.xL();
        return new DeviceChainInsertionPoint(chain, chain.cfM(device), true, true, selection);
    }

    /** A drag from the browser carries the browser's session. */
    private static Map<Class<?>, Jp> dragDetails(dCS list) {
        cFV browser = (cFV) list.TMF(cFV.class);
        Jp session = browser == null ? null : browser.EUG();
        return session == null ? Map.of() : Map.of(ivQ.class, session.IwV(false));
    }

    /** Logs how an insert ended. */
    private static final class Done implements lBQ {
        private final String what;

        Done(String what) {
            this.what = what;
        }

        @Override public void taskCompleted() {
            log("DROP_DONE: " + what);
        }

        @Override public void cfM(Exception problem) {
            log("DROP_ERROR: " + what + ": " + problem);
        }
    }

    /** Enter on the result list: Bitwig's own activation of the selected result. */
    private static final class ActivateRunnable implements Runnable {
        private final dCS list;
        ActivateRunnable(dCS list) {
            this.list = list;
        }
        @Override public void run() {
            try {
                Object selected = list.d3().Zyp(); // the item the activation below would use
                if (selected instanceof wt result && result.tp2() != null) {
                    List<z2y> several = selectedSamples(list, selected);
                    if (several != null ? insertAsDrop(list, several) : insertAsDrop(list, result.tp2())) return;
                }
                list.ELS(); // activate the selected item
                log("ACTIVATE: native selected-result activation");
            } catch (Throwable problem) {
                log("ACTIVATE_ERROR: " + problem.getClass().getName()
                        + ": " + problem.getMessage());
            }
        }
    }

    /**
     * Enter in the search field: activates the first visible result that has a resource (a file
     * or a Bitwig content item). Resource-bearing is not a device-only filter: the browser
     * decides what it supports. No synthetic input, no focus shift, no selection change.
     */
    private static final class SearchRunnable implements Runnable {
        private final dCS list;

        SearchRunnable(dCS list) {
            this.list = list;
        }

        @Override public void run() {
            try {
                int count = list.FFu(); // number of items
                if (count <= 0) {
                    log("SEARCH_NO_RESULT: no visible results");
                    return;
                }
                // Bound scan for pathological models, without choosing an
                // offscreen/stale object when no eligible item was found.
                int max = Math.min(count, 1024);
                for (int i = 0; i < max; i++) {
                    Object item = list.Hnt(i); // the item at i
                    if (!(item instanceof wt result)) continue;
                    if (result.tp2() == null) continue; // its resource
                    if (!insertAsDrop(list, result.tp2())) list.nVd(item); // activate this item
                    log("SEARCH_ACTIVATE: result_index=" + i +
                            " result_type=" + item.getClass().getName());
                    return;
                }
                log("SEARCH_NO_ACTIONABLE_RESULT: results=" + count);
            } catch (Throwable problem) {
                log("SEARCH_ERROR: " + problem.getClass().getName()
                        + ": " + problem.getMessage());
            }
        }
    }
}
