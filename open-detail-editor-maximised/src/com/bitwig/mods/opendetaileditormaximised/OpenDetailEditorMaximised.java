package com.bitwig.mods.opendetaileditormaximised;

import bitwig.obf.aBW; // a window's project view (its layout widgets and view settings)
import bitwig.obf.akn; // a mode of the main view area (ARRANGE, MIX, EDIT)
import bitwig.obf.alW; // a mode, as the view settings set it
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.Settings;

/**
 * Detail Editor: Open Maximised (mod ID open-detail-editor-maximised).
 *
 * Every opening of a clip in the Detail Editor passes through aBW.jHi(): a double-click on a clip
 * (arranger, clip launcher), Bitwig's "Automatically open new clips in Detail Editor Panel"
 * (Behaviour > Defaults) and the "edit in Detail Editor" actions. In a profile whose flag says so,
 * jHi() switches to EDIT; otherwise it shows the Detail Editor panel (aBW.MRY(Class)). This mod
 * makes that call switch to EDIT as Bitwig's own branch does, so the Detail Editor is the main
 * content (with Panel: Streamlined Views, the Detail Editor alone). Where the profile has no EDIT
 * mode, Bitwig's own call runs.
 *
 * Settings > Mods > Open Detail Editor Maximised switches it (on by default). Off, the hook runs
 * Bitwig's own call, so Bitwig behaves exactly as without the mod; the switch applies at once.
 */
public final class OpenDetailEditorMaximised {
    static final Bml.Mod MOD = Bml.mod("open-detail-editor-maximised");
    private static final Settings STORE = MOD.settings();
    private static final String KEY = "open_maximised";

    private static volatile boolean enabled = STORE.getBoolean(KEY, true);

    private OpenDetailEditorMaximised() {
    }

    /** Opening a clip in the Detail Editor opens it maximised (default on). */
    static boolean enabled() {
        return enabled;
    }

    static void setEnabled(boolean value) {
        enabled = value;
        STORE.putBoolean(KEY, value);
    }

    @Hook(target = aBW.class, method = "jHi()V", at = At.REPLACE_CALL, id = "open-detail-editor",
          callOwner = aBW.class, call = "MRY(Ljava/lang/Class;)V", occurrence = 1)
    public static void openDetailEditor(aBW window, Class<?> panel) {
        try {
            if (enabled && switchToEdit(window)) return;
        } catch (Throwable failure) {
            MOD.log("Could not open the Detail Editor maximised; it opens as Bitwig opens it.", failure);
        }
        window.MRY(panel); // Bitwig's own: shows the Detail Editor panel
    }

    /**
     * What Bitwig's private aBW.cfM(String) does for "EDIT": the profile's mode by name, set
     * through rsB.cfM(alW). Not gJi.cfM(akn): that one goes back to the previous mode when the
     * mode is already chosen (the mode toggle).
     */
    private static boolean switchToEdit(aBW window) {
        akn edit = window.puu().IwV("EDIT"); // puu(): the window's display profile
        if (edit == null) return false;
        window.RDJ().cfM((alW) edit); // RDJ(): the window's view settings
        return true;
    }
}
