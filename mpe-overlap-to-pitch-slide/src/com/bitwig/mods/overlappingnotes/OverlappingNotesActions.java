package com.bitwig.mods.overlappingnotes;

import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.actions.Menu;
import bml.actions.Submenu;

/**
 * The two actions, declared through Framework: Actions and Menus, in an "Overlapping Notes" submenu of
 * the note right-click menus and the note toolbar dropdowns.
 */
public final class OverlappingNotesActions implements ActionProvider {
    private static final String[] NOTE_KINDS = {
        "arranger_clip_instrument_note", "arranger_instrument_note",
        "launcher_clip_instrument_note", "launcher_instrument_note"
    };

    @Override
    public void declare(Actions actions) {
        Submenu submenu = actions.submenu("overlapping_notes", "Overlapping Notes").in(noteMenus());
        // Action IDs are stored in users' shortcut settings: never change them.
        submenu.action("bml_mpe_overlap_convert_to_pitch_slide", PitchSlide.LABEL)
               .icon("pitch_icon")
               .onRun(context -> OverlappingNotes.run(PitchSlide.LABEL, context.selection(), PitchSlide::apply));
        submenu.action("bml_mpe_overlap_merge_with_pitch_slide", Merge.LABEL)
               .icon("pitch_icon")
               .onRun(context -> OverlappingNotes.run(Merge.LABEL, context.selection(), Merge::apply));
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
