# Audio Clip: Detect Key

Author: kalifrn. Based on Bitwig Superpowers.

Estimates an audio clip's key and adds it to the clip's name, as in "Chords (Ebmaj)".

## Use

1. Right-click an audio clip in the arranger > **Detect Key**.
2. It runs in the background (seconds to a minute or two). **Audio Analysis > Cancel Audio Analysis Job** stops it.
3. Bitwig's dialog shows the key, its confidence and the relative key (the major or minor key with the same notes), and the new name. **OK** renames the clip; **Undo** ("Detect Key") brings the old name back. Detecting again replaces the key in the name rather than adding a second one.

**Settings > Mods > Detect Key > After detecting...:** **Ask** (default: asks whether to rename the clip), **Rename** (renames the clip with no dialog; a message in the notification centre gives the key), or **Show** (only gives the key, in a message in the notification centre that stays until you close it; nothing changes).

## Needs

- **Helper: Audio Analysis**, with **Set Up Audio Analysis** done (right-click an audio clip > Audio Analysis). Until then, Detect Key shows a notification saying how.
- Framework: Actions and Menus 0.8.0-dev.1 or later, Framework: Settings 1.0.0 or later.

## Notes

- A major key and its relative minor share every note, so they are the hardest to tell apart. The estimator keeps the engine's major/minor reading unless the notes contradict it, and the dialog names the other one so you can judge.
- The whole audio file is analysed.
