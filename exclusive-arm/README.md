# Track: Exclusive Arm

When you arm a track for recording, every other track is disarmed, so only one track is armed at a time. It works however the track is armed: its arm button, Bitwig's arm actions and their shortcuts, or a controller. Loading a project is left as it is.

Undo: arming a track is not an Undo step in Bitwig, with or without this mod. Undo after any kind of arm (click or Shift-click) undoes your previous edit, not the arm.

## Setting

Settings > Mods > Exclusive Arm > **Disarm other tracks when arming** (on by default). Turn it off and arming works as in Bitwig without the mod.

The action **Toggle Exclusive Arm** (Commander, or give it a shortcut in Settings > Shortcuts) turns the setting on and off and shows the new state in Bitwig's notification bar.

## Shift-click

Shift-click a track's arm button, in the arranger's track header or in the mixer, to arm that track and disarm every other track, as Shift-click on a solo button solos one track. It works whether the setting is on or off. Shift-clicking a track that is already armed keeps it armed and disarms the others; to disarm it, click it without Shift. A track that can't be armed (no recording input) is left as it is, and so are the others.

## Needs

- Framework: Settings (`preferences-composition-framework`)
- Framework: Actions and Menus (`action-menu-composition-framework`)

Install both next to this mod.
