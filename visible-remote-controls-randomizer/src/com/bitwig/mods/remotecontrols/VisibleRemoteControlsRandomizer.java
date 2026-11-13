package com.bitwig.mods.remotecontrols;

import bitwig.obf.aYg; // the Inspector panel
import bitwig.obf.bDH; // a parameter
import bitwig.obf.dAd; // an icon button
import bitwig.obf.dmQ; // an action binding: an action and what it runs
import bitwig.obf.dmX; // an action
import bitwig.obf.dpN; // Bitwig's application, which owns the action registry
import bitwig.obf.eoV; // a key modifier
import bitwig.obf.FyM; // a Remote Controls page
import bitwig.obf.jsd; // a device
import bitwig.obf.kbG; // an Inspector selection
import bitwig.obf.NBQ; // what a Remote Controls chooser shows: a device's pages
import bitwig.obf.obZ; // the Inspector's selection of devices
import bitwig.obf.waW; // a Remote Controls slot
import bitwig.obf.z2X; // a parameter that can be set
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.This;
import com.bitwig.flt.document.core.master.device.iKw; // a Remote Controls slot of a device
import com.bitwig.flt.widget.core.device.FUR; // the Remote Controls page chooser
import com.bitwig.flt.widget.core.device.hgq; // a device's panel in the device chain
import com.bitwig.flt.widget.core.device.yPU; // what a page chooser is built from
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Device: Randomise Remote Controls. Randomises the Remote Controls pages that are actually visible.
 *
 * The action "Randomise Visible Remote Controls" is declared through the Action
 * and Menu framework ({@link VisibleRemoteControlsActions}). Ctrl+left-click (Cmd on
 * macOS) on Bitwig's Remote Controls button in a device's panel runs the same
 * operation on that device.
 *
 * The visible-page registry accepts any number of pages, so Configurable Remote
 * Control Pages can publish every page it shows. With stock Bitwig, the page the
 * chooser shows is used.
 */
public final class VisibleRemoteControlsRandomizer {
    static final String ACTION_ID = "bitwig_re_randomize_visible_remote_controls";
    static final String ACTION_LABEL = "Randomise Visible Remote Controls";

    private static final Bml.Mod MOD = Bml.mod("visible-remote-controls-randomizer");

    private static final Map<Object, WeakReference<Object>> CURRENT_PAGE =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, List<WeakReference<Object>>> VISIBLE_PAGES =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile WeakReference<obZ> activeDeviceSelection = new WeakReference<>(null);
    private static final ThreadLocal<hgq> DEVICE_PANEL_BUILD = new ThreadLocal<>();

    private VisibleRemoteControlsRandomizer() { }

    // ---------------------------------------------------------------------
    // Stock chooser tracking + multi-page publication API
    // ---------------------------------------------------------------------

    /** After the page chooser is set up: remember the page it shows. */
    @Hook(target = FUR.class, method = "MRY(Lcom/bitwig/flt/widget/core/device/yPU;)V",
          at = At.BEFORE_RETURN, id = "remember-chooser-after-initialize")
    public static void rememberChooserAfterInitialize(@This FUR chooser) {
        rememberChooser(chooser);
    }

    /** After the chooser shows another page: remember it. */
    @Hook(target = FUR.class, method = "cfM(LFyM;)V",
          at = At.BEFORE_RETURN, id = "remember-chooser-after-page-change")
    public static void rememberChooserAfterPageChange(@This FUR chooser) {
        rememberChooser(chooser);
    }

    private static void rememberChooser(FUR chooser) {
        if (chooser == null) return;
        try {
            Object device = deviceFromChooser(chooser);
            if (device == null) return;
            FyM page = chooser.jFL(); // the page it shows
            synchronized (CURRENT_PAGE) {
                if (page == null) CURRENT_PAGE.remove(device);
                else CURRENT_PAGE.put(device, new WeakReference<>(page));
            }
        } catch (Throwable t) {
            MOD.log("CHOOSER_TRACK_ERROR", t);
        }
    }

    /**
     * Public integration point for a renderer that knows the exact pages it is
     * currently displaying (Configurable Remote Control Pages).
     */
    public static void publishVisiblePagesForDevice(Object device, Object[] pages) {
        if (device == null) return;
        List<WeakReference<Object>> refs = new ArrayList<>();
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        if (pages != null) {
            for (Object page : pages) {
                if (page == null || seen.put(page, Boolean.TRUE) != null) continue;
                refs.add(new WeakReference<>(page));
            }
        }
        synchronized (VISIBLE_PAGES) {
            if (refs.isEmpty()) VISIBLE_PAGES.remove(device);
            else VISIBLE_PAGES.put(device, refs);
        }
    }

    public static void publishVisiblePagesFromChooser(Object chooser, Object[] pages) {
        if (!(chooser instanceof FUR c)) return;
        try {
            publishVisiblePagesForDevice(deviceFromChooser(c), pages);
        } catch (Throwable t) {
            MOD.log("VISIBLE_PAGES_PUBLISH_ERROR", t);
        }
    }

    private static Object deviceFromChooser(FUR chooser) {
        NBQ shown = chooser.BDm(); // the device's pages it shows
        return shown == null ? null : shown.lYc(); // their device
    }

    // ---------------------------------------------------------------------
    // Device Inspector selection, for the shortcut and Commander
    // ---------------------------------------------------------------------

