package com.bitwig.mods.stepsequencer;

import java.util.List;
import java.util.function.IntConsumer;

final class StepGrid {
    static final int STEPS = 16;
    static final double STEP = 0.25;
    static final double TIME_INCREMENT = STEP / 4;
    private static final double NATIVE_TICK = 1.0 / 1048576;
    private static final double PAGE = STEPS * STEP;
    private static final double EPSILON = 1e-9;

    record Range(double start, double end) {
        int pages() {
            return Math.max(1, (int) Math.ceil((end - start - EPSILON) / PAGE));
        }

        double beat(int page, int column) {
            return start + page * PAGE + column * STEP;
        }

        int playbackColumn(int page, double beat) {
            double first = beat(page, 0);
            if (!Double.isFinite(beat) || beat < first || beat >= Math.min(end, first + PAGE)) {
                return -1;
            }
            return (int) ((beat - first) / STEP);
        }
    }

    record Event<T>(T note, int key, int channel, double start, double end, double velocity) {}

    record Placement(int key, double start, double end) {
        boolean overlaps(Event<?> other) {
            return key == other.key() && start < other.end() - EPSILON && other.start() < end - EPSILON;
        }
    }

    static Placement offset(Event<?> note, double cellStart, double clipEnd, int increments) {
        double length = note.end() - note.start();
        double latest = Math.min(cellStart + STEP - NATIVE_TICK, clipEnd - length);
        // Existing notes can extend beyond the editable range. Leave those tails intact.
        latest = Math.max(note.start(), latest);
        double start = Math.clamp(note.start() + increments * TIME_INCREMENT, cellStart, latest);
        return new Placement(note.key(), start, start + length);
    }

    static Placement length(Event<?> note, double clipEnd, int increments) {
        double minimum = Math.min(note.end(), note.start() + TIME_INCREMENT);
        double end = Math.clamp(note.end() + increments * TIME_INCREMENT, minimum, Math.max(note.end(), clipEnd));
        return new Placement(note.key(), note.start(), end);
    }

    static boolean canPlace(Event<?> source, Placement placement, List<? extends Event<?>> events) {
        Placement original = new Placement(source.key(), source.start(), source.end());
        for (Event<?> other : events) {
            if (other.note().equals(source.note()) || other.channel() != source.channel() || !placement.overlaps(other)) {
                continue;
            }
            // Shortening an existing overlap is safe. Moving into an overlap can cut another note in Bitwig.
            if (placement.key() != original.key() || placement.start() != original.start() || placement.end() > original.end()) {
                return false;
            }
        }
        return true;
    }

    record Cell<T>(Event<T> note, int starts, boolean continuation, boolean cuts) {
        boolean canAdd() {
            return starts == 0 && !continuation && !cuts;
        }
    }

    static <T> Cell<T> cell(List<Event<T>> events, int key, int channel, double start, double end) {
        Event<T> note = null;
        int starts = 0;
        boolean continuation = false;
        boolean cuts = false;
        for (Event<T> event : events) {
            if (event.key() != key) {
                continue;
            }
            if (event.start() >= start - EPSILON && event.start() < end - EPSILON) {
                starts++;
                note = event;
            } else if (event.start() < start - EPSILON && event.end() > start + EPSILON) {
                continuation = true;
            }
            if (event.channel() == channel && event.start() < end - EPSILON && event.end() > start + EPSILON) {
                cuts = true;
            }
        }
        return new Cell<>(starts == 1 ? note : null, starts, continuation, cuts);
    }

    static int velocity127(double velocity) {
        return Math.max(1, Math.min(127, (int) Math.round(velocity * 127)));
    }

    /** Visit intermediate cells when the pointer travels farther than one cell between mouse events. */
    static void trace(int from, int to, IntConsumer visit) {
        int column = from % STEPS;
        int row = from / STEPS;
        int dx = to % STEPS - column;
        int dy = to / STEPS - row;
        int distance = Math.max(Math.abs(dx), Math.abs(dy));
        for (int i = 1; i <= distance; i++) {
            int x = column + (int) Math.round((double) dx * i / distance);
            int y = row + (int) Math.round((double) dy * i / distance);
            visit.accept(y * STEPS + x);
        }
    }
}
