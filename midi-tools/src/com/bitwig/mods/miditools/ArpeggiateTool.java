package com.bitwig.mods.miditools;

import bml.inspector.BlockContext;
import bml.inspector.Rows;
import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import bml.pianoroll.Refused;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Arpeggiate in the block: its values and rows. The musical logic is in {@link ArpLogic}. */
final class ArpeggiateTool implements MidiTool {
    /** Notes further apart than Bitwig's rounding of note times don't start together. */
    private static final double SAME_START = 2.0 / 1048576.0;
    /** The rates as the Rate bar shows them: shortest at the left, as Bitwig's Arpeggiator Rate (a length in beats) grows. */
    private static final ArpLogic.Rate[] BY_LENGTH = Arrays.stream(ArpLogic.Rate.values())
            .sorted(Comparator.comparingDouble((ArpLogic.Rate r) -> r.beats)).toArray(ArpLogic.Rate[]::new);

    private int style;
    private int rate;
    private int gate = 100;
    private int steps = 1;
    private int distance = 12;

    /** Notes of one clip and channel that start together are a chord, and each is arpeggiated on its own. */
    @Override
    public void plan(List<Note> notes, Edit edit) {
        for (List<Note> chord : chords(notes)) {
            planChord(chord, edit);
        }
    }

    private void planChord(List<Note> chord, Edit edit) {
        List<ArpLogic.In> in = new ArrayList<>();
        for (int i = 0; i < chord.size(); i++) {
            in.add(new ArpLogic.In(i, chord.get(i).key(), chord.get(i).start(), chord.get(i).end()));
        }
        ArpLogic.Plan plan = ArpLogic.plan(in, ArpLogic.Style.values()[style], ArpLogic.Rate.values()[rate], gate, steps, distance);
        if (plan.notes().isEmpty()) {
            throw new Refused("the selection can't make an arpeggio");
        }
        // Copies first, as a copy takes what its note holds when it is made.
        for (ArpLogic.Out out : plan.notes()) {
            if (!out.sameNote()) {
                edit.copy(chord.get(out.source()), out.key(), out.start(), out.end());
            }
        }
        for (ArpLogic.Out out : plan.notes()) {
            if (out.sameNote()) {
                Note note = chord.get(out.source());
                edit.move(note, out.start());
                edit.setEnd(note, out.end());
            }
        }
        for (int unused : plan.unused()) {
            edit.remove(chord.get(unused));
        }
    }

    /** Notes of one clip and channel that start together are a chord. */
    private static List<List<Note>> chords(List<Note> notes) {
        List<Note> sorted = new ArrayList<>(notes);
        sorted.sort(Comparator.comparingInt((Note note) -> note.clip() == null ? 0 : note.clip().hashCode())
                              .thenComparingInt(Note::channel).thenComparingDouble(Note::start));
        List<List<Note>> chords = new ArrayList<>();
        for (Note note : sorted) {
            List<Note> chord = chords.isEmpty() ? null : chords.get(chords.size() - 1);
            if (chord == null || !sameChord(chord.get(0), note)) {
                chord = new ArrayList<>();
                chords.add(chord);
            }
            chord.add(note);
        }
        return chords;
    }

    private static boolean sameChord(Note first, Note note) {
        return Objects.equals(first.clip(), note.clip()) && first.channel() == note.channel()
                && Math.abs(first.start() - note.start()) <= SAME_START;
    }

    @Override
    public String label() {
        return "Arpeggiate";
    }

    /** Where a rate is on the Rate bar. */
    private static int place(ArpLogic.Rate rate) {
        return Arrays.asList(BY_LENGTH).indexOf(rate);
    }

    @Override
    public List<Object> rows(BlockContext context) {
        String[] styles = Arrays.stream(ArpLogic.Style.values()).map(s -> s.label).toArray(String[]::new);
        String[] rates = Arrays.stream(BY_LENGTH).map(r -> r.label).toArray(String[]::new);
        return List.of(
                Rows.choice(context, "Style", styles, () -> style, value -> {
                    style = value;
                }).row(),
                Rows.steps(context, "Rate", rates, place(ArpLogic.Rate.values()[0]), () -> place(ArpLogic.Rate.values()[rate]),
                        value -> {
                            rate = BY_LENGTH[value].ordinal();
                        }),
                Rows.number(context, "Gate", 10, 100, 100, " %", false, () -> gate, value -> {
                    gate = (int) value;
                }),
                Rows.number(context, "Steps", 1, 8, 1, "", false, () -> steps, value -> {
                    steps = (int) value;
                }),
                Rows.number(context, "Distance", -24, 24, 12, " st", true, () -> distance, value -> {
                    distance = (int) value;
                }, () -> steps > 1)); // greyed out at one step, which goes nowhere
    }
}
