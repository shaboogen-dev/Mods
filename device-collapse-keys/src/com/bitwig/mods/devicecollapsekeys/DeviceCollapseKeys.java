package com.bitwig.mods.devicecollapsekeys;

import bitwig.obf.ICh; // a selection of document objects
import bitwig.obf.bid; // a device chain's selection handler
import bitwig.obf.fSU; // a device's display settings
import bitwig.obf.jsd; // a device
import bml.api.Bml;
import com.bitwig.flt.widget.core.device.THR; // the device chain widget
import com.bitwig.ramona.core.lBQ; // a boolean value of a document object

import java.util.ArrayList;
import java.util.List;

/** Collapse Device and Expand Device act on the selected devices of the device chain that has focus. */
public final class DeviceCollapseKeys {
    private static final Bml.Mod MOD = Bml.mod("device-collapse-keys");

    private DeviceCollapseKeys() {
    }

    static void setExpanded(THR chain, boolean expanded) {
        try {
            for (jsd device : selected(chain)) {
                fSU settings = device.MJs(); // its display settings
                if (settings == null) continue;
                if (settings.cfM() != expanded) settings.cfM(expanded); // is it expanded; set it
                // The controller API's path to the same value, for when that setter is ignored.
                lBQ value = settings.FzB();
                if (settings.cfM() != expanded && value != null) value.setBoolValue(expanded);
            }
        } catch (RuntimeException failure) {
            MOD.log("could not change the selected devices", failure);
        }
    }

    /** The selected devices of the chain. */
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
