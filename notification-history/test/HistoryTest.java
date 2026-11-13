import com.bitwig.mods.notificationhistory.DotRoute;
import com.bitwig.mods.notificationhistory.History;
import com.bitwig.mods.notificationhistory.PastText;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Offline checks of the notification history (no Bitwig needed): only entries that were shown are
 * recorded; newest first; the cap drops the oldest; Clear empties it and the list Bitwig shows; a
 * replaced entry is forgotten; which handler a press on the dot gets; the row texts.
 * Run: java -cp <helper.jar>;<this folder's classes> HistoryTest
 */
public final class HistoryTest {
    static int checks;

    public static void main(String[] args) {
        AtomicLong clock = new AtomicLong(1000);
        History<String, String> h = new History<>(3, clock::get);
        List<String> shown = new ArrayList<>(); // the list Bitwig shows, kept by the sink
        int[] changes = {0};
        h.sink(new History.Sink<>() {
            @Override
            public void added(String e) {
                shown.add(0, e);
            }

            @Override
            public void removed(String e) {
                shown.remove(e);
            }

            @Override
            public void cleared() {
                shown.clear();
            }
        });
        h.listen(() -> changes[0]++);

        // only shown entries
        check(h.ended("never-shown", t -> "never-shown@" + t) == null, "an entry never shown isn't recorded");
        check(h.isEmpty(), "still empty");
        h.shown("a");
        clock.set(2000);
        h.shown("a"); // shown again (the view refilled): the first time counts
        eq(h.ended("a", t -> "a@" + t), "a@1000", "a shown entry is recorded with its first-shown time");
        check(!h.wasShown("a"), "an ended entry is no longer tracked");
        check(h.ended("a", t -> "again") == null, "an entry ends once");

        // newest first, cap
        h.shown("b");
        h.ended("b", t -> "b");
        h.shown("c");
        h.ended("c", t -> "c");
        eq(h.entries(), List.of("c", "b", "a@1000"), "newest first");
        h.shown("d");
        h.ended("d", t -> "d");
        eq(h.entries(), List.of("d", "c", "b"), "the cap drops the oldest");
        eq(shown, List.of("d", "c", "b"), "the shown list follows, newest first");

        // a make that returns null records nothing
        h.shown("skip");
        check(h.ended("skip", t -> null) == null, "make may skip an entry");
        eq(h.entries().size(), 3, "nothing added for a skipped entry");

        // remove one (its row's close button)
        h.remove("c");
        eq(h.entries(), List.of("d", "b"), "remove takes one out");
        eq(shown, List.of("d", "b"), "and from the shown list");
        h.remove("not-there"); // nothing happens

        // replaced: forgotten
        h.shown("old");
        h.forget("old");
        check(h.ended("old", t -> "old") == null, "a forgotten entry isn't recorded");

        // Clear empties the history only
        h.shown("live"); // a live entry, still in the centre
        int before = changes[0];
        h.clear();
        check(h.isEmpty() && shown.isEmpty(), "Clear empties the history and the shown list");
        check(changes[0] == before + 1, "Clear tells the listeners once");
        check(h.wasShown("live"), "Clear leaves live entries alone");
        eq(h.ended("live", t -> "live"), "live", "a live entry still goes into the history after Clear");

        h.shown("f");
        eq(h.ended("f", t -> "f"), "f", "recording goes on after Clear");

        // a late sink gets the current entries, oldest added first
        List<String> late = new ArrayList<>();
        h.shown("g");
        h.ended("g", t -> "g");
        h.sink(new History.Sink<>() {
            @Override
            public void added(String e) {
                late.add(0, e);
            }

            @Override
            public void removed(String e) {
                late.remove(e);
            }

            @Override
            public void cleared() {
                late.clear();
            }
        });
        eq(late, List.of("g", "f", "live"), "a new sink starts with the history, newest first");

        // a throwing listener is contained
        h.listen(() -> { throw new RuntimeException("listener"); });
        h.clear();
        check(h.isEmpty(), "a throwing listener doesn't stop Clear");

        // the full cap of 100
        History<Object, Integer> big = new History<>(100, clock::get);
        for (int i = 0; i < 150; i++) {
            final int n = i;
            Object live = new Object(); // live entries are compared by identity
            big.shown(live);
            big.ended(live, at -> n);
        }
        eq(big.entries().size(), 100, "at most 100 entries");
        eq(big.entries().get(0), 149, "the newest on top");
        eq(big.entries().get(99), 50, "the oldest kept is the 100th from the end");

        // which handler a press on the dot gets
        eq(DotRoute.of(true, DotRoute.Button.LEFT), DotRoute.PANEL, "left click: the panel");
        eq(DotRoute.of(true, DotRoute.Button.RIGHT), DotRoute.MENU, "right click: the menu");
        eq(DotRoute.of(true, DotRoute.Button.OTHER), DotRoute.STOCK, "another button: Bitwig's");
        eq(DotRoute.of(true, null), DotRoute.STOCK, "unknown button: Bitwig's");
        eq(DotRoute.of(false, DotRoute.Button.LEFT), DotRoute.STOCK, "no handler of Bitwig's to fall back on: left untouched");
        eq(DotRoute.of(false, DotRoute.Button.RIGHT), DotRoute.STOCK, "no handler of Bitwig's to fall back on: right untouched");

        // row texts
        long t = 14 * 3600_000L + 32 * 60_000L; // 14:32 UTC
        eq(PastText.time(t, ZoneOffset.UTC), "14:32", "the time");
        eq(PastText.message("14:32", "Disk full"), "14:32 · Disk full", "a message's text");
        eq(PastText.message("14:32", ""), "14:32", "a message without text: just the time");
        eq(PastText.message("14:32", null), "14:32", "a null text: just the time");
        eq(PastText.message("14:32", "  "), "14:32", "a blank text: just the time");
        eq(PastText.task(false, "14:32"), "Finished · 14:32", "a finished task");
        eq(PastText.task(true, "14:32"), "Cancelled · 14:32", "a cancelled task");

        System.out.println("HistoryTest OK (" + checks + " checks)");
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
