package com.bitwig.mods.addtodrummachine;

import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.actions.Menu;

/** The action: a row in the browser's right-click menu; also for a shortcut and the Commander. */
public final class AddToDrumMachineActions implements ActionProvider {
    @Override
    public void declare(Actions actions) {
        actions.action("add_to_drum_machine_add", "Add to Drum Machine")
               .in(Menu.BROWSER_ITEM)
               .category("browser")
               .visibleWhen(AddToDrumMachine::offered)
               .onRun(AddToDrumMachine::run);
    }
}
