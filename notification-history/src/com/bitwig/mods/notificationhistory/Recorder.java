package com.bitwig.mods.notificationhistory;

import bitwig.obf.r3s; // Bitwig's observable list

import bml.notifications.Centre;
import bml.notifications.CentreEntry;
import bml.notifications.CentreProvider;
import bml.notifications.CentreWatcher;
import bml.notifications.Notifications;
import bml.notifications.Severity;

import java.time.ZoneId;

/**
 * Records the history: Framework: Notifications tells it about every entry the centre shows and when
 * each ends (declared through the registration, {@code notification-centre.properties}). It copies a
 * title, a text, a severity and a time. Everything runs on Bitwig's UI thread.
 */
public final class Recorder implements CentreProvider, CentreWatcher {
    static final int CAP = 100;

    private static final History<CentreEntry, Past> HISTORY = new History<>(CAP, System::currentTimeMillis);
    private static r3s list; // the past entries as Bitwig's observable list, newest first

    @Override
    public void declare(Centre centre) {
        centre.watch(this);
    }

    @Override
    public void shown(CentreEntry entry) {
        HISTORY.shown(entry);
    }

    @Override
    public void ended(CentreEntry entry) {
        if (entry.replaced()) { // its mod replaced it: the new one follows
            HISTORY.forget(entry);
            return;
        }
        HISTORY.ended(entry, shownAt -> past(entry, shownAt));
    }

    /** The history (UI thread). */
    static History<CentreEntry, Past> history() {
        return HISTORY;
    }

    /** The past entries as Bitwig's observable list, for the panel's list (UI thread). */
    static r3s list() {
        if (list == null) {
            r3s l = new r3s();
            HISTORY.sink(new History.Sink<>() {
                @Override
                public void added(Past entry) {
                    l.add(0, entry); // on top
                }

                @Override
                public void removed(Past entry) {
                    l.remove(entry);
                }

                @Override
                public void cleared() {
                    l.clear();
                }
            });
            list = l;
        }
        return list;
    }

    /** A past entry's row: Bitwig's message row, laid out as the centre's are; its close button takes it out. */
    static Object row(Past past) {
        return Notifications.messageRow(past.severity, past.title, past.text, () -> HISTORY.remove(past));
    }

    /** A message as it was, with the time it was first shown; a task by how it ended, with the time it ended. */
    private static Past past(CentreEntry entry, long shownAt) {
        ZoneId zone = ZoneId.systemDefault();
        if (entry.isTask()) {
            long end = entry.endedAt() > 0 ? entry.endedAt() : HISTORY.now();
            return new Past(Severity.INFORMATION, entry.title(), PastText.task(entry.cancelled(), PastText.time(end, zone)));
        }
        return new Past(entry.severity(), entry.title(), PastText.message(PastText.time(shownAt, zone), entry.text()));
    }
}
