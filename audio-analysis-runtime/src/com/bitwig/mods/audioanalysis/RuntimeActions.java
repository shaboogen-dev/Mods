package com.bitwig.mods.audioanalysis;

import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.actions.Menu;
import bml.actions.Submenu;

/**
 * The runtime's own actions: Set Up, Set Up Stem Separation, Cancel and Remove, in an "Audio
 * Analysis" submenu of an audio clip's right-click menu (each shown only when it applies), and in
 * the Commander and Settings > Shortcuts under "Audio Analysis".
 */
public final class RuntimeActions implements ActionProvider {
    public static final String CATEGORY = "audio_analysis_runtime";

    /** The menus the audio tools use: the right-click menu of an arranger audio clip. */
    public static Menu[] audioClipMenus() {
        Menu[] menus = new Menu[AudioSelection.KINDS.size()];
        for (int i = 0; i < menus.length; i++) menus[i] = Menu.selection(AudioSelection.KINDS.get(i));
        return menus;
    }

    @Override
    public void declare(Actions actions) {
        actions.category(CATEGORY, "Audio Analysis");
        Submenu menu = actions.submenu("audio_analysis", "Audio Analysis").in(audioClipMenus());

        menu.action("audio_analysis_runtime_set_up", "Set Up Audio Analysis")
            .menuLabel("Set Up Audio Analysis…")
            .category(CATEGORY)
            .icon("setup_icon")
            .visibleWhen(c -> !AudioAnalysis.coreReady() && !Jobs.busy())
            .onRun(c -> Setup.core(c.widget()));

        menu.action("audio_analysis_runtime_set_up_stems", "Set Up Stem Separation")
            .menuLabel("Set Up Stem Separation…")
            .category(CATEGORY)
            .icon("setup_icon")
            .visibleWhen(c -> AudioAnalysis.coreReady() && !AudioAnalysis.stemsReady() && !Jobs.busy())
            .onRun(c -> Setup.stems(c.widget()));

        menu.action("audio_analysis_runtime_cancel", "Cancel Audio Analysis Job")
            .category(CATEGORY)
            .icon("cancel_icon")
            .visibleWhen(c -> Jobs.busy())
            .onRun(c -> Jobs.cancelCurrent());

        menu.action("audio_analysis_runtime_remove", "Remove Audio Analysis Setup")
            .menuLabel("Remove Audio Analysis Setup…")
            .category(CATEGORY)
            .icon("remove_icon")
            .visibleWhen(c -> !Jobs.busy() && (AudioAnalysis.coreReady() || java.nio.file.Files.isDirectory(AudioAnalysis.paths().venv())))
            .onRun(c -> Setup.remove(c.widget()));
    }
}
