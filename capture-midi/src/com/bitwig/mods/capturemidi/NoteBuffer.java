package com.bitwig.mods.capturemidi;

/**
 * The recently played MIDI: a fixed ring of note-on, note-off and sustain pedal messages, written
 * from Bitwig's MIDI reader thread and the computer keyboard. Writing allocates nothing: three
 * primitive arrays, a counter and a short lock. Older messages are overwritten when the ring is
 * full; the capture reads only the time span the user keeps (Settings).
 */
final class NoteBuffer {
    /** Messages kept at most (power of two): about 10 minutes of dense playing. */
    static final int CAPACITY = 1 << 15;
    private static final int MASK = CAPACITY - 1;

    private static final long[] TIME = new long[CAPACITY]; // System.nanoTime() at arrival
    private static final int[] MESSAGE = new int[CAPACITY]; // status | data1 << 8 | data2 << 16
    private static final int[] SOURCE = new int[CAPACITY]; // which input it came from
    private static final Object LOCK = new Object();

    /** Messages written since Bitwig started; the next one goes to {@code written & MASK}. */
    private static long written;
    /** Messages before this count were placed by a capture and aren't offered again. */
    private static long consumed;

    /** The computer keyboard's source ID; MIDI ports use their reader's identity hash. */
    static final int COMPUTER_KEYBOARD = 0;

    private NoteBuffer() {
    }

    /** Records a short MIDI message if it is a note or the sustain pedal; anything else is ignored. */
    static void record(int source, int message) {
        int type = message & 0xf0;
        if (type != 0x80 && type != 0x90 && !(type == 0xb0 && ((message >>> 8) & 0x7f) == 64)) return;
        long now = System.nanoTime();
        synchronized (LOCK) {
            int at = (int) (written & MASK);
            TIME[at] = now;
            MESSAGE[at] = message;
            SOURCE[at] = source;
            written++;
        }
    }

    /** A copy of what can still be captured: messages after the last capture and not older than {@code since}. */
    static Snapshot snapshot(long since) {
        synchronized (LOCK) {
            long first = Math.max(consumed, Math.max(0, written - CAPACITY));
            while (first < written && TIME[(int) (first & MASK)] - since < 0) first++;
            int n = (int) (written - first);
            long[] time = new long[n];
            int[] message = new int[n];
            int[] source = new int[n];
            for (int i = 0; i < n; i++) {
                int at = (int) ((first + i) & MASK);
                time[i] = TIME[at];
                message[i] = MESSAGE[at];
                source[i] = SOURCE[at];
            }
            return new Snapshot(time, message, source, written);
        }
    }

    /** After a capture: nothing up to {@code through} is offered again, as in Ableton's Capture MIDI. */
    static void consume(long through) {
        synchronized (LOCK) {
            if (through > consumed) consumed = through;
        }
    }

    /** Messages in arrival order; {@code end} is the counter value after the last of them. */
    record Snapshot(long[] time, int[] message, int[] source, long end) {
    }
}
