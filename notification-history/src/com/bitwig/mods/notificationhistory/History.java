package com.bitwig.mods.notificationhistory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

/**
 * The notification history: past entries, newest first, at most {@code cap}, for this session only.
 * No Bitwig names, so it can be tested without Bitwig. One thread (Bitwig's UI thread).
 *
 * <ul>
 *   <li><b>Only shown entries:</b> {@link #shown} notes when a live entry first appeared in the centre;
 *       {@link #ended} records it only if it was shown. An entry withdrawn before Bitwig showed it (a task
 *       under 300 ms, a message replaced at once) never reaches the history.</li>
 * </ul>
 *
 * @param <K> a live entry (compared by identity)
 * @param <E> a past entry
 */
public final class History<K, E> {
    /** Mirrors the history into a list Bitwig shows. Each call follows the change it names. */
    public interface Sink<E> {
        void added(E entry);          // at the top
        void removed(E entry);
        void cleared();
    }

    private final int cap;
    private final LongSupplier clock;
    private final Map<K, Long> shownAt = new IdentityHashMap<>();
    private final List<E> entries = new ArrayList<>(); // newest first
    private final List<Runnable> listeners = new ArrayList<>();
    private Sink<E> sink;

    public History(int cap, LongSupplier clock) {
        this.cap = Math.max(1, cap);
        this.clock = clock;
    }

    /** The list Bitwig shows; it is brought up to date at once. */
    public void sink(Sink<E> sink) {
        this.sink = sink;
        if (sink == null) return;
        sink.cleared();
        for (int i = entries.size() - 1; i >= 0; i--) sink.added(entries.get(i));
    }

    /** A live entry appeared in the centre; only the first time counts. */
    public void shown(K entry) {
        if (entry != null) shownAt.putIfAbsent(entry, clock.getAsLong());
    }

    /** Whether a live entry has been shown and hasn't ended yet. */
    public boolean wasShown(K entry) {
        return entry != null && shownAt.containsKey(entry);
    }

    /**
     * A live entry ended. When it was shown, {@code make} turns its first-shown
     * time into the past entry, which goes on top (the oldest drops off past the cap). Returns the past
     * entry, or null when nothing was recorded. {@code make} may return null to skip it.
     */
    public E ended(K entry, LongFunction<E> make) {
        Long at = entry == null ? null : shownAt.remove(entry);
        if (at == null || make == null) return null;
        E past = make.apply(at);
        if (past == null) return null;
        entries.add(0, past);
        if (sink != null) sink.added(past);
        while (entries.size() > cap) {
            E oldest = entries.remove(entries.size() - 1);
            if (sink != null) sink.removed(oldest);
        }
        changed();
        return past;
    }

    /** A live entry ended without a record (its mod replaced it). */
    public void forget(K entry) {
        if (entry != null) shownAt.remove(entry);
    }

    /** Removes one past entry (its row's close button). */
    public void remove(E past) {
        if (!entries.remove(past)) return;
        if (sink != null) sink.removed(past);
        changed();
    }

    /** Empties the history. Live entries are untouched. */
    public void clear() {
        if (entries.isEmpty()) return;
        entries.clear();
        if (sink != null) sink.cleared();
        changed();
    }

    /** The past entries, newest first (a copy). */
    public List<E> entries() {
        return List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** The current time of the history's clock. */
    public long now() {
        return clock.getAsLong();
    }

    /** Called after every change to the past entries. */
    public void listen(Runnable listener) {
        if (listener != null) listeners.add(listener);
    }

    public void unlisten(Runnable listener) {
        listeners.remove(listener);
    }

    private void changed() {
        for (Runnable r : List.copyOf(listeners)) {
            try {
                r.run();
            } catch (Throwable t) {
                // a listener's failure stays with it
            }
        }
    }
}
