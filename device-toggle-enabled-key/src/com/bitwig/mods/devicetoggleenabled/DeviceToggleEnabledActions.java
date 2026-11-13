package com.bitwig.mods.devicetoggleenabled;

import bml.actions.ActionProvider;
import bml.actions.Actions;
import com.bitwig.flt.widget.core.device.THR; // the device chain widget

/**
 * Declares the action: a real Bitwig action, with no key until the user assigns one.
 *
 * It is bound on device chains and nowhere else, so a key it shares with one of Bitwig's actions
 * stays Bitwig's everywhere outside a chain.
 */
public final class DeviceToggleEnabledActions implements ActionProvider {
    @Override
    public void declare(Actions actions) {
        actions.action("device_collapse_keys_toggle_enabled", "Toggle Device Enabled").category("edit").bindOn(THR.class)
               .onRun(context -> DeviceToggleEnabled.toggleEnabled((THR) context.widget()));
    }
}
