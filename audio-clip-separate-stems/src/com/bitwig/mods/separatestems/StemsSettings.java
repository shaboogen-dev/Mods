package com.bitwig.mods.separatestems;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** Settings > Mods > Separate Stems: which stems become tracks. */
public final class StemsSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods");
        Section section = preferences.section(mods, "audio_clip_separate_stems", "Separate Stems"); // the ID stays: stored settings keep it
        add(preferences, section, "vocals", "Add a Vocals track");
        add(preferences, section, "drums", "Add a Drums track");
        add(preferences, section, "bass", "Add a Bass track");
        add(preferences, section, "other", "Add an Other (everything else) track");
    }

    private static void add(Preferences preferences, Section section, String stem, String label) {
        preferences.checkbox("stem_" + stem, label)
                   .in(section)
                   .value(() -> SeparateStems.wants(stem), on -> SeparateStems.setWants(stem, on));
    }
}
