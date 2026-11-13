package com.bitwig.mods.miditools;

import bml.api.Bml;
import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** Whether the preview plays, in the mod's section on the Settings > Mods page. */
public final class MidiToolsSettings implements PreferenceProvider {
    private static final bml.api.Settings STORE = Bml.mod("midi-tools").settings();
    private static final String KEY_PLAY_PREVIEW = "play_preview";

    private static volatile boolean playPreview = STORE.getBoolean(KEY_PLAY_PREVIEW, true);

    /** The preview plays in place of the notes it changes whenever the clip plays. Read for each new preview. */
    static boolean playPreview() {
        return playPreview;
    }

    private static void setPlayPreview(boolean value) {
        playPreview = value;
        STORE.putBoolean(KEY_PLAY_PREVIEW, value);
    }

    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods"); // the page every mod shares: this exact ID
        Section section = preferences.section(mods, "midi-tools", "MIDI Tools");
        preferences.checkbox("play_preview", "Play the preview")
                   .in(section)
                   .value(MidiToolsSettings::playPreview, MidiToolsSettings::setPlayPreview);
    }
}
