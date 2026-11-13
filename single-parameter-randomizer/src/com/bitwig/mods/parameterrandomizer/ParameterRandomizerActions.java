package com.bitwig.mods.parameterrandomizer;

import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.actions.Menu;

/** The action, declared through the Action and Menu framework. */
public final class ParameterRandomizerActions implements ActionProvider {
    @Override
    public void declare(Actions actions) {
        actions.action(ParameterRandomizer.ACTION_ID, ParameterRandomizer.ACTION_LABEL)
               .in(Menu.PARAMETER)
               .visibleWhen(context -> ParameterRandomizer.settable(context.parameter()))
               .onRun(context -> {
                   if (context.parameter() != null) {
                       ParameterRandomizer.randomize(context.parameter(), "parameter-menu");
                   } else {
                       ParameterRandomizer.randomize(ParameterRandomizer.hoveredParameter(), "shortcut");
                   }
               });
    }
}
