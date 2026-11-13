import com.bitwig.mods.popupsincentre.PopupText;
import com.bitwig.mods.popupsincentre.PopupText.Words;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Offline checks of what a pop-up says as a message (no Bitwig needed): the same text again within 5 s
 * replaces; the title is the text before the first colon.
 * Run: java -cp <helper.jar>;<framework helper.jar>;<this folder's classes> PopupTest
 */
public final class PopupTest {
    static int checks;

    public static void main(String[] args) {
        AtomicLong clock = new AtomicLong(1_000_000);

        // repeats
        PopupText q = new PopupText(clock::get);
        check(!q.repeat("Busy"), "the first pop-up is new");
        clock.addAndGet(2_000);
        check(q.repeat("Busy"), "the same text within 5 s replaces");
        clock.addAndGet(4_000);
        check(q.repeat("Busy"), "and again, counted from the last one");
        clock.addAndGet(PopupText.REPEAT_MS + 1);
        check(!q.repeat("Busy"), "after 5 s it is new again");
        check(!q.repeat("Done"), "another text is new");
        check(!q.repeat("Busy"), "a text after another one is new");
        check(!q.repeat(null), "null is never a repeat");

        // words
        eq(PopupText.words("Voice Lead Chords: select the chords of a progression: then run it"),
           new Words("Voice Lead Chords", "select the chords of a progression: then run it"), "title before the first colon");
        eq(PopupText.words("Nothing selected"), new Words(PopupText.UNTITLED, "Nothing selected"), "no colon: Notification and the whole text");
        eq(PopupText.words("Note:"), new Words(PopupText.UNTITLED, "Note:"), "nothing after the colon: the whole text");
        eq(PopupText.words(": nothing before"), new Words(PopupText.UNTITLED, ": nothing before"), "nothing before the colon: the whole text");
        eq(PopupText.words("See https://bitwig.com for more"), new Words(PopupText.UNTITLED, "See https://bitwig.com for more"), "a URL isn't a prefix");
        String longHead = "x".repeat(PopupText.MAX_PREFIX + 1) + ": text";
        eq(PopupText.words(longHead), new Words(PopupText.UNTITLED, longHead), "a prefix over 60 characters isn't a title");
        eq(PopupText.words(null), new Words(PopupText.UNTITLED, ""), "null: empty text");

        System.out.println("PopupTest OK (" + checks + " checks)");
    }

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    static void eq(Object actual, Object expected, String what) {
        checks++;
        if (!java.util.Objects.equals(actual, expected)) throw new AssertionError(what + ": expected " + expected + ", got " + actual);
    }
}
