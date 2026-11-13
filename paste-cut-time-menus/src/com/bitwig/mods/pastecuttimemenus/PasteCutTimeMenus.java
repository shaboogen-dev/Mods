package com.bitwig.mods.pastecuttimemenus;

import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.actions.Menu;

/**
 * Puts Bitwig's own Paste Time and Cut Time, with Bitwig's Insert Silence and Remove Time icons, in the
 * menus where Bitwig shows its other time actions (Insert Silence, Duplicate Time, Remove Time): an
 * arranger time selection in clip lanes or automation lanes, in the right-click menu and the selection
 * toolbar's menu. These are the only two time-selection kinds where Bitwig's menu builder shows its
 * Timeline group.
 */
public final class PasteCutTimeMenus implements ActionProvider {
    @Override
    public void declare(Actions actions) {
        Menu[] menus = {
                Menu.selection("arranger_clip_time_selection"),
                Menu.selectionToolbar("arranger_clip_time_selection"),
                Menu.selection("arranger_automation_time_selection"),
                Menu.selectionToolbar("arranger_automation_time_selection"),
        };
        actions.bitwigAction("paste_and_push").in(menus).icon("paste_time_icon"); // Paste Time
        actions.bitwigAction("cut_and_pull").in(menus).icon("cut_time_icon"); // Cut Time
    }
}
