package com.bitwig.mods.remotepagebuttons;

import bitwig.obf.AQw; // a boolean value that can be set
import bitwig.obf.rMe; // an observable value
import bml.api.At;
import bml.api.Hook;
import com.bitwig.flt.widget.core.device.FUR; // the Remote Controls page chooser
import com.bitwig.ramona.core.uzc; // a bindable value

/**
 * Keeps Bitwig's Remote Controls Previous/Next page buttons visible, not only in Mapping mode.
 */
public final class RemoteControlPageButtonsAlwaysVisible {
    private RemoteControlPageButtonsAlwaysVisible() {}

    /** The Previous arrow's visibility, which Bitwig binds to Mapping mode: always visible instead. */
    @Hook(target = FUR.class, method = "MRY(Lcom/bitwig/flt/widget/core/device/yPU;)V", at = At.REPLACE_CALL,
          callOwner = FUR.class, call = "cfM(Lcom/bitwig/ramona/core/uzc;LrMe;)V", occurrence = 1,
          id = "previous-page-button-visible")
    public static void previousButtonVisible(uzc visibility, rMe mappingMode) {
        forceVisible(visibility);
    }

    /** The Next arrow's visibility: the same. */
    @Hook(target = FUR.class, method = "MRY(Lcom/bitwig/flt/widget/core/device/yPU;)V", at = At.REPLACE_CALL,
          callOwner = FUR.class, call = "cfM(Lcom/bitwig/ramona/core/uzc;LrMe;)V", occurrence = 2,
          id = "next-page-button-visible")
    public static void nextButtonVisible(uzc visibility, rMe mappingMode) {
        forceVisible(visibility);
    }

    private static void forceVisible(uzc visibility) {
        if (visibility == null) return;
        ((AQw) visibility).setBoolValue(true);
    }
}
