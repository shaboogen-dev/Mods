package com.bitwig.mods.pluginundo;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/**
 * Settings > Mods > VST / CLAP Plugin Undo > Disable VST / CLAP plugin undo (through Framework: Settings).
 * The checkbox shows the stored setting inverted: ticked = no plug-in Undo steps (the stored value
 * false, the default), unticked = Bitwig's behaviour (true).
 */
public final class PluginUndoSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods"); // the page every mod shares: this exact ID
        Section section = preferences.section(mods, "plugin-undo", "VST / CLAP Plugin Undo"); // the ID stays: stored settings keep it
        preferences.checkbox("plugin-undo.enabled", "Disable VST / CLAP plugin undo")
                   .in(section)
                   .value(() -> !PluginUndo.enabled(), disabled -> PluginUndo.setEnabled(!disabled));
    }
}
