package com.bitwig.mods.popupsincentre;

import bml.notifications.Centre;
import bml.notifications.CentreProvider;
import bml.notifications.Message;
import bml.notifications.Notifications;
import bml.notifications.Severity;
import bml.notifications.Toast;
import bml.notifications.ToastHandler;

/**
 * Pop-up notifications (Bitwig's toasts in the middle of the window) as messages in the notification
 * centre: an information message with a close button that opens the overlay, as the pop-up would have
 * been seen, and removes itself after {@link #SECONDS} seconds. A controller script's pop-up (a knob's
 * value as it turns) stays Bitwig's. Declared through the registration with Framework: Notifications
 * ({@code notification-centre.properties}). UI thread.
 */
public final class Popups implements CentreProvider, ToastHandler {
    /** How long a pop-up stays in the centre's overlay (Bitwig's own is gone after a few seconds). */
    static final int SECONDS = 8;

    private static final PopupText TEXT = new PopupText(System::currentTimeMillis);
    private static Message last; // the last pop-up posted to the centre, for repeats (UI thread)

    @Override
    public void declare(Centre centre) {
        centre.toasts(this);
    }

    @Override
    public boolean toast(Toast toast) {
        String text = toast.text();
        if (toast.fromController() || text == null || text.isBlank()) return false; // Bitwig's pop-up
        PopupText.Words words = PopupText.words(text);
        Message previous = last;
        if (TEXT.repeat(text) && previous != null) {
            previous.replace(words.title(), words.text()); // the same again: one message, its time restarted
            return true;
        }
        last = Notifications.message(Severity.INFORMATION, words.title(), words.text())
                .dismissAfterSeconds(SECONDS)
                .closable(true)
                .quiet(false) // opens the overlay, as a pop-up is seen
                .post();
        return true;
    }
}
