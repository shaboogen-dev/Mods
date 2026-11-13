package com.bitwig.mods.exclusivearm;

import bitwig.obf.dmX; // Bitwig's action class

/**
 * The Shift-click action on arm buttons. Bitwig looks an action's name up in its translation
 * tables by action ID; this subclass answers with the mod's own label instead, so the status bar
 * hint reads "SHIFT+CLICK Arm exclusively" and nothing is looked up.
 */
final class ShiftArmAction extends dmX {
    static final ShiftArmAction INSTANCE = new ShiftArmAction();

    private ShiftArmAction() {
        super("exclusive_arm_shift_click"); // the action ID: never shown, never looked up
    }

    /** Bitwig's name lookup (protected in Bitwig; answered with the mod's label). */
    @Override
    public String TtY() {
        return "Arm exclusively";
    }
}
