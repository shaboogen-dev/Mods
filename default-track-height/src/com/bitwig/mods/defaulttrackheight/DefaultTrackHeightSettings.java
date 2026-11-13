package com.bitwig.mods.defaulttrackheight;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** The mod's section on the Settings > Mods page. */
public final class DefaultTrackHeightSettings implements PreferenceProvider {
    @Override
    public void declare(final Preferences preferences) {
        final Page mods = preferences.page("mods", "Mods"); // the page every mod shares: this exact ID
        final Section section = preferences.section(mods, "default-track-height", "Default Track Height");
        preferences.slider("default_track_height", "Height", DefaultTrackHeight.MIN, DefaultTrackHeight.MAX).wholeNumbers()
                   .in(section)
                   .value(DefaultTrackHeight::height, DefaultTrackHeight::setHeight);
    }
}
