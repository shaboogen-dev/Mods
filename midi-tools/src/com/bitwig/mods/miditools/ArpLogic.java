package com.bitwig.mods.miditools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Arpeggiate's musical logic, in plain values: one chord in, the notes of its arpeggio out. */
final class ArpLogic {
    /** The styles, in the dropdown's order. */
    enum Style {
        UP("Up"), DOWN("Down"), UP_DOWN("UpDown"), DOWN_UP("DownUp"),
        UP_AND_DOWN("Up & Down"), DOWN_AND_UP("Down & Up"),
        CONVERGE("Converge"), DIVERGE("Diverge"), CON_AND_DIVERGE("Con & Diverge"),
        PINKY_UP("Pinky Up"), PINKY_UP_DOWN("Pinky UpDown"),
        THUMB_UP("Thumb Up"), THUMB_UP_DOWN("Thumb UpDown"),
        PLAY_ORDER("Play Order"), CHORD_TRIGGER("Chord Trigger"),
        RANDOM("Random"), RANDOM_OTHER("Random Other"), RANDOM_ONCE("Random Once");

        final String label;

        Style(String label) {
            this.label = label;
        }
    }

    /** The rates, in the dropdown's order. */
    enum Rate {
        QUARTER("1/4", 1.0), EIGHTH("1/8", 0.5), SIXTEENTH("1/16", 0.25), THIRTY_SECOND("1/32", 0.125),
        QUARTER_TRIPLET("1/4T", 2.0 / 3.0), EIGHTH_TRIPLET("1/8T", 1.0 / 3.0),
        SIXTEENTH_TRIPLET("1/16T", 1.0 / 6.0), THIRTY_SECOND_TRIPLET("1/32T", 1.0 / 12.0);

        final String label;
        final double beats;

        Rate(String label, double beats) {
            this.label = label;
            this.beats = beats;
        }
    }

    /** A note of the chord. */
    record In(int index, int key, double start, double end) {
    }

    /** A note of the arpeggio: its source note, whether it is that note itself or a copy, and where it goes. */
    record Out(int source, boolean sameNote, int key, double start, double end) {
    }

    /** The arpeggio, and the chord notes it has no use for. */
    record Plan(List<Out> notes, List<Integer> unused) {
    }

    /** Twice Bitwig's rounding of note times (1/1048576 of a beat), so a rounded range can't gain a stray step. */
    private static final double TOLERANCE = 2.0 / 1048576.0;

    private ArpLogic() {
    }

    /** Steps at the rate fill the range from the chord's first start to its last end. Keys above 127 are dropped. */
    static Plan plan(List<In> chord, Style style, Rate rate, int gatePercent, int steps, int distance) {
        double rangeStart = chord.stream().mapToDouble(In::start).min().orElse(0.0);
        double rangeEnd = chord.stream().mapToDouble(In::end).max().orElse(rangeStart);
        List<Out> notes = new ArrayList<>();
        List<Integer> unused = new ArrayList<>();
        chord.forEach(note -> unused.add(note.index()));
        if (rangeEnd - rangeStart <= TOLERANCE) {
            return new Plan(List.of(), List.of());
        }
        List<Voice> pattern = pattern(chord, style, steps, distance);
        if (pattern.isEmpty()) {
            return new Plan(List.of(), List.of());
        }
        double gate = Math.max(0.10, Math.min(1.0, gatePercent / 100.0));
        Random random = new Random(seed(chord, Math.max(1, Math.min(8, steps)), distance));
        List<Voice> bag = new ArrayList<>();
        int next = 0;
        // Steps count from the range's start, so a long arpeggio doesn't collect rounding from adding up the rate.
        long count = Math.max(1, (long) Math.ceil((rangeEnd - rangeStart - TOLERANCE) / rate.beats));
        for (long step = 0; step < count; step++) {
            double start = rangeStart + step * rate.beats;
            double end = Math.min(rangeEnd, start + rate.beats * gate);
            if (end - start <= TOLERANCE) {
                continue;
            }
            if (style == Style.CHORD_TRIGGER) {
                for (Voice voice : pattern) {
                    place(voice, start, end, notes, unused);
                }
                continue;
            }
            Voice voice;
            if (style == Style.RANDOM) {
                voice = pattern.get(random.nextInt(pattern.size()));
            } else if (style == Style.RANDOM_OTHER) {
                if (bag.isEmpty()) {
                    bag.addAll(pattern);
                    Collections.shuffle(bag, random);
                }
                voice = bag.remove(bag.size() - 1);
            } else {
                voice = pattern.get(next++ % pattern.size());
            }
            place(voice, start, end, notes, unused);
        }
        return new Plan(notes, unused);
    }

