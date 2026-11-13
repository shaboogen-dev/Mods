package com.bitwig.mods.devicecollapsekeys;

import bml.actions.ActionProvider;
import bml.actions.Actions;
import com.bitwig.flt.widget.core.device.THR; // the device chain widget

/**
 * Declares the two actions: real Bitwig actions, with no key until the user assigns one.
 *
 * They are bound on device chains and nowhere else, so a key they share with one of Bitwig's actions
 * stays Bitwig's everywhere outside a chain.
 */
public final class DeviceCollapseActions implements ActionProvider {
    @Override
    public void declare(Actions actions) {
        actions.action("device_collapse_keys_collapse", "Collapse Device").category("edit").bindOn(THR.class)
               .onRun(context -> DeviceCollapseKeys.setExpanded((THR) context.widget(), false));
        actions.action("device_collapse_keys_expand", "Expand Device").category("edit").bindOn(THR.class)
               .onRun(context -> DeviceCollapseKeys.setExpanded((THR) context.widget(), true));
    }
}
