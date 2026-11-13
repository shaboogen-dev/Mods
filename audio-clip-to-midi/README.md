# Audio Clip: Audio to MIDI

Author: kalifrn. Based on Bitwig Superpowers.

Transcribes an audio clip's melody, harmony or drums into a MIDI clip.

## Use

1. Right-click an audio clip in the arranger > **Audio to MIDI** > **Melody**, **Harmony** or **Drums**.
2. It runs in the background (from seconds to a few minutes), shown in Bitwig's notification centre. **Audio Analysis > Cancel Audio Analysis Job** stops it.
3. The notes arrive as a MIDI clip on a new instrument track next to the clip's track, from where the clip starts and as long as the clip. One **Undo** removes it.

The first run analyses all three; Melody, Harmony and Drums of the same clip afterwards come from the cache at once.

**Settings > Mods > Audio to MIDI:**

- **Note detail percentage**, 5 to 50 (15 by default): higher finds more notes, lower keeps only the clear ones.
- **Separate stems first, when stem separation is set up** (on by default): melody is transcribed from the vocals stem, harmony from the other stem and drums from the drums stem, which gives cleaner results. Without stem separation set up, the full mix is used and the message at the end says so.

## Needs

- **Helper: Audio Analysis**, with **Set Up Audio Analysis** done (right-click an audio clip > Audio Analysis). Until then, Audio to MIDI shows a notification saying how. Stem separation is optional.
- Framework: Actions and Menus 0.8.0-dev.1 or later, Framework: Settings 1.0.0 or later.

## Notes

- The new track is an empty instrument track: add an instrument to hear it.
- The whole audio file is analysed and spread over the clip's length, so a clip that plays its whole file at its own tempo lines up; one that plays part of its file doesn't.
- Transcription uses Spotify's Basic Pitch (Apache 2.0).