    /** A chord note is used once at its own key, so it stays the same note; every other step is a copy. */
    private static void place(Voice voice, double start, double end, List<Out> notes, List<Integer> unused) {
        boolean sameNote = voice.key == voice.source.key() && unused.remove((Integer) voice.source.index());
        notes.add(new Out(voice.source.index(), sameNote, voice.key, start, end));
    }

    /** One key of the pattern, and the chord note it comes from. */
    private record Voice(int key, In source) {
    }

    private static List<Voice> pattern(List<In> chord, Style style, int steps, int distance) {
        List<In> roots = new ArrayList<>(chord);
        if (style != Style.PLAY_ORDER) {
            roots.sort((a, b) -> Integer.compare(a.key(), b.key()));
        }
        int octaves = Math.max(1, Math.min(8, steps));
        List<Voice> up = new ArrayList<>();
        for (int step = 0; step < octaves; step++) {
            int offset = octaves == 1 ? 0 : (int) Math.round(distance * (step / (double) (octaves - 1)));
            for (In source : roots) {
                int key = source.key() + offset;
                if (key >= 0 && key <= 127) {
                    up.add(new Voice(key, source));
                }
            }
        }
        if (style != Style.PLAY_ORDER) {
            up.sort((a, b) -> Integer.compare(a.key, b.key));
        }
        switch (style) {
            case RANDOM_ONCE:
                Collections.shuffle(up, new Random(seed(chord, octaves, distance)));
                return up;
            case DOWN:
                Collections.reverse(up);
                return up;
            case UP_DOWN:
                return bounce(up, false);
            case DOWN_UP:
                return bounce(up, true);
            case UP_AND_DOWN:
            case DOWN_AND_UP: {
                List<Voice> first = new ArrayList<>(up);
                if (style == Style.DOWN_AND_UP) {
                    Collections.reverse(first);
                }
                List<Voice> result = new ArrayList<>(first);
                Collections.reverse(first);
                result.addAll(first);
                return result;
            }
            case CONVERGE:
            case DIVERGE:
            case CON_AND_DIVERGE: {
                List<Voice> inward = new ArrayList<>();
                for (int low = 0, high = up.size() - 1; low <= high; low++, high--) {
                    inward.add(up.get(low));
                    if (low != high) {
                        inward.add(up.get(high));
                    }
                }
                if (style == Style.DIVERGE) {
                    Collections.reverse(inward);
                }
                return style == Style.CON_AND_DIVERGE ? bounce(inward, false) : inward;
            }
            case PINKY_UP:
            case PINKY_UP_DOWN:
            case THUMB_UP:
            case THUMB_UP_DOWN: {
                if (up.size() <= 1) {
                    return up;
                }
                boolean pinky = style == Style.PINKY_UP || style == Style.PINKY_UP_DOWN;
                Voice anchor = up.get(pinky ? up.size() - 1 : 0);
                List<Voice> walking = new ArrayList<>(up.subList(pinky ? 0 : 1, pinky ? up.size() - 1 : up.size()));
                if (style == Style.PINKY_UP_DOWN || style == Style.THUMB_UP_DOWN) {
                    walking = bounce(walking, false);
                }
                List<Voice> result = new ArrayList<>();
                for (Voice voice : walking) {
                    result.add(pinky ? voice : anchor);
                    result.add(pinky ? anchor : voice);
                }
                return result;
            }
            default:
                return up;
        }
    }

    /** Up and back without repeating either end. */
    private static List<Voice> bounce(List<Voice> ascending, boolean startDown) {
        List<Voice> first = new ArrayList<>(ascending);
        if (startDown) {
            Collections.reverse(first);
        }
        if (first.size() <= 2) {
            return first;
        }
        List<Voice> result = new ArrayList<>(first);
        for (int i = first.size() - 2; i >= 1; i--) {
            result.add(first.get(i));
        }
        return result;
    }

    /** The same chord always gets the same random order. */
    private static long seed(List<In> chord, int octaves, int distance) {
        long seed = 17;
        for (In note : chord) {
            seed = seed * 31 + note.key();
            seed = seed * 31 + Double.doubleToLongBits(note.start());
            seed = seed * 31 + Double.doubleToLongBits(note.end());
        }
        return seed * 31 + octaves * 13L + distance;
    }
}
