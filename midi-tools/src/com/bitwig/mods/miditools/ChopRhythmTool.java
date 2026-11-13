package com.bitwig.mods.miditools;

import bml.inspector.BlockContext;
import bml.inspector.Rows;
import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import bml.pianoroll.Refused;
import java.util.List;

/** Chop Rhythm in the block: its values and rows. The musical logic is in {@link ChopLogic}. */
final class ChopRhythmTool implements MidiTool {
    private int family;
    private int pattern;
    private int gate = 50;
    private int swing;
    private int accent = 50;
    private int tail; // 0: short, 1: holds to the note's end

    // Evolve: off, or the rhythm varied over a phrase (EvolveLogic). Defaults as the original suggests to start with.
    private int evolve;
    private int phrase = 2; // 4 Bars
    private int variation = 50;
    private int cycles = 2; // Upper 15
    private int roll = 1; // Stepped
    private int harmony; // Strict

    @Override
    public void plan(List<Note> notes, Edit edit) {
        if (evolve == 1) {
            if (notes.stream().map(Note::clip).distinct().count() > 1) {
                throw new Refused("Evolve works on notes of one clip");
            }
            try {
                ChopLogic.Plan plan = EvolveLogic.plan(ChopWriter.inputs(notes), patternsOf(family).get(pattern), gate, swing, accent,
                        tail == 1, new EvolveLogic.Options(phrase, variation, cycles, roll, harmony));
                ChopWriter.write(notes, plan, edit);
            } catch (EvolveLogic.Refusal e) {
                throw new Refused(e.getMessage());
            }
            return;
        }
        try {
            ChopLogic.Plan plan = ChopLogic.rhythm(ChopWriter.inputs(notes), patternsOf(family).get(pattern), gate, swing, accent, tail == 1);
            if (plan.slices().size() > ChopLogic.MAX_SLICES) {
                throw new Refused("that would make too many notes for the editor");
            }
            ChopWriter.write(notes, plan, edit);
        } catch (ChopLogic.TooShort e) {
            throw new Refused("select notes at least one sixteenth long");
        }
    }

    private static List<RhythmPattern> patternsOf(int family) {
        String name = RhythmPattern.families().get(family);
        return RhythmPattern.catalog().stream().filter(rhythm -> rhythm.family().equals(name)).toList();
    }

    private static String[] patternNames(int family) {
        return patternsOf(family).stream().map(RhythmPattern::name).toArray(String[]::new);
    }

    @Override
    public String label() {
        return "Chop Rhythm";
    }

    @Override
    public List<Object> rows(BlockContext context) {
        Rows.Choice rhythms = Rows.choice(context, "Rhythm", patternNames(family), () -> pattern, value -> {
            pattern = value;
        });
        return List.of(
                Rows.choice(context, "Family", RhythmPattern.families().toArray(String[]::new), () -> family, value -> {
                    family = value;
                    pattern = Math.min(pattern, patternsOf(family).size() - 1);
                    rhythms.options(patternNames(family));
                }).row(),
                rhythms.row(),
                Rows.number(context, "Gate", 0, 100, 50, " %", false, () -> gate, value -> {
                    gate = (int) value;
                }),
                Rows.number(context, "Swing", 0, 100, 0, " %", false, () -> swing, value -> {
                    swing = (int) value;
                }),
                Rows.number(context, "Accent", 0, 100, 50, " %", false, () -> accent, value -> {
                    accent = (int) value;
                }),
                Rows.choice(context, "Tail", new String[] {"Short", "Hold Last"}, () -> tail, value -> {
                    tail = value;
                }).row(),
                Rows.choice(context, "Evolve", new String[] {"Off", "On"}, () -> evolve, value -> {
                    evolve = value;
                }).row(),
                Rows.when(context, () -> evolve == 1,
                        Rows.steps(context, "Phrase", EvolveLogic.PHRASES, 2, () -> phrase, value -> {
                            phrase = value;
                        }),
                        Rows.number(context, "Variation", 0, 100, 50, " %", false, () -> variation, value -> {
                            variation = (int) value;
                        }),
                        Rows.choice(context, "Cycles", EvolveLogic.CYCLES, () -> cycles, value -> {
                            cycles = value;
                        }).row(),
                        Rows.choice(context, "Roll", EvolveLogic.ROLLS, () -> roll, value -> {
                            roll = value;
                        }).row(),
                        Rows.choice(context, "Harmony", EvolveLogic.HARMONIES, () -> harmony, value -> {
                            harmony = value;
                        }).row()));
    }
}
