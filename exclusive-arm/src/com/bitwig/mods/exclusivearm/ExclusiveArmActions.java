package com.bitwig.mods.exclusivearm;

import bml.actions.ActionProvider;
import bml.actions.Actions;
import com.bitwig.flt.app.CHR; // the application window controller (shows notifications)

/** Toggle Exclusive Arm: an action (shortcut, Commander) that turns the setting on and off. */
public final class ExclusiveArmActions implements ActionProvider {
    @Override
    public void declare(Actions actions) {
        actions.action("exclusive_arm_toggle", "Toggle Exclusive Arm")
               .onRun(context -> toggle());
    }

    private static void toggle() {
        boolean now = !ExclusiveArm.enabled();
        ExclusiveArm.setEnabled(now);
        CHR.Grk().hUR("Exclusive Arm: " + (now ? "on" : "off")); // Bitwig's on-screen notification
    }
}
