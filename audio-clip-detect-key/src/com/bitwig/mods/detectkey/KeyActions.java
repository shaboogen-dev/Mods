package com.bitwig.mods.detectkey;

import bml.actions.ActionProvider;
import bml.actions.Actions;
import com.bitwig.mods.audioanalysis.AudioSelection;
import com.bitwig.mods.audioanalysis.RuntimeActions;

/** "Detect Key" in an arranger audio clip's right-click menu. */
public final class KeyActions implements ActionProvider {
    @Override
    public void declare(Actions actions) {
        actions.action("audio_clip_detect_key_run", "Detect Key")
               .in(RuntimeActions.audioClipMenus())
               .menuOnly()
               .icon("key_icon")
               .visibleWhen(AudioSelection::isSingleAudio)
               .onRun(DetectKey::run);
    }
}
