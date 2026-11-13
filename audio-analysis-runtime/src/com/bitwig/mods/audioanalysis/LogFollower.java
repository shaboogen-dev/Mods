package com.bitwig.mods.audioanalysis;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the lines a child process has added to its log since the last call ({@code \n}, {@code \r\n}
 * or a lone {@code \r}, as progress bars write). A line still being written waits for its end.
 * Starts again from the top when the file got shorter (a new run). Pure: tested offline.
 */
public final class LogFollower {
    private final Path log;
    private long offset;
    private final StringBuilder partial = new StringBuilder();

    public LogFollower(Path log) {
        this.log = log;
    }

    /** The complete lines added since the last call; none when the file isn't there or can't be read. */
    public List<String> poll() {
        List<String> lines = new ArrayList<>();
        try {
            if (!Files.isRegularFile(log)) return lines;
            try (RandomAccessFile f = new RandomAccessFile(log.toFile(), "r")) {
                long length = f.length();
                if (length < offset) {
                    offset = 0;
                    partial.setLength(0);
                }
                if (length == offset) return lines;
                int size = (int) Math.min(length - offset, 1 << 20);
                byte[] data = new byte[size];
                f.seek(offset);
                f.readFully(data);
                offset += size;
                partial.append(new String(data, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            return lines;
        }
        int start = 0;
        for (int i = 0; i < partial.length(); i++) {
            char c = partial.charAt(i);
            if (c == '\n' || c == '\r') {
                lines.add(partial.substring(start, i));
                if (c == '\r' && i + 1 < partial.length() && partial.charAt(i + 1) == '\n') i++;
                start = i + 1;
            }
        }
        partial.delete(0, start);
        return lines;
    }
}
