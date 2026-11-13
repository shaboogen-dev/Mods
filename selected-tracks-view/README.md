# Device: Selected Tracks View

For Bitwig 6.1.4 (Windows x64); built to run on Bitwig 6.0 to 6.1.4 on Windows, macOS and Linux. Needs Framework: Actions and Menus and Framework: Settings.

Shows the device chains of several tracks at once: one row per selected track, each with the track's name and its full device chain, on the device panel's black background.

## Opening it

Select two or more tracks (Ctrl-click or Shift-click in the arranger or the mixer), then:

- **Right-click** one of them and choose **Show Devices of Selected Tracks**. The entry is shown only while two or more tracks are selected.
- Or run **Show Devices of Selected Tracks** from the Commander, or with a shortcut you assign to it (Settings > Shortcuts, under Panel Management).

With fewer than two tracks selected, the shortcut and the Commander do nothing.

The view shows the tracks that were selected when it opened. Selecting other tracks afterwards doesn't change it. To show other tracks, close the view and open it again.

**Closing it:** run **Show Devices of Selected Tracks** again (whatever is selected), or press **Toggle Expanded Device View** while the view has focus.

Group tracks, effect tracks and the master can be shown too: each row is the track's own device chain.

## Docked or in its own window

The view behaves like The Grid's expanded view:

- It opens docked where Bitwig docks expanded device views: over the arranger in the Arrange view, over the mixer in the Mix view. The Edit view has no place for docked views, so there it opens in its own window, as The Grid's does.
- The **undock** button in its header moves it into its own window; that window's **dock** button moves it back. Until Bitwig is closed, the view opens the way you left it: in its own window after you undock it, docked again after you dock it. Each time Bitwig starts, it opens docked.

The window's size and place are remembered.

## In the view

- The rows are in Bitwig's track order, top to bottom, whatever order the tracks were selected in.
- Each row starts with a narrow header, drawn like a device's header: the track's name, reading bottom to top, under a bar in the track's colour. Renaming or recolouring a track shows at once.
- Next to it is the track's device chain, as the device panel shows it and at the device panel's height: edit parameters, add devices with **+**, drag devices within a row and between rows, open nested chains.
- A line separates the rows.
- The view scrolls with Bitwig's scroll bars: up and down when the rows don't fit, sideways when a row is wider than the view.
- Deleting a shown track removes its row. When every shown track is deleted, the view closes. Undoing the deletion doesn't bring the row back: run the view again.

The device panel goes on showing the current track as usual.

## Settings

**Settings > Mods > Selected Tracks View > Hide the device panel while the view is open** (off by default). When it's on, opening the view hides Bitwig's device panel, as Toggle Device Panel does, and closing the view shows it again. If the device panel was already hidden when the view opened, it stays hidden.

## What is kept

Nothing is saved into the project, so the project opens unchanged without the mod. The window's size and place and the setting are kept in the mod's settings; the docked or floating choice lasts until Bitwig is closed.
