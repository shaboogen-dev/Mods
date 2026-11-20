# Track: Exclusive Arm

> **Built against**: Bitwig 6.1.4 (Windows x64)
>
> **Will map to**: All Bitwig versions Patchbay supports on Windows, Mac and Linux
>
> **Mod ID**: exclusive-arm
>
> **Prerequisites**: **Framework: Settings** 1.0.0 or later, and **Framework: Actions and Menus** 0.9.0-dev.4 or later.

## What does this do?

This mod comes off the back of a request from *Lokan* to create "exclusive solo but with the round and black button that turns red".

**Track: Exclusive Arm** adds an **Exclusive Arm** setting that mirrors the functionality of **Exclusive Solo** for the **Record Arm** button.

## How do you use it?

* To turn it on, go to **Settings > Mods > Arm > Exclusive Arm**. This is on by default when the mod is installed.
* While **Exclusive Arm** is off, arming tracks works exactly as it did before with one exception:
  * **Shift-Click** an arm button to exclusively arm that track.
* While **Exclusive Arm** is on:
  * The default arm behaviour when clicking a track, or using any other mechanism to activate **Record Arm** is to exclusively arm the selected track.
  * **Ctrl-clicking** (Cmd-clicking on Mac) an arm button additionally arms the track.
    * This can also be used to deactivate **Record Arm** on a track.
  * **Shift-click** follows the same functionality as **Ctrl-click**.

## Things to note

* A track that can't be armed (one with no recording input) is left as it is, and so are the other tracks.
* Arming several selected tracks with one click arms them all together.
* Opening a project doesn't change it. Tracks that were saved armed stay armed.
* Arming isn't an Undo step in Bitwig, with or without this mod, so Undo after arming undoes your previous edit.
