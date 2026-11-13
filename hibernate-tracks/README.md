# Track: Hibernate Tracks

Frees the CPU a track uses while keeping it in the project, ready to come back.

**Hibernate Track** renders the selected tracks to new audio tracks and deactivates the originals with Bitwig's own Deactivate. The new track takes the original's place in the track list, and the original sits directly below it, so with deactivated tracks shown, or in Bitwig without this mod, each original is right under its `^` track. **Wake Track** rebuilds the original from what's on the new track, activates it and deletes the new track. **Make Permanent** keeps the audio and deletes the original.

All three are in the track header menu and can be given a shortcut in **Settings > Shortcuts**, category **Arranger**. None has a key of its own.

The original stays in the project, with its clips and devices untouched, until Wake or Make Permanent. Nothing is written outside the project.

## What the new track takes over

- The render is taken after the track's effects and before its fader, in 32-bit float at the project's sample rate, by Bitwig's own bounce. It always runs from the first clip to 32 beats past the last one, so that's how long Hibernate takes, however short the tails turn out to be.
- The new track gets the original's fader, pan, mute, sends (amount, on/off, pre or post) and output, so it sits in the mix the same way.
- Whatever listened to the original's audio (a sidechain, another track's input) listens to the new track instead, and goes back on Wake.
- Changes to those mixer settings made while hibernated carry back on Wake.

## Moving things while hibernated

The render is cut into one clip per original clip, each in that clip's place, and gaps stay empty. Move, duplicate, delete or split these as you like. Wake rebuilds the originals from what's on the new track then, in one undo step:

- **Moved:** the originals come back at the new place.
- **Duplicated:** they're copied, one for each.
- **Deleted:** they don't come back, and the notification bar says so. Ctrl+Z after Wake recovers them.
- **Split or trimmed:** an original comes back cut to the part its rendered clip still covers, as if you'd cut it there in Bitwig. A clip goes with a rendered clip if the two overlap in the render's own time, so a piece of tail alone brings nothing back. A split that falls inside a note behaves as cutting a clip there does.
- **Stretched, looped, reversed, slipped or otherwise edited:** it can't be matched, so nothing is guessed. Originals no rendered clip can claim go back where they started, and the notification bar says so.
- **Overlaps:** a returning clip that would overlap another goes on a new deactivated track called "(overlaps)", at the place it would have played, and the notification bar says so. Nothing is lost.
- **Anything else on the new track** comes back to the original track. Rendered clips are recognised by the file they play, not by their name, so renaming them changes nothing.

Mixer automation doesn't move with a rendered clip, as in Bitwig generally.

## Settings

In **Settings > Mods > Hibernate Tracks**. Each applies when Hibernate runs.

**Auto hide deactivated tracks** (on by default). Hibernate turns on Bitwig's hide deactivated tracks if it's off, so deactivated originals are out of sight. Waking or making permanent the last hibernated track in the project turns it off again, only if this mod turned it on. If you turned it on yourself first, it stays on. With this option off, the mod doesn't touch it. The setting belongs to each project, and what this mod did is remembered per project in the mod's settings. The mod can't tell if you turned it off and back on by hand in between, and undo doesn't change it: an undone Hibernate leaves it on until the next Wake.

**Preserve tails** (on by default). On: a rendered clip runs to the next clip's start or the end of its tail, whichever comes first. Off: it ends exactly where its clip ends.

**Tail volume threshold** (−60.0 dB by default, −96 to −24). A tail ends 100 ms after the last 256-sample block above this level, looking backward from the end of the render or the next clip's start, and never before its own clip ends. So a delay that goes quiet and comes back stays, as long as it comes back inside the 32 beats. It's used only when Preserve tails is on. If the file can't be read, or the tempo changes along the render, tails aren't measured: each rendered clip runs to the next clip's start, and the last keeps its full length.

## Make Permanent

It asks first, because it destroys the original: its clips and devices. It keeps the new track and its audio, deletes the original, removes the link from the new track's comment, and drops a leading `^` from its name, so `^Lead` becomes `Lead`. Ctrl+Z restores it all in one step, while the project is open.

## Deleting

Deleting a hibernated track deletes its deactivated original with it, in one step: Ctrl+Z brings both back, still paired. If you delete the original (with deactivated tracks shown), its hibernated track stays as a plain audio track: the link leaves its comment and the `^` its name, also in one step.

While Hibernate keeps deactivated tracks hidden, an Undo or Redo that would show them again leaves them hidden.

## Going back without the mod

The new track's comment says which track it came from. Activate that track and delete the new one. The project opens in Bitwig without this mod.

If the comment has been removed, or the original is gone, Wake Track says so and changes nothing, and you can do it by hand.

## What it won't do

- Groups, effect tracks and the main track are refused.
- A track with automation on its fader, pan, mute or a send is refused.
- It refuses while recording, and a track with no clips in the arranger.
- Launcher clips aren't rendered.
- A sidechain tapping a point inside the original's device chain has no equivalent on the new track. It stays where it is and hears nothing until Wake; the notification bar says how many.
- Notes sent from the original to other tracks stop while it is hibernated.
- Crossfade setting and track delay aren't copied.
- Edits made to the original while it renders aren't in the render.
- If the new track is moved into another group, Wake Track leaves the original in its own group, and says so.

Each Hibernate, Wake and Make Permanent writes one line to the mod's log, with how long it took and what changed.

Requires **Framework: Actions and Menus** and **Framework: Settings**.

## Release

- Mod ID: `hibernate-tracks`
- Author: `shaboogen`
- Built against: Bitwig Studio 6.1.4 / Windows x64
- License: MIT
- Inspired by Bitwig Superpowers by kalifrn.
