package com.bitwig.mods.opendetaileditormaximised;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/**
 * Settings > Mods > Open Detail Editor Maximised > Automatically open Detail Editor maximised
 * (through Framework: Settings). Ticked by default; unticked, Bitwig opens the Detail Editor as it
 * always does. The switch applies at once.
 */
public final class OpenDetailEditorMaximisedSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods"); // the page every mod shares: this exact ID
        Section section = preferences.section(mods, "open-detail-editor-maximised", "Open Detail Editor Maximised");
        preferences.checkbox("open-detail-editor-maximised.enabled", "Automatically open Detail Editor maximised")
                   .in(section)
                   .value(OpenDetailEditorMaximised::enabled, OpenDetailEditorMaximised::setEnabled);
    }
}
