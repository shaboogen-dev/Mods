package com.bitwig.mods.linewaveforms;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** The mod's section on the Settings > Mods page. */
public final class LineWaveformsSettings implements PreferenceProvider {
    @Override
    public void declare(final Preferences preferences) {
        final Page mods = preferences.page("mods", "Mods"); // the page every mod shares: this exact ID
        final Section section = preferences.section(mods, "line-waveforms", "Line Waveforms");
        preferences.checkbox("line_waveforms", "Draw waveforms as a line")
                   .in(section)
                   .value(Options::lineWaveforms, Options::setLineWaveforms);
    }
}
