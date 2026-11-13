package com.bitwig.mods.hibernatetracks;

import bml.api.Bml;
import bml.api.Settings;
import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** The mod's options, on the Mods page of Bitwig's Settings. */
public final class HibernateSettings implements PreferenceProvider {
    private static final Settings STORE = Bml.mod("hibernate-tracks").settings();
    private static final String AUTO_HIDE = "auto_hide_deactivated";
    private static final String TAILS = "preserve_tails";
    private static final String THRESHOLD = "tail_threshold_db";
    private static final double DEFAULT_THRESHOLD = -60.0;

    static boolean autoHide() {
        return STORE.getBoolean(AUTO_HIDE, true);
    }

    static boolean preserveTails() {
        return STORE.getBoolean(TAILS, true);
    }

    static double tailThresholdDb() {
        try {
            return Double.parseDouble(STORE.get(THRESHOLD, Double.toString(DEFAULT_THRESHOLD)));
        } catch (NumberFormatException unreadable) {
            return DEFAULT_THRESHOLD;
        }
    }

    @Override
    public void declare(Preferences preferences) {
        Page page = preferences.page("mods", "Mods");
        Section section = preferences.section(page, "hibernate-tracks", "Hibernate Tracks");
        preferences.checkbox("auto_hide_deactivated", "Auto hide deactivated tracks")
                   .in(section)
                   .value(HibernateSettings::autoHide, value -> STORE.putBoolean(AUTO_HIDE, value));
        preferences.checkbox("preserve_tails", "Preserve tails")
                   .in(section)
                   .value(HibernateSettings::preserveTails, value -> STORE.putBoolean(TAILS, value));
        preferences.number("tail_threshold_db", "Tail volume threshold", -96, -24)
                   .in(section)
                   .decibels()
                   .value(HibernateSettings::tailThresholdDb, value -> STORE.put(THRESHOLD, Double.toString(value)));
    }
}
