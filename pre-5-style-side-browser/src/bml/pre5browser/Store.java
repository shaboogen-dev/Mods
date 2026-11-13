package bml.pre5browser;

import java.util.List;

import bml.api.Bml;
import bml.api.Settings;

/**
 * The mod's log and its own settings: the pane's width ({@code pane.units}), the browser panel's
 * last dragged width ({@code browser.units}) and which tabs are on ({@code pane.on.<TAB>}). Until
 * 0.2, Framework: Browser built the pane and kept these in its settings; the first launch with this
 * version moves them here and removes them there.
 */
final class Store {
    static final Bml.Mod LOG = Bml.mod("pre-5-style-side-browser");
    private static final Settings STORE = LOG.settings();
    private static final String MOVED = "moved-from-framework";
    private static final List<String> MOVED_KEYS = List.of("pane.units", "browser.units",
            "pane.on.PACKAGES", "pane.on.COLLECTIONS", "pane.on.KIND", "pane.on.LOCATIONS");

    static {
        moveFromFramework();
    }

    private Store() {
    }

    static String read(String key) {
        return STORE.get(key, "");
    }

    static void write(String key, String value) {
        STORE.put(key, value);
    }

    /** Once: the values the framework kept for the pane become this mod's. */
    private static void moveFromFramework() {
        if (STORE.getBoolean(MOVED, false)) return;
        try {
            Settings framework = Bml.mod("browser-framework").settings();
            int moved = 0;
            for (String key : MOVED_KEYS) {
                String value = framework.get(key, "");
                if (value.isEmpty()) continue;
                if (STORE.get(key, null) == null) STORE.put(key, value);
                moved++;
            }
            STORE.putBoolean(MOVED, true);
            STORE.flush(); // here before they go there
            for (String key : MOVED_KEYS) framework.remove(key);
            if (moved > 0) LOG.log("pane: " + moved + " value(s) moved from Framework: Browser's settings");
        } catch (Throwable t) {
            LOG.log("pane: moving the values from Framework: Browser's settings failed; the pane starts from its defaults", t);
        }
    }
}
