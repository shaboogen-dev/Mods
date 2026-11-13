package com.bitwig.mods.popupsincentre;

import java.util.function.LongSupplier;

/**
 * What a pop-up says as a message, and when it repeats the last one. No Bitwig names, so it can be
 * tested without Bitwig.
 */
public final class PopupText {
    /** The same text again within this time replaces the last message instead of adding one. */
    public static final long REPEAT_MS = 5_000;
    /** A title is the text before the first colon when it is at most this long. */
    public static final int MAX_PREFIX = 60;
    /** The title of a pop-up without a "Name: text" prefix. */
    public static final String UNTITLED = "Notification";

    /** A pop-up as a message: title and text. */
    public record Words(String title, String text) { }

    private final LongSupplier clock;
    private String lastText;
    private long lastAt = Long.MIN_VALUE;

    public PopupText(LongSupplier clock) {
        this.clock = clock;
    }

    /** Whether {@code text}, going to the centre now, repeats the last one closely enough to replace it. */
    public synchronized boolean repeat(String text) {
        long now = clock.getAsLong();
        boolean same = text != null && text.equals(lastText) && now - lastAt <= REPEAT_MS;
        lastText = text;
        lastAt = now;
        return same;
    }

    /**
     * A pop-up's words as a message. "Voice Lead Chords: select the chords..." gives the title "Voice Lead
     * Chords" and the rest as the text (mods prefix their pop-ups with their name); a pop-up without such
     * a prefix gets the title "Notification" and its whole text, which wraps in the row.
     */
    public static Words words(String popup) {
        String t = popup == null ? "" : popup.strip();
        int colon = t.indexOf(':');
        if (colon > 0 && colon <= MAX_PREFIX) {
            String head = t.substring(0, colon).strip();
            String rest = t.substring(colon + 1).strip();
            if (!head.isEmpty() && !rest.isEmpty() && !rest.startsWith("//")) return new Words(head, rest); // "//": a URL, not a prefix
        }
        return new Words(UNTITLED, t);
    }
}
