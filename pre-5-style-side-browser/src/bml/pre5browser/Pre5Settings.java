package bml.pre5browser;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** Settings → Mods → Pre-5.0 Style Side Browser: which tabs the pane offers (it opens on the leftmost). */
public final class Pre5Settings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods"); // the page every mod shares: this exact ID
        Section section = preferences.section(mods, "pre-5-style-side-browser", "Pre-5.0 Style Side Browser");
        for (PaneHost.Paradigm p : PaneHost.Paradigm.values()) {
            String label = p.tabLabel + " tab";
            preferences.checkbox("pane-" + p.name().toLowerCase(), label)
                       .in(section)
                       .value(p::enabled, on -> {
                           p.setEnabled(on);
                           Pre5SideBrowser.optionChanged(label);
                       });
        }
    }
}
