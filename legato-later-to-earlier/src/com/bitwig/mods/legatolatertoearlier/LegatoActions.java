package com.bitwig.mods.legatolatertoearlier;

import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.actions.Menu;

/** The action, declared through Framework: Actions and Menus, in the note right-click menus and toolbar dropdowns. */
public final class LegatoActions implements ActionProvider {
    private static final String[] NOTE_KINDS = {
        "arranger_clip_instrument_note", "arranger_instrument_note",
        "launcher_clip_instrument_note", "launcher_instrument_note"
    };

    @Override
    public void declare(Actions actions) {
        // Stored in users' shortcut settings: never change it. It keeps the ID it had in Overlapping Notes.
        actions.action("bml_mpe_overlap_make_legato_later_to_earlier", Legato.LABEL)
               .in(noteMenus())
               .icon("legato_icon")
               .onRun(context -> Legato.run(context.selection()));
    }

    /** Each note kind's right-click menu and toolbar dropdown (the dropdown gives no selection: the shortcut path). */
    private static Menu[] noteMenus() {
        Menu[] menus = new Menu[NOTE_KINDS.length * 2];
        for (int i = 0; i < NOTE_KINDS.length; i++) {
            menus[2 * i] = Menu.selection(NOTE_KINDS[i]);
            menus[2 * i + 1] = Menu.selectionToolbar(NOTE_KINDS[i]);
        }
        return menus;
    }
}
