package com.bitwig.mods.devicewindowhelper;

import bml.api.At;
import bml.api.Hook;
import com.bitwig.flt.document.core.master.device.HSu; // a device in the document

/**
 * Device: Keep Plug-in Windows Closed on Insert.
 *
 * Bitwig opens a plug-in's window when an instrument or effect is inserted. With this mod on, it
 * doesn't; opening the window yourself is unchanged. Turning the mod off gives Bitwig's behaviour back.
 */
public final class DeviceWindowHelper {
    private DeviceWindowHelper() {
    }

    /** An instrument was inserted: leave its plug-in window closed. */
    @Hook(target = HSu.class, method = "Rh1", at = At.REPLACE_CALL, id = "conditional-automatic-expanded-view-instrument",
          callOwner = HSu.class, call = "E3(Z)V", occurrence = 1)
    public static void openInstrumentWindow(HSu device, boolean flag) {
        // Bitwig would call device.E3(flag) here, which opens the plug-in window.
    }

    /** An effect was inserted: leave its plug-in window closed. */
    @Hook(target = HSu.class, method = "Ai", at = At.REPLACE_CALL, id = "conditional-automatic-expanded-view-effect",
          callOwner = HSu.class, call = "E3(Z)V", occurrence = 1)
    public static void openEffectWindow(HSu device, boolean flag) {
        // Bitwig would call device.E3(flag) here, which opens the plug-in window.
    }
}
