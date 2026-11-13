package com.bitwig.mods.remotecontrols;

import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.actions.Menu;

/** The action, declared through the Action and Menu framework. */
public final class VisibleRemoteControlsActions implements ActionProvider {
    @Override
    public void declare(Actions actions) {
        actions.action(VisibleRemoteControlsRandomizer.ACTION_ID, VisibleRemoteControlsRandomizer.ACTION_LABEL)
               .in(Menu.DEVICE, Menu.selectionToolbar("device"))
               .onRun(context -> VisibleRemoteControlsRandomizer.randomizeFromAction(
                       context.device(), context.menu() == null ? "shortcut" : context.menu().id()));
    }
}
