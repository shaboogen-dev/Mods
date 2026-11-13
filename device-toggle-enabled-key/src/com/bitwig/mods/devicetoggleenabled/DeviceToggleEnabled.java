package com.bitwig.mods.devicetoggleenabled;

import bitwig.obf.BhF; // a device's "enabled" control
import bitwig.obf.ICh; // a selection of document objects
import bitwig.obf.bid; // a device chain's selection handler
import bitwig.obf.jsd; // a device
import bml.api.Bml;
import com.bitwig.flt.widget.core.device.THR; // the device chain widget
import com.bitwig.ramona.core.lBQ; // a boolean value of a document object

import java.util.ArrayList;
import java.util.List;

/** Toggle Device Enabled switches the selected devices of the device chain that has focus on or off. */
public final class DeviceToggleEnabled {
    private static final Bml.Mod MOD = Bml.mod("device-toggle-enabled-key");

    private DeviceToggleEnabled() {
    }

    /** All selected devices follow the primary one, as Bitwig's own Toggle Active does. */
    static void toggleEnabled(THR chain) {
        try {
            List<jsd> devices = selected(chain);
            if (devices.isEmpty()) return;
            lBQ first = enabled(devices.get(0));
            if (first == null) return;
            boolean target = !first.getBoolValue();
            for (jsd device : devices) {
                lBQ value = enabled(device);
                if (value != null && value.isSettable()) value.setBoolValue(target);
            }
        } catch (RuntimeException failure) {
            MOD.log("could not toggle the selected devices", failure);
        }
    }

    private static lBQ enabled(jsd device) {
        BhF control = device.wZs(); // its "enabled" control
        return control == null ? null : control.iCS(); // the control's value
    }

    /** The primary selected device first, then the rest of the selection. */
    private static List<jsd> selected(THR chain) {
        List<jsd> devices = new ArrayList<>();
        bid selection = chain.Scv(); // the chain's selection handler
        if (selection == null) return devices;
        if (selection.QNk() != null && selection.QNk().getValue() instanceof jsd primary) devices.add(primary); // the primary device
        ICh items = selection.UNj(); // the selected objects
        if (items != null) {
            for (Object item : items.FzB()) {
                if (item instanceof jsd device && !devices.contains(device)) devices.add(device);
            }
        }
        return devices;
    }
}
