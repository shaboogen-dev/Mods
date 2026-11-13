package com.bitwig.mods.quantizeautomationpoints;

import bitwig.obf.GYI; // the selection kind of automation points: arranger lanes and clip automation
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;

import java.util.Collection;

/**
 * Automation: Quantize Points.
 *
 * Every selection kind tells Bitwig whether it has Quantize ({@code iHb()}) and whether a
 * selection can be quantized now ({@code Grk(Collection)}). Notes, audio events, clips and
 * beat markers answer yes; automation points inherit no. These two overrides make the
 * automation-point kinds answer yes, so Bitwig's own Quantize, its dialog, settings,
 * right-click group, shortcut and Undo step work on selected automation points. Bitwig's
 * generic quantize edit does the rest.
 */
public final class QuantizeAutomationPoints {
    private QuantizeAutomationPoints() {
    }

    /** Automation points have Quantize: the right-click group and the Quantize actions. */
    @Hook(target = GYI.class, method = "iHb()Z", at = At.OVERRIDE_INSTEAD, id = "automation-points-can-quantize")
    public static boolean canQuantize() {
        return true;
    }

    /** A selection of automation points can be quantized when it holds at least one point. */
    @Hook(target = GYI.class, method = "Grk(Ljava/util/Collection;)Z", at = At.OVERRIDE_INSTEAD, id = "automation-selection-can-quantize")
    public static boolean selectionCanQuantize(@Arg(0) Collection<?> selection) {
        return selection != null && !selection.isEmpty();
    }
}
