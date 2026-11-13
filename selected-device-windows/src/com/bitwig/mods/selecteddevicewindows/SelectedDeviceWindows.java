package com.bitwig.mods.selecteddevicewindows;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import bitwig.obf.OJv; // a Bitwig device with its own device window
import bitwig.obf.jsd; // a device, as the device widget knows it
import bitwig.obf.obZ; // the Device Inspector's selection of devices on one track
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.This;
import com.bitwig.flt.document.core.master.device.xlG; // a device in the document, with its plug-in window
import com.bitwig.flt.widget.core.device.L1F; // a Bitwig device's widget in the device chain
import com.bitwig.flt.widget.core.device.THR; // the device widget's actions
import com.bitwig.flt.widget.core.device.htp; // the device chain's widget actions

/**
 * Device: Toggle Selected Devices' Windows Together.
 *
 * Toggling the window of one of several devices selected on a track (a plug-in's window or a
 * Bitwig device's own window) toggles it on every selected device.
 */
public final class SelectedDeviceWindows {
    private static final Bml.Mod MOD = Bml.mod("selected-device-windows");
    private static final int MAX_SELECTIONS = 24;

    /**
     * The selections of two or more devices, newest first. Held weakly, so a closed project or a
     * deleted track or device isn't kept alive; a selection that drops below two is removed.
     */
    private static final List<Selection> SELECTIONS = new ArrayList<>();

    private SelectedDeviceWindows() {
    }

    private record Selection(WeakReference<obZ> model, List<WeakReference<Object>> devices) {
    }

    /** Notes the devices selected on one track whenever that selection changes. */
    @Hook(target = obZ.class, method = "NC", at = At.ENTRY, id = "observe-native-same-track-device-selection")
    public static void noteSelection(@This obZ model) {
        try {
            List<?> selected = (List<?>) model.bJ_(); // the selected devices
            synchronized (SELECTIONS) {
                SELECTIONS.removeIf(s -> {
                    obZ m = s.model.get();
                    return m == null || m == model;
                });
                if (selected.size() < 2) return;
                List<WeakReference<Object>> devices = new ArrayList<>(selected.size());
                for (Object d : selected) devices.add(new WeakReference<>(d));
                SELECTIONS.add(0, new Selection(new WeakReference<>(model), devices));
                while (SELECTIONS.size() > MAX_SELECTIONS) SELECTIONS.remove(SELECTIONS.size() - 1);
            }
        } catch (Throwable t) {
            MOD.log("reading the device selection failed", t);
        }
    }

    /**
     * The user toggles one device's window: toggle it on the other selected devices too.
     * Bitwig's own code then toggles the clicked device, once.
     */
    @Hook(target = THR.class, method = "IwV(Ljsd;)V", at = At.ENTRY, id = "fan-out-selected-device-expanded-view-toggle")
    public static void toggleSelectedWindows(@This THR actions, @Arg(0) jsd clicked) {
        toggleOthers(actions, clicked);
    }

    /**
     * The device chain toggles a Bitwig device's window through the device's widget, without
     * THR.IwV (which it calls only for other devices, so each toggle fans out once).
     */
    @Hook(target = htp.class, method = "IwV(Ljsd;)V", at = At.ENTRY, id = "fan-out-selected-native-device-window-toggle")
    public static void toggleSelectedNativeWindows(@This htp chain, @Arg(0) jsd clicked) {
        if (clicked instanceof OJv) toggleOthers(chain, clicked);
    }

    private static void toggleOthers(THR actions, jsd clicked) {
        if (clicked == null) return;
        List<Object> selected = selectionContaining(clicked);
        if (selected == null) return;
        for (Object device : selected) {
            if (device == clicked) continue;
            try {
                toggle(actions, device);
            } catch (Throwable t) {
                MOD.log("toggling a selected device's window failed", t);
            }
        }
    }

    /** Toggles one device's window as a click on it would. */
    private static void toggle(THR actions, Object device) {
        if (device instanceof OJv && device instanceof jsd d && actions instanceof htp chain) {
            L1F widget = chain.hUR(d); // the device's widget in this chain
            if (widget != null) {
                widget.LzO(); // toggle its device window, placed by its widget
                return;
            }
        }
        if (device instanceof xlG other) other.yj(); // toggle its window
    }

    /** The other devices of the selection the device is in, or null when it isn't in one of two or more. */
    private static List<Object> selectionContaining(jsd device) {
        synchronized (SELECTIONS) {
            for (Selection s : SELECTIONS) {
                if (s.model.get() == null) continue;
                boolean found = false;
                List<Object> devices = new ArrayList<>(s.devices.size());
                for (WeakReference<Object> ref : s.devices) {
                    Object d = ref.get();
                    if (d == null) continue;
                    if (d == device) found = true;
                    devices.add(d);
                }
                if (found) return devices.size() < 2 ? null : devices;
            }
        }
        return null;
    }
}
