package com.bitwig.mods.linewaveforms;

import bitwig.obf.eEN; // gives the time of each drawn step

/**
 * Reads raw samples the way Bitwig does (same positions, same interpolation, same range for each step) with
 * two differences: a step's range doesn't have to include zero, and a step's two values are written in the
 * order they happen in time. Bitwig's painter copes with this because {@link LanePainter} puts zero back
 * before the painter sees the values.
 */
final class SampleReader {
    private SampleReader() {
    }

    /**
     * @param channel the channel to read, or -1 for all channels together
     * @param frames  samples, interleaved, starting at frame {@code first}
     * @param out     two values per step
     */
    static void read(final int channel, final double start, final eEN offsets, final float[] frames,
                     final int first, final int length, final int channels, final double rate,
                     final float[] out, final int steps) {
        final int last = length - channels; // Bitwig's limit
        double previous = start;
        for (int step = 0; step < steps; step++) {
            final double time = start + offsets.applyAsDouble(step + 1);
            final long position = (long) (time * rate * 256.0); // in 1/256 of a frame
            final int frame = (int) (position >> 8);
            final int span = (int) ((time - previous) * rate);
            final int from = clamp(frame - first - span, last);
            final int to = clamp(frame - first, last);
            final int low = Math.min(from, to);
            final int high = Math.max(from, to);
            final int at = step * 2;
            if (high - low <= 1) {
                final float value = interpolate(channel, frames, low, last, channels, (int) (position & 255L));
                out[at] = value;
                out[at + 1] = value;
            } else {
                readRange(channel, frames, low, high, channels, out, at, span < 0);
            }
            previous = time;
        }
    }

    private static int clamp(final int value, final int last) {
        return value < 0 ? 0 : Math.min(value, last);
    }

    private static float interpolate(final int channel, final float[] frames, final int frame, final int last,
                                     final int channels, final int fraction) {
        double here = 0.0;
        double next = 0.0;
        if (channel == -1) {
            for (int c = 0; c < channels; c++) {
                here += frame < last ? frames[frame * channels + c] : 0.0;
                next += frame + 1 < last ? frames[(frame + 1) * channels + c] : 0.0;
            }
            here /= channels;
            next /= channels;
        } else {
            here = frame < last ? frames[frame * channels + channel] : 0.0;
            next = frame + 1 < last ? frames[(frame + 1) * channels + channel] : 0.0;
        }
        return (float) ((here * (256 - fraction) + next * fraction) / 256.0);
    }

    private static void readRange(final int channel, final float[] frames, final int low, final int high,
                                  final int channels, final float[] out, final int at, final boolean backwards) {
        final int firstChannel = channel == -1 ? 0 : channel;
        final int endChannel = channel == -1 ? channels : channel + 1;
        float least = Float.POSITIVE_INFINITY;
        float most = Float.NEGATIVE_INFINITY;
        int leastAt = low;
        int mostAt = low;
        for (int frame = low; frame <= high; frame++) {
            final int base = frame * channels;
            for (int c = firstChannel; c < endChannel; c++) {
                final float sample = frames[base + c];
                if (sample < least) {
                    least = sample;
                    leastAt = frame;
                }
                if (sample > most) {
                    most = sample;
                    mostAt = frame;
                }
            }
        }
        final boolean leastFirst = backwards ? leastAt >= mostAt : leastAt <= mostAt;
        out[at] = leastFirst ? least : most;
        out[at + 1] = leastFirst ? most : least;
    }
}
