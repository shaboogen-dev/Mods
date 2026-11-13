package com.bitwig.mods.selectedtracksview;

import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.actions.Menu;

/**
 * The mod's one action: a real Bitwig action (a shortcut can be assigned, the Commander lists it)
 * and a row in the track right-click menu, shown there only while the menu's selection holds two or
 * more tracks. It opens the view, so it means something on its own: no action exists that only works
 * while the view is open.
 */
public final class SelectedTracksActions implements ActionProvider {
    @Override
    public void declare(Actions actions) {
        actions.action("selected_tracks_view_show", "Show Devices of Selected Tracks")
               .in(Menu.selection("track"))
               .category("panel_management")
               .visibleWhen(context -> Tracks.ofMenu(context).size() >= 2)
               .onRun(SelectedTracksView::toggle);
    }
}
