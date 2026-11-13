package com.bitwig.mods.exclusivearm;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** The on/off checkbox, in the mod's section on the Settings > Mods page. */
public final class ExclusiveArmSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods"); // the page every mod shares: this exact ID
        Section arm = preferences.section(mods, "exclusive-arm", "Exclusive Arm");
        preferences.checkbox("enabled", "Disarm other tracks when arming")
                   .in(arm)
                   .value(ExclusiveArm::enabled, ExclusiveArm::setEnabled);
    }
}
