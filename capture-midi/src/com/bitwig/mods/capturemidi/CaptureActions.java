package com.bitwig.mods.capturemidi;

import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.actions.Menu;

/** The Capture MIDI action: shortcut, Commander, the Play menu and the empty launcher slot menu. */
public final class CaptureActions implements ActionProvider {
    @Override
    public void declare(Actions actions) {
        actions.action("capture_midi_capture", "Capture MIDI")
               .in(Menu.PLAY, Menu.CLIP_SLOT)
               .icon("capture_icon")
               .onRun(context -> Capture.run(context.slot()));
    }
}
