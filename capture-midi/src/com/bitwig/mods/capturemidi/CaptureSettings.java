package com.bitwig.mods.capturemidi;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** The mod's section on the Settings > Mods page. */
public final class CaptureSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods"); // the page every mod shares: this exact ID
        Section section = preferences.section(mods, "capture_midi", "Capture MIDI");
        preferences.number("keep_seconds", "Keep the last (seconds)", Options.KEEP_MIN, Options.KEEP_MAX).wholeNumbers()
                   .in(section)
                   .value(Options::keepSeconds, Options::setKeepSeconds);
        preferences.number("pause_seconds", "New phrase after... (seconds)", Options.PAUSE_MIN, Options.PAUSE_MAX).wholeNumbers()
                   .in(section)
                   .value(Options::pauseSeconds, Options::setPauseSeconds);
    }
}
