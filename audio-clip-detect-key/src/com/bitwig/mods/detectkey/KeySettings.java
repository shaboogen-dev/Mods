package com.bitwig.mods.detectkey;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** Settings > Mods > Detect Key. */
public final class KeySettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods");
        Section section = preferences.section(mods, "audio_clip_detect_key", "Detect Key"); // the ID stays: stored settings keep it
        preferences.choice("after", "After detecting...", "Ask", "Rename", "Show") // stored as the index: 0 ask, 1 rename, 2 show
                   .in(section)
                   .value(DetectKey::after, DetectKey::setAfter);
    }
}
