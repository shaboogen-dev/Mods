package bml.hideparts;

import java.util.List;

import bml.api.Bml;
import bml.api.Settings;
import bml.browser.Browser;
import bml.browser.BrowserProvider;
import bml.browser.Feature;
import bml.browser.Features;
import bml.browser.Scope;

/**
 * Hides parts of the side browser through Framework: Browser ({@link Browser#hide}): each option
 * turns a group of the framework's features off while this mod is loaded. The options are this
 * mod's own settings; the framework stores nothing for it, so turning the mod off gives every part
 * back. A browser view that claims a part still decides it while the view is chosen.
 *
 * <p>Before 0.3 these were the framework's own switches, kept in the framework's settings. The
 * first launch with this mod takes them over: a group the user had switched off is hidden here, and
 * the framework's values go back to their defaults, so they don't stay applied without this mod.
 */
public final class HideParts implements BrowserProvider {
    static final Bml.Mod MOD = Bml.mod("browser-hide-parts");
    private static final Settings STORE = MOD.settings();

    /** An option: one group of the side browser's features, and its label in Settings. */
    enum Part {
        QUICK_SOURCES("quick-sources", "Hide Quick Sources",
                Feature.QUICK_SOURCES_VISIBLE, Feature.QUICK_SOURCES_ACTIONS),
        FILTERS("filtering", "Hide filters",
                Feature.FILTERS_VISIBLE, Feature.FILTERING, Feature.FILTER_AUTO_SHOW),
        SUGGESTIONS("suggestions", "Hide search suggestions",
                Feature.SUGGEST_SOURCES, Feature.SUGGEST_INSTRUMENT_KINDS, Feature.SUGGEST_FILTER_VALUES,
                Feature.SUGGEST_OTHER_CATEGORIES, Feature.SUGGEST_EVERYTHING),
        DETAILS("details-pane", "Hide details pane",
                Feature.DETAILS_VISIBLE, Feature.DETAILS_ACTIONS);

        /** The ID: the framework's switch had the same, so the option keeps its place in Settings' order. */
        final String id, label;
        final List<Feature> features;

        Part(String id, String label, Feature... features) {
            this.id = id;
            this.label = label;
            this.features = List.of(features);
        }

        String key() {
            return "hide." + id;
        }

        /** The user's choice: hidden or not (shown until chosen). */
        boolean hidden() {
            return STORE.getBoolean(key(), false);
        }

        void setHidden(boolean hidden) {
            STORE.putBoolean(key(), hidden);
            apply(this);
        }
    }

    /** The framework's handle for this mod, once it has declared. */
    private static volatile Browser browser;

    @Override
    public void declare(Browser browser) {
        takeOver(browser.features());
        HideParts.browser = browser;
        for (Part part : Part.values()) apply(part);
    }

    /** Holds the part's features off while it is hidden, or lets them go. */
    static void apply(Part part) {
        Browser b = browser;
        if (b == null) return; // not declared yet: declare applies every part
        boolean hidden = part.hidden();
        for (Feature feature : part.features) b.hide(feature, Scope.SIDE, hidden);
    }

    /**
     * Once per part: the framework's own switch (0.2 and earlier) becomes this mod's option. A part
     * is hidden here if any of its features was off in the framework's stored values; then those
     * values go back to their defaults. Runs while the framework calls {@code declare}, before any
     * view is active, so no view's claim refuses the reset. This mod's value is written first, so a
     * crash in between loses nothing.
     */
    @SuppressWarnings("deprecation")
    private static void takeOver(Features features) {
        for (Part part : Part.values()) {
            String moved = "moved." + part.id;
            if (STORE.getBoolean(moved, false)) continue;
            try {
                if (STORE.get(part.key(), null) == null) {
                    boolean off = false;
                    for (Feature feature : part.features) off |= !features.userValue(feature, Scope.SIDE);
                    STORE.putBoolean(part.key(), off);
                    STORE.flush();
                    if (off) MOD.log(part.label + ": taken over from Framework: Browser's settings (hidden)");
                }
                boolean all = true;
                for (Feature feature : part.features) {
                    if (features.userValue(feature, Scope.SIDE) == feature.defaultOn()) continue;
                    all &= features.set(feature, Scope.SIDE, feature.defaultOn());
                }
                if (all) STORE.putBoolean(moved, true);
                else MOD.log(part.label + ": the framework's old value couldn't be reset yet; tried again next launch");
            } catch (Throwable t) {
                MOD.log(part.label + ": taking over the framework's setting failed", t);
            }
        }
    }
}
