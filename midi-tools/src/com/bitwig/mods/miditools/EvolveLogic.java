package com.bitwig.mods.miditools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * Chop Rhythm's Evolve, in plain values: the rhythm varies over a phrase. The lowest note sounding in a channel is the
 * bass and keeps the rhythm as it is; the notes above can run the rhythm at another length (Cycles), answer it at the
 * end of each phrase (Variation), roll into the next phrase (Roll), and change chord a little early or late
 * (Harmony). Keys and channels never change. A bar is four beats: the note editor's time signature isn't read.
 */
final class EvolveLogic {
    static final String[] PHRASES = {"1 Bar", "2 Bars", "4 Bars", "8 Bars"};
    static final int[] PHRASE_BARS = {1, 2, 4, 8};
    static final String[] CYCLES = {"Locked", "Upper 12", "Upper 15", "7-12-16"};
    static final String[] ROLLS = {"Off", "Stepped", "Accelerate", "Decelerate", "Triplet"};
    static final String[] HARMONIES = {"Strict", "Early 1/16", "Early 1/8", "Late 1/16", "Late 1/8"};
    static final double[] HARMONY_BEATS = {0, -0.25, -0.5, 0.25, 0.5};

    /** The options, as positions in the lists above, and Variation in percent. */
    record Options(int phrase, int variation, int cycle, int roll, int harmony) {
    }

    /** The notes can't take this evolution: the message says why. */
    static final class Refusal extends RuntimeException {
        Refusal(String message) {
            super(message);
        }
    }

    private record Hit(double start, int step, int accent) {
    }

    private record Event(int source, double start, double end, int accent) {
    }

    private static final double EPS = 0.02;
    private static final double CELL = 0.25; // a sixteenth: 16 of them to a four-beat bar
    private static final double MIN = 0.01;
    private static final long SEED = 0x525954484dL;
    static final int MAX_EVENTS = ChopLogic.MAX_SLICES;

    private EvolveLogic() {
    }

