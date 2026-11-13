# Arranger: Default Track Height

Sets the height a new track or group track starts at in the Arranger, and adds an action that sets every track to that height.

- Mod ID: `default-track-height`
- Built against Bitwig 6.1.4, Windows x64.
- Needs Framework: Settings and Framework: Actions and Menus.

## Setting

In **Settings > Mods > Default Track Height**: **Height**, 21–600, default 43 (Bitwig's own start height).

A track or group track created from nothing starts at this height. It is the track's ordinary lane height: it can be dragged as usual and is saved with the project, so Bitwig without this mod opens the project with the same sizes. A change applies to the next new track; existing tracks keep their height.

| How the track comes about | Height |
|---|---|
| New instrument, audio or hybrid track; new effect track | the setting |
| A device dragged into empty space in the track list, or anything else that makes a new track of a type for it | the setting |
| Duplicate, copy and paste | the source's |
| Track preset, template, opening a project | what was saved |
| New group track, or grouping the selected tracks | the setting |
| Master track | Bitwig's own |

## Set All Tracks to Default Height

An action for **Settings > Shortcuts** and the Commander, with no key of its own. It sets every instrument, audio, hybrid, effect and group track in the project to the setting's height, nested tracks and tracks in folded groups included, whatever is selected. The master track keeps its height. The change saves with the project like any other track height. In an empty project, or when every track is at that height already, it does nothing.

**It can't be undone.** Like resizing a track by dragging, it doesn't enter Bitwig's Undo history, so Undo won't bring back the previous heights.

## Display profiles

Bitwig keeps a project's track heights per display profile. The setting and the action write the profile in use; the project's other profiles keep their heights, and a new track starts at Bitwig's height in them.
