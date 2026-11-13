package com.bitwig.mods.audiotomidi;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** Settings > Mods > Audio to MIDI. */
public final class MidiSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods");
        Section section = preferences.section(mods, "audio_clip_to_midi", "Audio to MIDI"); // the ID stays: stored settings keep it
        preferences.slider("detail", "Note detail percentage", 5, 50).wholeNumbers()
                   .in(section)
                   .value(AudioToMidi::detail, AudioToMidi::setDetail);
        preferences.checkbox("separate_first", "Separate stems first, when stem separation is set up")
                   .in(section)
                   .value(AudioToMidi::separateFirst, AudioToMidi::setSeparateFirst);
    }
}
