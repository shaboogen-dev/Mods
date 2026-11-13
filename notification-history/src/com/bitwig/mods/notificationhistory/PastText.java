package com.bitwig.mods.notificationhistory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * What a past entry's row says. No Bitwig names, so it can be tested without Bitwig. The time goes in
 * the text, not in a part of the row, so a message-row layout can't clash with it.
 */
public final class PastText {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    public static final String SEPARATOR = " · ";

    private PastText() { }

    /** {@code millis} as the hour and minute on this computer's clock, e.g. "14:32". */
    public static String time(long millis, ZoneId zone) {
        return CLOCK.format(Instant.ofEpochMilli(millis).atZone(zone));
    }

    /** A message's text: "14:32 · text", or just the time when it had none. */
    public static String message(String time, String text) {
        String t = text == null ? "" : text.strip();
        return t.isEmpty() ? time : time + SEPARATOR + t;
    }

    /** A task's text: "Finished · 14:32" or "Cancelled · 14:32". */
    public static String task(boolean cancelled, String time) {
        return (cancelled ? "Cancelled" : "Finished") + SEPARATOR + time;
    }
}
