package com.bitwig.mods.audioanalysis;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Runs one child process to completion, off Bitwig's UI thread: output goes to a log file (so a
 * full pipe never blocks it), stdin is closed, and it is killed with its children when the job
 * is cancelled or runs out of time. Never runs a shell.
 */
public final class ProcessRunner {
    /** How it ended. {@code output} is the last part of what it printed. */
    public record Result(int exitCode, String output, boolean cancelled, boolean timedOut) {
        public boolean ok() { return exitCode == 0 && !cancelled && !timedOut; }
    }

    private ProcessRunner() { }

    /**
     * @param onPoll called about five times a second while it runs (e.g. to read a progress file)
     */
    public static Result run(List<String> command, Map<String, String> env, Path workDir, Path log,
                             Duration timeout, BooleanSupplier cancelled, Runnable onPoll)
            throws IOException, InterruptedException {
        Files.createDirectories(log.getParent());
        ProcessBuilder builder = new ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .redirectInput(ProcessBuilder.Redirect.from(nullFile()));
        if (workDir != null) builder.directory(workDir.toFile());
        if (env != null) {
            builder.environment().clear();
            builder.environment().putAll(env);
        }
        Process process = builder.start();
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                boolean cancel = cancelled != null && cancelled.getAsBoolean();
                boolean late = System.nanoTime() >= deadline;
                if (cancel || late) {
                    kill(process);
                    return new Result(-1, tail(log), cancel, !cancel);
                }
                if (onPoll != null) {
                    try { onPoll.run(); } catch (RuntimeException ignored) { /* progress is optional */ }
                }
            }
            return new Result(process.exitValue(), tail(log), false, false);
        } catch (InterruptedException e) {
            kill(process);
            throw e;
        }
    }

    static void kill(Process process) {
        try {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
        } catch (RuntimeException ignored) {
            // the process may have gone already
        }
        process.destroyForcibly();
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The last 4 KB of the log, for messages and the mod log. */
    static String tail(Path log) {
        try (RandomAccessFile f = new RandomAccessFile(log.toFile(), "r")) {
            long length = f.length();
            int size = (int) Math.min(length, 4096);
            byte[] data = new byte[size];
            f.seek(length - size);
            f.readFully(data);
            return new String(data, StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            return "";
        }
    }

    private static java.io.File nullFile() {
        return new java.io.File(Platform.current() == Platform.Os.WINDOWS ? "NUL" : "/dev/null");
    }
}
