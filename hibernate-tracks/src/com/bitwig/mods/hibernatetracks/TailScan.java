package com.bitwig.mods.hibernatetracks;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;

/** Finds where a rendered 32-bit float WAV goes quiet. Reads only the stretches it's asked about. */
final class TailScan implements AutoCloseable {
    private static final int BLOCK_FRAMES = 256;
    private static final int READ_BLOCKS = 256;

    private final FileChannel channel;
    private final long dataStart;
    final long frames;
    final int sampleRate;
    private final int channels;
    private float threshold;

    private TailScan(FileChannel channel, long dataStart, long frames, int sampleRate, int channels) {
        this.channel = channel;
        this.dataStart = dataStart;
        this.frames = frames;
        this.sampleRate = sampleRate;
        this.channels = channels;
    }

    /** Null if the file can't be read as a finished 32-bit float WAV. */
    static TailScan open(File file, double thresholdDb) {
        FileChannel channel = null;
        try {
            channel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
            TailScan scan = read(channel);
            if (scan == null) {
                channel.close();
            } else {
                scan.threshold = (float) Math.pow(10, thresholdDb / 20);
            }
            return scan;
        } catch (IOException | RuntimeException failure) {
            Tracks.MOD.log("can't scan " + file + ": " + failure);
            try {
                if (channel != null) channel.close();
            } catch (IOException ignored) {
                // nothing more to do
            }
            return null;
        }
    }

    private static TailScan read(FileChannel channel) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        long position = 12; // after "RIFF", size, "WAVE"
        int channels = 0, rate = 0, bits = 0, format = 0;
        while (position + 8 <= channel.size()) {
            header.clear();
            if (channel.read(header, position) < 8) return null;
            String id = new String(header.array(), 0, 4, StandardCharsets.ISO_8859_1);
            long size = header.getInt(4) & 0xffffffffL;
            if (id.equals("fmt ")) {
                ByteBuffer fmt = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
                if (channel.read(fmt, position + 8) < 16) return null;
                format = fmt.getShort(0) & 0xffff;
                channels = fmt.getShort(2);
                rate = fmt.getInt(4);
                bits = fmt.getShort(14);
            } else if (id.equals("data")) {
                boolean float32 = (format == 3 || format == 0xfffe) && bits == 32;
                long available = channel.size() - position - 8;
                if (!float32 || channels <= 0 || rate <= 0 || size == 0 || size > available) return null; // not ours, or not finished
                return new TailScan(channel, position + 8, size / (4L * channels), rate, channels);
            }
            position += 8 + size + (size & 1);
        }
        return null;
    }

    /** The frame just after the last block above the threshold between two frames, scanning back from the end; -1 if all quiet. */
    long lastLoud(long from, long to) throws IOException {
        long end = Math.min(to, frames);
        long start = Math.max(0, from);
        ByteBuffer bytes = ByteBuffer.allocate(BLOCK_FRAMES * READ_BLOCKS * channels * 4).order(ByteOrder.LITTLE_ENDIAN);
        while (end > start) {
            long chunk = Math.min(end - start, BLOCK_FRAMES * READ_BLOCKS);
            long first = end - chunk;
            bytes.clear();
            bytes.limit((int) (chunk * channels * 4));
            long at = dataStart + first * channels * 4;
            while (bytes.hasRemaining()) {
                int read = channel.read(bytes, at + bytes.position());
                if (read <= 0) throw new IOException("short read");
            }
            bytes.flip();
            FloatBuffer samples = bytes.asFloatBuffer();
            for (long block = (chunk - 1) / BLOCK_FRAMES; block >= 0; block--) {
                int low = (int) (block * BLOCK_FRAMES * channels);
                int high = (int) Math.min(chunk * channels, low + (long) BLOCK_FRAMES * channels);
                for (int i = low; i < high; i++) {
                    if (Math.abs(samples.get(i)) > threshold) return first + high / channels;
                }
            }
            end = first;
        }
        return -1;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
