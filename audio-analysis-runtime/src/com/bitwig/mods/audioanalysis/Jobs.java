package com.bitwig.mods.audioanalysis;

import bml.notifications.Notifications;
import bml.notifications.Task;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Long audio jobs: one at a time, on a background thread, cancellable (the Cancel button of its entry
 * in Bitwig's notification centre, Audio Analysis > Cancel, or the "Cancel Audio Analysis Job"
 * action). Each job is a task entry in the notification centre (Framework: Notifications): its steps
 * in the status line, its progress in the bar (the setup's weighted steps, or what an engine run writes
 * to its progress file); a failure or a cancel ends as a message there.
 * A job changes the project only through {@link Control#onUi}, on Bitwig's UI thread, at its end.
 */
public final class Jobs {
    /** The body of a job. Throw to fail with that message. */
    @FunctionalInterface
    public interface Work {
        void run(Control control) throws Exception;
    }

    /** Thrown by {@link Control#check()} after Cancel. */
    public static final class Cancelled extends RuntimeException {
        public Cancelled() { super("cancelled", null, false, false); }
    }

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BML-AudioAnalysis");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicReference<Control> CURRENT = new AtomicReference<>();

    private Jobs() { }

    /** The job running now, or null. */
    public static Control current() {
        return CURRENT.get();
    }

    public static boolean busy() {
        return CURRENT.get() != null;
    }

    /**
     * Starts {@code work} unless a job is running (then says so in a toast and returns false).
     * {@code title} names its entry in the notification centre and its messages ("Separate Stems").
     */
    public static boolean start(String title, Work work) {
        Control control = new Control(title);
        if (!CURRENT.compareAndSet(null, control)) {
            Control running = CURRENT.get();
            Ui.toast("Audio Analysis is busy with " + (running == null ? "a job" : running.title)
                + ". Wait for it, or cancel it (right-click an audio clip > Audio Analysis).");
            return false;
        }
        control.task = Notifications.task(title, control::cancelFromEntry);
        try {
            WORKER.execute(() -> runJob(control, work));
        } catch (RuntimeException e) {
            control.task.finish();
            CURRENT.compareAndSet(control, null);
            AudioAnalysis.MOD.log("could not start " + title, e);
            return false;
        }
        return true;
    }

    /** Asks the running job to stop; its process is killed at the next check. */
    public static void cancelCurrent() {
        Control c = CURRENT.get();
        if (c == null) {
            Ui.toast("No Audio Analysis job is running");
            return;
        }
        c.cancel();
        Ui.toast("Cancelling " + c.title + "…");
    }

    private static void runJob(Control control, Work work) {
        AudioAnalysis.MOD.log("job started: " + control.title);
        try {
            work.run(control);
            AudioAnalysis.MOD.log("job finished: " + control.title);
        } catch (Cancelled e) {
            AudioAnalysis.MOD.log("job cancelled: " + control.title);
            Ui.info(control.title + " cancelled", "");
        } catch (Throwable t) {
            if (control.cancelled) {
                Ui.info(control.title + " cancelled", "");
            } else {
                AudioAnalysis.MOD.log("job failed: " + control.title, t);
                String m = t.getMessage();
                Ui.error(control.title + " failed", m == null || m.isBlank() ? t.getClass().getSimpleName() : m);
            }
        } finally {
            control.task.finish();
            CURRENT.compareAndSet(control, null);
        }
    }

    /** What a job's body uses: its steps, cancellation, processes and the UI thread. */
    public static final class Control implements Installer.Steps {
        public final String title;
        volatile boolean cancelled;
        private volatile String lastPhase = "";
        volatile Task task; // its entry in the notification centre; set before the job runs

        Control(String title) {
            this.title = title;
        }

        public boolean cancelled() {
            return cancelled;
        }

        /** Throws {@link Cancelled} after Cancel. */
        public void check() {
            if (cancelled) throw new Cancelled();
        }

        void cancel() {
            cancelled = true;
            Task t = task;
            if (t != null) t.status(Status.CANCELLING);
        }

        /** The Cancel button of the job's entry (Bitwig's UI thread). */
        private void cancelFromEntry() {
            AudioAnalysis.MOD.log(title + ": Cancel pressed in the notification centre");
            cancel();
        }

        /**
         * Shows a step in the status line of the job's entry (once per change), without a trailing
         * ellipsis. The bar isn't touched: it shows the whole job.
         */
        @Override
        public void phase(String text) {
            check();
            String shown = Status.plain(text);
            if (shown.isEmpty() || shown.equals(lastPhase)) return;
            lastPhase = shown;
            AudioAnalysis.MOD.log(title + ": " + shown);
            task.status(shown);
        }

        /** Fills the progress bar of the job's entry: the whole job is {@code fraction} (0 to 1) done. */
        @Override
        public void progress(double fraction) {
            task.progress(fraction);
        }

        @Override
        public void log(String line) {
            AudioAnalysis.MOD.log(title + ": " + line);
        }

        @Override
        public ProcessRunner.Result run(List<String> command, Map<String, String> env, Path workDir,
                                        String logName, Duration timeout) throws IOException, InterruptedException {
            return run(command, env, workDir, logName, timeout, null);
        }

        /**
         * Runs a command to completion (cancellable, with a time limit); its output is kept in
         * {@code logs/<logName>.log} in the runtime's home. Returns the result; a cancel throws.
         * An engine run that reports progress (to its {@code AUDIO_DNA_PROGRESS_FILE},
         * {@code logs/<logName>.progress}) fills the job's bar.
         */
        @Override
        public ProcessRunner.Result run(List<String> command, Map<String, String> env, Path workDir,
                                        String logName, Duration timeout, Runnable onPoll)
                throws IOException, InterruptedException {
            check();
            Path log = AudioAnalysis.paths().logs().resolve(logName + ".log");
            Path progressFile = AudioAnalysis.paths().logs().resolve(logName + ".progress");
            Files.createDirectories(progressFile.getParent());
            Files.deleteIfExists(progressFile);
            Map<String, String> runEnv = env;
            if (env != null) {
                runEnv = new LinkedHashMap<>(env);
                runEnv.put(Commands.PROGRESS_FILE, progressFile.toString());
            }
            Runnable poll = () -> {
                if (onPoll != null) onPoll.run();
                double f = Progress.readFraction(progressFile);
                if (f >= 0) task.progress(f);
            };
            AudioAnalysis.MOD.log(title + ": running " + String.join(" ", command));
            ProcessRunner.Result r;
            try {
                r = ProcessRunner.run(command, runEnv, workDir, log, timeout, this::cancelled, poll);
            } finally {
                try {
                    Files.deleteIfExists(progressFile);
                } catch (IOException ignored) {
                    // a stale progress file is deleted before the next run anyway
                }
            }
            AudioAnalysis.MOD.log(title + ": exit " + r.exitCode() + (r.timedOut() ? " (timed out)" : "")
                + " at " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME) + ", log " + log);
            if (r.cancelled()) throw new Cancelled();
            return r;
        }

        /**
         * Runs {@code action} on Bitwig's UI thread and waits for its result (up to a minute).
         * This is where a job changes the project, once, at its end.
         */
        public <T> T onUi(Supplier<T> action) throws Exception {
            check();
            CompletableFuture<T> result = new CompletableFuture<>();
            Ui.later(() -> {
                if (cancelled) {
                    result.completeExceptionally(new Cancelled());
                    return;
                }
                try {
                    result.complete(action.get());
                } catch (Throwable t) {
                    result.completeExceptionally(t);
                }
            });
            try {
                return result.get(60, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception ex) throw ex;
                throw new IllegalStateException(cause);
            }
        }
    }
}
