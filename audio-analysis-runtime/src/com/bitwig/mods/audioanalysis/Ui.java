package com.bitwig.mods.audioanalysis;

import bitwig.obf.dpN; // Bitwig's application: its UI event loop
import bml.notifications.Message;
import bml.notifications.Notifications;
import bml.notifications.Severity;
import com.bitwig.base.platform.Platform; // Bitwig's platform helpers (opening a folder in the file manager)

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Bitwig's UI thread and the runtime's messages, through Framework: Notifications. Safe to call from
 * any thread. Replies to a click are toasts; a job's outcome is a message in Bitwig's notification
 * centre; a running job is a task entry there ({@link Jobs}).
 */
public final class Ui {
    private Ui() { }

    /** How long an information outcome ("set up", "cancelled") stays, in seconds. */
    static final int INFO_SECONDS = 15;

    /** Runs {@code action} on Bitwig's UI thread, after the current event. */
    public static void later(Runnable action) {
        dpN.TVZ().asyncExec(() -> { // dpN.TVZ(): the application; asyncExec: its event loop
            try {
                action.run();
            } catch (Throwable t) {
                AudioAnalysis.MOD.log("UI task failed", t);
            }
        });
    }

    /** A short reply to something the user just did, in Bitwig's transient notification. */
    public static void notify(String text) {
        toast(text);
    }

    /** A short reply to something the user just did, in Bitwig's transient notification. */
    public static void toast(String text) {
        AudioAnalysis.MOD.log("toast: " + text);
        Notifications.toast(text);
    }

    /** A job's outcome to read in passing, in the notification centre: information that goes after 15 seconds. */
    public static void info(String title, String text) {
        AudioAnalysis.MOD.log("message: " + title + (text.isEmpty() ? "" : ": " + text));
        Notifications.message(Severity.INFORMATION, title, text).dismissAfterSeconds(INFO_SECONDS).post();
    }

    /** A job's result the user asked to see, in the notification centre: information that stays until closed. */
    public static void result(String title, String text) {
        AudioAnalysis.MOD.log("message: " + title + (text.isEmpty() ? "" : ": " + text));
        Notifications.post(Severity.INFORMATION, title, text);
    }

    /** Something the user has to know or do, in the notification centre: a warning that stays until closed. */
    public static void warning(String title, String text) {
        AudioAnalysis.MOD.log("warning: " + title + ": " + text);
        Notifications.post(Severity.WARNING, title, text);
    }

    /** A failure: an error that stays until closed, with Open Log Folder when there are logs. */
    static void error(String title, String text) {
        AudioAnalysis.MOD.log("error: " + title + ": " + text);
        Message.Builder m = Notifications.message(Severity.ERROR, title, text);
        Path logs = AudioAnalysis.paths().logs();
        if (Files.isDirectory(logs)) m.action("Open Log Folder", () -> openFolder(logs));
        m.post();
    }

    /** Opens {@code folder} in the system's file manager, as Bitwig opens files (off the UI thread: it waits for the opener). */
    static void openFolder(Path folder) {
        Thread t = new Thread(() -> {
            try {
                Platform.cfM(new File(folder.toString())); // open with the system: Explorer, Finder, xdg-open
            } catch (Throwable e) {
                AudioAnalysis.MOD.log("could not open " + folder, e);
                toast("Couldn't open " + folder);
            }
        }, "BML-AudioAnalysis-open");
        t.setDaemon(true);
        t.start();
    }
}
