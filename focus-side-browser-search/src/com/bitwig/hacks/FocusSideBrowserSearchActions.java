package com.bitwig.hacks;

import bml.actions.ActionProvider;
import bml.actions.Actions;

/** The action, declared through the Action and Menu framework: shortcut and Commander only (Bitwig's Browser category), no menus. */
public final class FocusSideBrowserSearchActions implements ActionProvider {
    @Override
    public void declare(Actions actions) {
        actions.action(FocusSideBrowserSearch.ACTION_ID, FocusSideBrowserSearch.LABEL)
               .category("browser") // Bitwig's own Browser category
               .onRun(context -> FocusSideBrowserSearch.focus(context.widget())); // the project window
    }
}
