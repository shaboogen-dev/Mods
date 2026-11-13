package com.bitwig.mods.separatestems;

import bml.actions.ActionProvider;
import bml.actions.Actions;
import com.bitwig.mods.audioanalysis.AudioSelection;
import com.bitwig.mods.audioanalysis.RuntimeActions;

/** "Separate Stems" in an arranger audio clip's right-click menu. */
public final class StemsActions implements ActionProvider {
    @Override
    public void declare(Actions actions) {
        actions.action("audio_clip_separate_stems_run", "Separate Stems")
               .in(RuntimeActions.audioClipMenus())
               .menuOnly()
               .icon("stems_icon")
               .visibleWhen(AudioSelection::isSingleAudio)
               .onRun(SeparateStems::run);
    }
}
