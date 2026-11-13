package com.bitwig.mods.notificationhistory;

import bml.notifications.Severity;

/**
 * A past entry: what its history row shows. Compared by identity, so two entries with the same words
 * stay two rows.
 */
final class Past {
    final Severity severity;
    final String title;
    final String text;

    Past(Severity severity, String title, String text) {
        this.severity = severity == null ? Severity.INFORMATION : severity;
        this.title = title == null ? "" : title;
        this.text = text == null ? "" : text;
    }

    @Override
    public String toString() {
        return title + " / " + text;
    }
}
