# Transport: Capture MIDI

Author: kalifrn. Based on Bitwig Superpowers.

Played something good without recording? **Capture MIDI** turns what you just played into a note clip. The mod keeps the notes you play, on any MIDI keyboard or controller Bitwig reads and on Bitwig's computer keyboard, whether or not the transport is running and whether or not anything is armed.

## Using it

Run **Capture MIDI**:

- from the **Play menu** (the arrow beside the transport's Play button),
- with a shortcut (assign one in Settings > Shortcuts; it is listed under General),
- from the Commander,
- or from the right-click menu of an **empty clip launcher slot**, to put the clip in that slot.

From the Play menu, a shortcut or the Commander, the clip goes into the arranger, on the **selected track**. If that track can't hold a note clip (an audio track, say), or already has a clip where the new one would go, it goes on a **new track** instead, so nothing you have is overwritten. Bitwig's notification bar says how many notes were captured and where.

Each capture is one Undo step. After a capture, the next one holds only what you play afterwards.

## Where the clip lands in time

- **While the transport plays**, every note keeps the beat you played it on, and the clip covers the whole bars around them. A note played just before a bar line counts as that bar's downbeat.
- **While stopped** there is no beat to follow: the first note you played becomes the clip's downbeat, at the project's tempo, and the clip starts at the bar of the play position.

Notes are not quantised: use Bitwig's Quantize on the new clip if you want them on the grid. Notes you released while holding the sustain pedal last until you let go of the pedal, so the clip sounds as you played it; the pedal itself isn't written into the clip.

## Settings

Settings > Mods > Capture MIDI:

- **Keep the last (seconds)**: how far back a capture can reach (10 to 600, default 120).
- **New phrase after... (seconds)**: a silence longer than this starts a new phrase, and only the latest phrase is captured (0 to 60, default 5). **0 turns phrases off**: everything played in the kept time is captured.

## Needs

- Framework: Actions and Menus 0.7.0-dev.3 or later
- Framework: Settings
