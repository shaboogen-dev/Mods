package com.bitwig.mods.remotepageedit;

import bitwig.obf.dAa; // a compact icon button
import bitwig.obf.dyU; // a mouse gesture handler
import bitwig.obf.dyV; // a gesture handler's entry in the pointer help
import bitwig.obf.eoV; // key modifiers
import bitwig.obf.eoW; // a mouse gesture type
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import com.bitwig.flt.widget.core.device.FUR; // the Remote Controls page chooser
import com.bitwig.flt.widget.core.device.yPU; // the Remote Controls content panel
import java.util.function.Consumer;

/**
 * A right-click on Bitwig's Remote Controls Previous/Next page buttons opens Bitwig's Remote
 * Controls editor.
 */
public final class RemoteControlPageButtonsEdit {
    private static final Bml.Mod MOD = Bml.mod("remote-control-page-buttons-edit");

    /** Modified right-clicks kept silent: Shift, Ctrl, Alt, Shift+Ctrl, Ctrl+Alt, Shift+Alt. */
    private static final int[] SILENT_MODIFIER_MASKS = { 1, 2, 8, 3, 10, 9 };

    /** The Remote Controls content panel of the chooser being built; cleared after the Next button. */
    private static final ThreadLocal<yPU> CURRENT_PANEL = new ThreadLocal<>();

    private RemoteControlPageButtonsEdit() {}

    /** Before the chooser builds its two arrow buttons: note the content panel. */
    @Hook(target = FUR.class, method = "MRY(Lcom/bitwig/flt/widget/core/device/yPU;)V", at = At.ENTRY,
          id = "capture-chooser-content-panel")
    public static void rememberChooserPanel(@Arg(0) yPU contentPanel) {
        CURRENT_PANEL.set(contentPanel);
    }

    /** The Previous arrow's help text: set it as Bitwig does, then add the right-click. */
    @Hook(target = FUR.class, method = "MRY(Lcom/bitwig/flt/widget/core/device/yPU;)V", at = At.REPLACE_CALL,
          callOwner = dAa.class, call = "wGc", occurrence = 1, id = "previous-page-button-editor-right-click")
    public static void previousButtonHelp(dAa button, String stockHelp) {
        installEditorRightClick(button, stockHelp, CURRENT_PANEL.get());
    }

    /** The Next arrow's help text: the same. It is the last use of the panel in this build. */
    @Hook(target = FUR.class, method = "MRY(Lcom/bitwig/flt/widget/core/device/yPU;)V", at = At.REPLACE_CALL,
          callOwner = dAa.class, call = "wGc", occurrence = 2, id = "next-page-button-editor-right-click")
    public static void nextButtonHelp(dAa button, String stockHelp) {
        yPU panel = CURRENT_PANEL.get();
        CURRENT_PANEL.remove(); // don't keep the panel (and its device) alive until the next build
        installEditorRightClick(button, stockHelp, panel);
    }

    private static void installEditorRightClick(dAa button, String stockHelp, yPU panel) {
        if (button == null) return;
        button.wGc(stockHelp); // Bitwig's own click help, unchanged
        if (panel == null) {
            MOD.log("the Remote Controls panel wasn't noted before its page buttons were built; no right-click added");
            return;
        }

        // Unmodified right-click: open Bitwig's Remote Controls editor.
        button.cfM(eoW.Hnt, eoV.Hnt, new EditorGesture(panel, "Edit Remote Controls", true)); // menu gesture, no modifiers
        // Modified right-clicks do nothing and show no pointer help.
        EditorGesture silent = new EditorGesture(panel, null, false);
        for (int mask : SILENT_MODIFIER_MASKS) button.cfM(eoW.Hnt, eoV.MRY(mask), silent); // modifiers from a mask
    }

    /** A gesture handler: runs the editor, or nothing; shows its help, or none. */
    private static final class EditorGesture implements dyU {
        private final yPU panel;
        private final String helpText;
        private final boolean activateEditor;

        EditorGesture(yPU panel, String helpText, boolean activateEditor) {
            this.panel = panel;
            this.helpText = helpText;
            this.activateEditor = activateEditor;
        }

        /** The gesture happened. */
        @Override public void kP_() {
            if (activateEditor && panel != null) panel.cfM(true); // editor mode on
        }

        /** Its boolean callback: not used. */
        @Override public void cfM(boolean value) { }

        /** Its pointer help text. */
        @Override public String MRY() { return helpText; }

        /** Lists its entry for the pointer help (only the editor gesture has one). */
        @Override public void MRY(Consumer consumer) {
            if (activateEditor && consumer != null) consumer.accept(new dyV(this));
        }

        @Override public String toString() {
            return activateEditor ? "RemoteControlsEditorRightClick" : "RemoteControlsSilentModifiedRightClick";
        }
    }
}
