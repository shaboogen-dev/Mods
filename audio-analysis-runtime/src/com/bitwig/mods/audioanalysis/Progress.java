package com.bitwig.mods.audioanalysis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Overall progress of a task made of steps, each weighted by how long it usually takes (measured):
 * finished steps count in full, the current one by how far it is. What it reports never goes down.
 * Pure: tested offline.
 */
public final class Progress {
    private final DoubleConsumer out;
    private final Map<String, Double> weights = new LinkedHashMap<>();
    private final Map<String, Boolean> done = new LinkedHashMap<>();
    private String current;
    private double within;
    private double reported;

    /** {@code out} receives the overall fraction, 0 to 1. */
    public Progress(DoubleConsumer out) {
        this.out = out == null ? f -> { } : out;
    }

    /** Plans a step that will run, weighted (e.g. its usual seconds). A step planned twice keeps its first weight. */
    public Progress step(String id, double weight) {
        weights.putIfAbsent(id, Math.max(0, weight));
        return this;
    }

    /** The current step is done and {@code id} starts (planned now with weight 0 if it wasn't). */
    public void start(String id) {
        if (current != null) done.put(current, true);
        weights.putIfAbsent(id, 0.0);
        current = id;
        within = 0;
        report();
    }

    /** The current step is {@code fraction} done (0 to 1). */
    public void within(double fraction) {
        within = Double.isNaN(fraction) ? 0 : Math.max(0, Math.min(1, fraction));
        report();
    }

    /** Every step is done. */
    public void complete() {
        if (current != null) done.put(current, true);
        current = null;
        reported = 1;
        out.accept(1);
    }

    /** The overall fraction now (before the never-down rule). */
    public double fraction() {
        double total = 0, sum = 0;
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            total += e.getValue();
            if (done.containsKey(e.getKey())) sum += e.getValue();
            else if (e.getKey().equals(current)) sum += e.getValue() * within;
        }
        return total <= 0 ? 0 : Math.min(1, sum / total);
    }

    private void report() {
        double f = fraction();
        if (f < reported) return;
        reported = f;
        out.accept(f);
    }

    // ---------- reading progress from a child process ----------

    /** A tqdm bar: "45%|####      | 38.0M/84.1M [...]", sizes with an optional k/M/G/T and B. */
    private static final Pattern TQDM = Pattern.compile(
        "(\\d{1,3})%\\|[^|]*\\|\\s*[0-9.]+\\s*[kMGT]?i?B?\\s*/\\s*([0-9.]+)\\s*([kMGT]?)i?B?");

    /**
     * The fraction the last tqdm progress bar in {@code line} shows ("  45%|####   | 38.0M/84.1M ..."),
     * 0 to 1, counting only a bar whose total is at least {@code minTotal} (in its own unit, scaled by
     * k/M/G/T); -1 when there is none.
     */
    public static double tqdmFraction(String line, double minTotal) {
        if (line == null) return -1;
        Matcher m = TQDM.matcher(line);
        double result = -1;
        while (m.find()) {
            int percent = Integer.parseInt(m.group(1));
            double total;
            try {
                total = Double.parseDouble(m.group(2));
            } catch (NumberFormatException e) {
                continue;
            }
            total *= switch (m.group(3)) {
                case "k" -> 1e3;
                case "M" -> 1e6;
                case "G" -> 1e9;
                case "T" -> 1e12;
                default -> 1;
            };
            if (percent <= 100 && total >= minTotal) result = percent / 100.0;
        }
        return result;
    }

    /** The fraction an engine run wrote to its progress file (a number 0 to 1), or -1 when there is none yet. */
    public static double readFraction(Path file) {
        try {
            if (file == null || !Files.isRegularFile(file)) return -1;
            double f = Double.parseDouble(Files.readString(file).strip());
            return Double.isNaN(f) ? -1 : Math.max(0, Math.min(1, f));
        } catch (Exception e) {
            return -1; // being replaced right now, or not a number: the next poll reads it
        }
    }
}
