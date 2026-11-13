package com.bitwig.mods.audiotomidi;

import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.actions.Submenu;
import com.bitwig.mods.audioanalysis.AudioSelection;
import com.bitwig.mods.audioanalysis.RuntimeActions;

/** "Audio to MIDI" > Melody, Harmony, Drums in an arranger audio clip's right-click menu. */
public final class MidiActions implements ActionProvider {
    @Override
    public void declare(Actions actions) {
        Submenu menu = actions.submenu("audio_to_midi", "Audio to MIDI").in(RuntimeActions.audioClipMenus());
        add(menu, "audio_clip_to_midi_melody", Target.MELODY, "melody_icon");
        add(menu, "audio_clip_to_midi_harmony", Target.HARMONY, "harmony_icon");
        add(menu, "audio_clip_to_midi_drums", Target.DRUMS, "drums_icon");
    }

    private static void add(Submenu menu, String id, Target target, String icon) {
        menu.action(id, target.label)
            .menuOnly()
            .icon(icon)
            .visibleWhen(AudioSelection::isSingleAudio)
            .onRun(c -> AudioToMidi.run(c, target));
    }
}
