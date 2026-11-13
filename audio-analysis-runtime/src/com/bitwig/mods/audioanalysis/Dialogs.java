package com.bitwig.mods.audioanalysis;

import java.util.List;

/**
 * Bitwig's native dialogs for other mods. (Mods compile against this class rather than
 * {@link ConfirmDialog}, whose Bitwig superclass isn't on their classpath.)
 */
public final class Dialogs {
    private Dialogs() { }

    /**
     * A native confirmation over {@code window} (the widget an action ran from): a title, lines of
     * text, OK and Cancel; OK runs {@code onOk}. Call on Bitwig's UI thread.
     */
    public static void confirm(Object window, String title, List<String> lines, Runnable onOk) {
        ConfirmDialog.show(window, title, lines, onOk);
    }
}