    static ChopLogic.Plan plan(List<ChopLogic.In> sources, RhythmPattern pattern, int gate, int swing, int accentPercent,
                               boolean sustain, Options options) {
        if (sources.isEmpty()) {
            return new ChopLogic.Plan(List.of(), List.of());
        }
        if (sources.size() > 2048) {
            throw new Refusal("select at most 2048 notes");
        }
        for (ChopLogic.In s : sources) {
            if (s.end() - s.start() < CELL - ClearLogic.TOLERANCE) {
                throw new Refusal("select notes at least one sixteenth long");
            }
        }
        double origin = sources.stream().mapToDouble(ChopLogic.In::start).min().orElseThrow();
        double limit = sources.stream().mapToDouble(ChopLogic.In::end).max().orElseThrow();
        if ((limit - origin) / CELL > 65536) {
            throw new Refusal("the selection is too long");
        }
        String cycleMode = CYCLES[options.cycle()];
        String roll = ROLLS[options.roll()];
        double offset = HARMONY_BEATS[options.harmony()];
        int variation = Math.max(0, Math.min(100, options.variation()));
        List<Double> starts = new ArrayList<>();
        for (ChopLogic.In s : sources.stream().sorted(Comparator.comparingDouble(ChopLogic.In::start)).toList()) {
            if (starts.isEmpty() || s.start() - starts.get(starts.size() - 1) > EPS) {
                starts.add(s.start());
            }
        }
        double[] boundaries = starts.stream().mapToDouble(Double::doubleValue).toArray();
        for (int i = 1; i < boundaries.length; i++) {
            double b = starts.get(i);
            double prev = starts.get(i - 1);
            boolean contiguous = sources.stream().anyMatch(n -> Math.abs(n.start() - prev) <= EPS && Math.abs(n.end() - b) <= EPS);
            if (contiguous) {
                double width = Math.min(b - prev, (i + 1 < boundaries.length ? starts.get(i + 1) : limit) - b);
                boundaries[i] += Math.copySign(Math.min(Math.abs(offset), Math.max(0, width / 2 - EPS)), offset);
            }
        }
        List<Event> events = new ArrayList<>();
        for (ChopLogic.In source : sources) {
            int group = 0;
            while (group + 1 < starts.size() && starts.get(group + 1) <= source.start() + EPS) {
                group++;
            }
            double onset = starts.get(group);
            int rank = (int) sources.stream().filter(n -> n.channel() == source.channel() && n.start() <= onset + EPS
                    && n.end() > onset + EPS && n.key() < source.key()).map(ChopLogic.In::key).distinct().count();
            boolean bass = rank == 0;
            double a = source.start();
            double b = source.end();
            // The bass changes chord where it was written; the notes above follow the moved boundaries.
            if (!bass) {
                a += boundaries[group] - starts.get(group);
                if (group + 1 < starts.size() && Math.abs(b - starts.get(group + 1)) <= EPS) {
                    b += boundaries[group + 1] - starts.get(group + 1);
                }
            }
            a = Math.max(origin, a);
            b = Math.min(limit, b);
            if (b - a < MIN) {
                throw new Refusal("the harmony shift leaves no room for a note");
            }
            int cycle = bass ? 16 : switch (cycleMode) {
                case "Upper 12" -> 12;
                case "Upper 15" -> 15;
                case "7-12-16" -> rank == 1 ? 12 : 7;
                default -> 16;
            };
            double phrase = PHRASE_BARS[options.phrase()] * 4.0;
            TreeMap<Double, Hit> hits = new TreeMap<>();
            double voiceCell = cycle * CELL / 16.0;
            long first = Math.max(0, (long) Math.floor((a - origin) / voiceCell) - 1);
            long last = (long) Math.ceil((b - origin) / voiceCell);
            for (long cell = first; cell < last; cell++) {
                int step = (int) (cell % 16);
                long occurrence = (long) Math.floor(cell * voiceCell / phrase);
                long hash = mix(SEED ^ occurrence * 0x9e3779b97f4a7c15L ^ ((long) rank << 32) ^ cell);
                boolean variant = !bass && variation > 0;
                boolean hit = pattern.hitAt(step);
                int accent = pattern.accentAt(step);
                if (variant && Math.floorMod(hash, 100) < variation) {
                    // The phrase answers: attacks and their weight change, the same way each time.
                    hit = true;
                    accent = 75 + (int) Math.floorMod(hash >>> 8, 35);
                }
                double t = origin + cell * voiceCell + ChopLogic.swingDelay(step, swing) * voiceCell / CELL;
                if (hit && t >= a - 1e-9 && t < b - 1e-9) {
                    hits.put(Math.max(a, t), new Hit(Math.max(a, t), step, accent));
                }
                if (!bass && hit && variation > 0) {
                    for (int j = 1; j < 4; j++) {
                        long threshold = Math.floorMod(mix(hash ^ ((long) j * 0x632be59bd9b4e019L)), 100);
                        if (threshold >= variation) {
                            continue;
                        }
                        double rt = t + j * voiceCell / 4;
                        if (rt >= a - 1e-9 && rt < b - 1e-9) {
                            hits.put(rt, new Hit(rt, step, Math.max(45, accent - j * 8)));
                        }
                    }
                }
            }
            if (!bass && (!roll.equals("Off") || variation > 0)) {
                String rollMode = roll.equals("Off") ? "Stepped" : roll;
                double rollPhrase = roll.equals("Off") ? 4.0 : phrase;
                for (long p = Math.max(0, (long) Math.floor((a - origin) / rollPhrase)); origin + (p + 1) * rollPhrase - .5 < b; p++) {
                    double start = origin + (p + 1) * rollPhrase - .5;
                    double end = start + .5;
                    if (start < a - 1e-9 || end > b + 1e-9) {
                        continue;
                    }
                    if (roll.equals("Off") && Math.floorMod(mix(SEED ^ p ^ ((long) rank << 32)), 10000) >= variation * variation) {
                        continue;
                    }
                    hits.subMap(start, true, end, false).clear();
                    int count = rollMode.equals("Triplet") ? 6 : (rollMode.equals("Accelerate") || rollMode.equals("Decelerate")) ? 7 : 8;
                    if (roll.equals("Off")) {
                        count = 2 + variation * 6 / 100;
                    }
                    double[] positions = rollPositions(count, rollMode);
                    for (int j = 0; j < count; j++) {
                        double t = start + positions[j] * .5;
                        hits.put(t, new Hit(t, Math.min(15, (int) ((t - origin) / CELL) % 16), Math.min(127, 70 + j * 7)));
                    }
                }
            }
            if (hits.isEmpty()) {
                throw new Refusal("this rhythm leaves a note silent; choose a denser rhythm");
            }
            // Hits closer than the shortest note to the one before (a ratchet next to a hit moved by the harmony shift,
            // for example) join it. The original refused the whole evolution there.
            List<Hit> list = new ArrayList<>();
            for (Hit hit : hits.values()) {
                if ((list.isEmpty() || hit.start() - list.get(list.size() - 1).start() >= MIN) && b - hit.start() >= MIN) {
                    list.add(hit);
                }
            }
            for (int j = 0; j < list.size(); j++) {
                Hit h = list.get(j);
                double next = j + 1 < list.size() ? list.get(j + 1).start() : b;
                double end = h.start() + ChopLogic.gateLength(next - h.start(), gate);
                if (j + 1 == list.size() && sustain) {
                    end = b;
                }
                if (end - h.start() < MIN - 1e-9) {
                    throw new Refusal("the hits are too close; choose a lower Variation or another Roll");
                }
                events.add(new Event(source.index(), h.start(), end, h.accent()));
                if (events.size() > MAX_EVENTS) {
                    throw new Refusal("that would make too many notes for the editor");
                }
            }
        }
        events = normalize(events, sources);
        List<ChopLogic.Out> slices = new ArrayList<>();
        for (ChopLogic.In source : sources) {
            List<Event> owned = events.stream().filter(e -> e.source() == source.index())
                                      .sorted(Comparator.comparingDouble(Event::start)).toList();
            if (owned.isEmpty()) {
                throw new Refusal("overlapping notes on one key leave a note with no hit");
            }
            for (int i = 0; i < owned.size(); i++) {
                Event e = owned.get(i);
                double velocity = accentPercent == 0 ? -1
                        : Math.max(1, Math.min(127, Math.round(source.velocity127() + (e.accent() - source.velocity127()) * accentPercent / 100.0)));
                slices.add(new ChopLogic.Out(source.index(), i == 0, e.start(), e.end(), velocity));
            }
        }
        return new ChopLogic.Plan(slices, List.of());
    }

