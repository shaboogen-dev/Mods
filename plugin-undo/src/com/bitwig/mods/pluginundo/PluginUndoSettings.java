package com.bitwig.mods.pluginundo;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** Settings > Mods > Optional Plug-in Undo Steps (through Framework: Settings). */
public final class PluginUndoSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods"); // the page every mod shares: this exact ID
        Section section = preferences.section(mods, "plugin-undo", "Optional Plug-in Undo Steps");
        preferences.checkbox("plugin-undo.enabled", "Plug-in changes make Undo steps")
                   .in(section)
                   .value(PluginUndo::enabled, PluginUndo::setEnabled);
    }
}
