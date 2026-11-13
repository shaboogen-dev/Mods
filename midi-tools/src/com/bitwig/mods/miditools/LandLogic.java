package com.bitwig.mods.miditools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Notes going onto another key, in plain values: which notes already there they cut, and in which order they move.
 *
 * Bitwig keeps no two notes of one key and channel over each other: a note put on a key clears its time there, and a
 * note it lands inside becomes two. A tool that moves notes onto other keys (Voice Lead Chords, Extract Bass) plans
 * that cut itself, as edits the preview shows, so that by the time a note lands nothing is under it and Apply leaves
 * exactly what the preview showed.
 */
final class LandLogic {
    /** A note: its clip (group), lane and times. */
    record N(int id, int group, int channel, int key, double start, double end) {
        String lane(int onKey) {
            return group + ":" + channel + ":" + onKey;
        }
    }

    /** A new note the plan puts in: a copy of a note, on a key, between two times. */
    record Copy(int of, int key, double start, double end) {
    }

    /** What is left of a note other notes land on: its pieces, in order. None: it goes. */
    record Cut(int note, List<double[]> pieces) {
    }

    /** The cuts, and the order the moving notes move in. */
    record Plan(List<Cut> cuts, List<Integer> moveOrder) {
    }

    /** Two notes would land on each other, or each would land where the other still is. */
    static final class Collision extends RuntimeException {
        Collision(String message) {
            super(message);
        }
    }

    static final double TOLERANCE = ClearLogic.TOLERANCE;

    private LandLogic() {
    }

    /**
     * notes: every note concerned (the selected notes and the notes around them), by id = position. removed: notes
     * the plan removes. newKeys: notes the plan moves to another key. copies: new notes the plan puts in.
     */
    static Plan plan(List<N> notes, Set<Integer> removed, Map<Integer, Integer> newKeys, List<Copy> copies) {
        List<double[]> landingTimes = new ArrayList<>();
        List<String> landingLanes = new ArrayList<>();
        List<Integer> movers = new ArrayList<>();
        for (N note : notes) {
            Integer key = newKeys.get(note.id());
            if (key != null && key != note.key() && !removed.contains(note.id())) {
                movers.add(note.id());
                landingTimes.add(new double[] {note.start(), note.end()});
                landingLanes.add(note.lane(key));
            }
        }
        for (Copy copy : copies) {
            N of = notes.get(copy.of());
            landingTimes.add(new double[] {copy.start(), copy.end()});
            landingLanes.add(of.lane(copy.key()));
        }
        for (int i = 0; i < landingTimes.size(); i++) {
            for (int j = i + 1; j < landingTimes.size(); j++) {
                if (landingLanes.get(i).equals(landingLanes.get(j)) && overlap(landingTimes.get(i), landingTimes.get(j))) {
                    throw new Collision("two notes would land on each other");
                }
            }
        }
        // Notes that stay on their lane lose the time of every note landing there.
        List<Cut> cuts = new ArrayList<>();
        Set<Integer> moving = new HashSet<>(movers);
        for (N note : notes) {
            if (removed.contains(note.id()) || moving.contains(note.id())) {
                continue;
            }
            List<double[]> pieces = new ArrayList<>();
            pieces.add(new double[] {note.start(), note.end()});
            boolean cut = false;
            for (int i = 0; i < landingTimes.size(); i++) {
                double[] landing = landingTimes.get(i);
                if (!landingLanes.get(i).equals(note.lane(note.key())) || !overlap(landing, new double[] {note.start(), note.end()})) {
                    continue;
                }
                cut = true;
                List<double[]> next = new ArrayList<>();
                for (double[] piece : pieces) {
                    if (landing[0] - piece[0] > TOLERANCE) {
                        next.add(new double[] {piece[0], Math.min(piece[1], landing[0])});
                    }
                    if (piece[1] - landing[1] > TOLERANCE) {
                        next.add(new double[] {Math.max(piece[0], landing[1]), piece[1]});
                    }
                }
                pieces = next;
            }
            if (cut) {
                cuts.add(new Cut(note.id(), List.copyOf(pieces)));
            }
        }
        // A note moves after the moving notes it would land on have left.
        Map<Integer, List<Integer>> after = new HashMap<>();
        for (int m : movers) {
            N note = notes.get(m);
            String target = note.lane(newKeys.get(m));
            List<Integer> first = new ArrayList<>();
            for (int other : movers) {
                N there = notes.get(other);
                if (other != m && there.lane(there.key()).equals(target)
                        && overlap(new double[] {note.start(), note.end()}, new double[] {there.start(), there.end()})) {
                    first.add(other);
                }
            }
            after.put(m, first);
        }
        List<Integer> order = new ArrayList<>();
        Set<Integer> done = new HashSet<>();
        while (order.size() < movers.size()) {
            boolean progress = false;
            for (int m : movers) {
                if (!done.contains(m) && done.containsAll(after.get(m))) {
                    order.add(m);
                    done.add(m);
                    progress = true;
                }
            }
            if (!progress) {
                throw new Collision("notes would swap places with each other");
            }
        }
        return new Plan(cuts, order);
    }

    static boolean overlap(double[] a, double[] b) {
        return a[0] < b[1] - TOLERANCE && b[0] < a[1] - TOLERANCE;
    }

    /** The edits a plan needs, on whatever stands for a note: Bitwig's notes in the tools, a model in the tests. */
    interface Edits<T> {
        T copy(T note, int key, double start, double end);

        void trimStart(T note, double start);

        void setEnd(T note, double end);

        void setKey(T note, int key);

        void remove(T note);
    }

    /**
     * The tool's own removals first, then the cuts (the pieces after the first are copies, made before the note is
     * trimmed), then the moves in their order, then the new notes, which go in after every other edit.
     */
    static <T> void write(List<T> notes, List<N> plain, Set<Integer> removed, Map<Integer, Integer> newKeys, List<Copy> copies,
                          Plan plan, Edits<T> edits) {
        for (N note : plain) {
            if (removed.contains(note.id())) {
                edits.remove(notes.get(note.id()));
            }
        }
        for (Cut cut : plan.cuts()) {
            T note = notes.get(cut.note());
            N was = plain.get(cut.note());
            if (cut.pieces().isEmpty()) {
                edits.remove(note);
                continue;
            }
            for (int i = 1; i < cut.pieces().size(); i++) {
                double[] piece = cut.pieces().get(i);
                edits.copy(note, was.key(), piece[0], piece[1]);
            }
            double[] first = cut.pieces().get(0);
            if (first[0] - was.start() > TOLERANCE) {
                edits.trimStart(note, first[0]);
            }
            if (was.end() - first[1] > TOLERANCE) {
                edits.setEnd(note, first[1]);
            }
        }
        for (int m : plan.moveOrder()) {
            edits.setKey(notes.get(m), newKeys.get(m));
        }
        for (Copy copy : copies) {
            edits.copy(notes.get(copy.of()), copy.key(), copy.start(), copy.end());
        }
    }

    /**
     * The first keys.length notes move to these keys, after what they land on is cut. False when no key changes.
     * Collision when the moves can't be made.
     */
    static <T> boolean moveKeys(List<T> notes, List<N> plain, int[] keys, Edits<T> edits) {
        Map<Integer, Integer> newKeys = new HashMap<>();
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != plain.get(i).key()) {
                newKeys.put(i, keys[i]);
            }
        }
        if (newKeys.isEmpty()) {
            return false;
        }
        write(notes, plain, Set.of(), newKeys, List.of(), plan(plain, Set.of(), newKeys, List.of()), edits);
        return true;
    }
}