    private static ChopLogic.In byIndex(List<ChopLogic.In> sources, int index) {
        for (ChopLogic.In s : sources) {
            if (s.index() == index) {
                return s;
            }
        }
        throw new IllegalStateException();
    }

    private static boolean bass(ChopLogic.In source, List<ChopLogic.In> sources) {
        return sources.stream().noneMatch(n -> n.channel() == source.channel() && n.start() <= source.start() + EPS
                && n.end() > source.start() + EPS && n.key() < source.key());
    }

    /** No two hits of one key and channel over each other: the earlier is cut short, and the bass wins. */
    private static List<Event> normalize(List<Event> input, List<ChopLogic.In> sources) {
        List<Event> sorted = new ArrayList<>(input);
        sorted.sort(Comparator.comparingInt((Event e) -> byIndex(sources, e.source()).channel())
                              .thenComparingInt(e -> byIndex(sources, e.source()).key())
                              .thenComparingDouble(Event::start)
                              .thenComparingDouble(e -> byIndex(sources, e.source()).start()));
        List<Event> kept = new ArrayList<>();
        for (Event current : sorted) {
            ChopLogic.In cur = byIndex(sources, current.source());
            boolean skip = false;
            while (!kept.isEmpty()) {
                Event previous = kept.get(kept.size() - 1);
                ChopLogic.In prev = byIndex(sources, previous.source());
                if (prev.key() != cur.key() || prev.channel() != cur.channel() || previous.end() <= current.start() + 1e-9) {
                    break;
                }
                if (bass(prev, sources) && !bass(cur, sources)) {
                    skip = true;
                    break;
                }
                kept.remove(kept.size() - 1);
                if (current.start() - previous.start() >= MIN - 1e-9) {
                    kept.add(new Event(previous.source(), previous.start(), current.start(), previous.accent()));
                    break;
                }
            }
            if (!skip) {
                kept.add(current);
            }
        }
        return kept;
    }

    /** Where a roll's hits fall in its half beat, from 0 to 1. */
    static double[] rollPositions(int count, String mode) {
        double[] result = new double[count];
        if (mode.equals("Stepped")) {
            double sum = 0;
            for (int i = 0; i < count; i++) {
                result[i] = sum;
                sum += i < count / 2 ? 2 : 1;
            }
            for (int i = 0; i < count; i++) {
                result[i] /= sum;
            }
        } else {
            for (int i = 0; i < count; i++) {
                double x = (double) i / count;
                result[i] = switch (mode) {
                    case "Accelerate" -> 1 - (1 - x) * (1 - x);
                    case "Decelerate" -> x * x;
                    default -> x;
                };
            }
        }
        return result;
    }

    private static long mix(long x) {
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        return x ^ (x >>> 31);
    }
}