    /**
     * Each time the Inspector shows a new selection: keep it (weakly) when it is a
     * selection of devices, otherwise forget the last one. The selected devices
     * are read when the action runs.
     */
    @Hook(target = aYg.class, method = "FzB(LkbG;)V", at = At.ENTRY, id = "remember-current-device-inspector-selection")
    public static void rememberInspectorSelection(@Arg(0) kbG selection) {
        if (selection instanceof obZ devices) {
            activeDeviceSelection = new WeakReference<>(devices);
        } else {
            activeDeviceSelection = new WeakReference<>(null);
        }
    }

    /**
     * The device the shortcut and Commander act on: the last of the Inspector's
     * selected devices, which is the one Bitwig's own device actions use when
     * several devices on a track are selected.
     */
    private static Object resolveSelectedDevice() {
        obZ selection = activeDeviceSelection.get();
        if (selection == null) return null;
        try {
            List<?> devices = selection.bJ_(); // the selected devices
            if (devices == null) return null;
            for (int i = devices.size() - 1; i >= 0; i--) {
                Object device = devices.get(i);
                if (device != null) return device;
            }
        } catch (Throwable t) {
            MOD.log("DEVICE_SELECTION_RESOLVE_ERROR", t);
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // Ctrl+click on the device panel's Remote Controls button
    // ---------------------------------------------------------------------

    /** While a device panel builds its Remote Controls button: note the panel. */
    @Hook(target = hgq.class, method = "ERO", at = At.ENTRY, id = "remember-device-shell-during-button-build")
    public static void beginRemoteControlsButtonBuild(@This hgq devicePanel) {
        DEVICE_PANEL_BUILD.set(devicePanel);
    }

    /**
     * Sets the button's icon as Bitwig does, then adds Ctrl+left-click (Cmd on macOS) through
     * Bitwig's own mouse bindings. A plain click is unchanged.
     */
    @Hook(target = hgq.class, method = "ERO", at = At.REPLACE_CALL, id = "install-ctrl-click-on-native-remote-controls-button",
          callOwner = dAd.class, call = "TMF", occurrence = 1)
    public static void configureRemoteControlsButtonIcon(dAd button, String iconResource) {
        hgq devicePanel = DEVICE_PANEL_BUILD.get();
        DEVICE_PANEL_BUILD.remove();
        button.TMF(iconResource); // set the icon
        if (devicePanel == null) return;
        try {
            dmX action = dpN.TVZ().tp2(ACTION_ID); // the action, as registered by the framework
            if (action == null) {
                MOD.log("CTRL_CLICK_SKIPPED action " + ACTION_ID + " is not registered");
                return;
            }
            dmQ binding = dmQ.cfM(action, () -> randomizeForPanel(devicePanel));
            button.cfM(eoV.lYc, binding, false); // Ctrl + left click (Cmd on macOS, where Ctrl+click is a right-click)
        } catch (Throwable t) {
            MOD.log("CTRL_CLICK_INSTALL_ERROR", t);
        }
    }

    private static void randomizeForPanel(hgq devicePanel) {
        randomizeDevice(devicePanel.Scv(), "ctrl-click"); // the panel's device
    }

    // ---------------------------------------------------------------------
    // Shared randomisation operation
    // ---------------------------------------------------------------------

    /** The action from a menu: the clicked device, or the Inspector's when there is none. */
    static void randomizeFromAction(Object clickedDevice, String source) {
        if (clickedDevice instanceof jsd) {
            randomizeDevice(clickedDevice, source);
            return;
        }
        Object device = resolveSelectedDevice();
        if (device == null) {
            MOD.log("RANDOMIZE_SKIP source=" + source + " reason=no_active_device_selection");
            return;
        }
        randomizeDevice(device, source);
    }

    private static void randomizeDevice(Object device, String source) {
        if (device == null) return;
        try {
            List<Object> pages = liveVisiblePages(device);
            if (pages.isEmpty()) {
                Object page = currentPage(device);
                if (page != null) pages.add(page);
            }
            if (pages.isEmpty()) {
                MOD.log("RANDOMIZE_SKIP source=" + source + " reason=no_visible_pages");
                return;
            }

            IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
            int changed = 0;
            for (Object p : pages) {
                if (!(p instanceof FyM page)) continue;
                int n = page.cfM(); // its number of slots
                for (int i = 0; i < n; i++) {
                    waW slot = page.cfM(i);
                    if (!(slot instanceof iKw deviceSlot)) continue;
                    bDH parameter = deviceSlot.LaE(); // the slot's parameter
                    if (parameter == null || seen.put(parameter, Boolean.TRUE) != null) continue;
                    if (randomizeOne(parameter)) changed++;
                }
            }
            MOD.log("RANDOMIZED source=" + source + " pages=" + pages.size() + " parameters=" + changed);
        } catch (Throwable t) {
            MOD.log("RANDOMIZE_ERROR source=" + source, t);
        }
    }

    private static boolean randomizeOne(bDH parameter) {
        if (!(parameter instanceof z2X settable) || !settable.d3()) return false; // can be set now
        settable.FzB(ThreadLocalRandom.current().nextDouble()); // normalised value, 0 to 1
        return true;
    }

    private static List<Object> liveVisiblePages(Object device) {
        List<Object> result = new ArrayList<>();
        synchronized (VISIBLE_PAGES) {
            List<WeakReference<Object>> refs = VISIBLE_PAGES.get(device);
            if (refs == null) return result;
            for (WeakReference<Object> ref : refs) {
                Object page = ref == null ? null : ref.get();
                if (page != null) result.add(page);
            }
            if (result.isEmpty()) VISIBLE_PAGES.remove(device);
        }
        return result;
    }

    private static Object currentPage(Object device) {
        synchronized (CURRENT_PAGE) {
            WeakReference<Object> ref = CURRENT_PAGE.get(device);
            return ref == null ? null : ref.get();
        }
    }
}
