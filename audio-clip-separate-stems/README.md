# Audio Clip: Separate Stems

Author: kalifrn. Based on Bitwig Superpowers.

Splits an audio clip into vocals, drums, bass and other, and adds them as new audio tracks.

## Use

1. Right-click an audio clip in the arranger > **Separate Stems**.
2. It runs in the background (several minutes for a song; Bitwig stays usable), shown with its progress in Bitwig's notification centre. **Audio Analysis > Cancel Audio Analysis Job** stops it.
3. The stems arrive as new audio tracks below the clip's track, starting where the clip starts. One **Undo** removes them all.

Separating the same file again is quick: the stems are kept in Helper: Audio Analysis's cache.

**Settings > Mods > Separate Stems** chooses which stems become tracks (all four by default).

## Needs

- **Helper: Audio Analysis**, with **Set Up Audio Analysis** and **Set Up Stem Separation** done (right-click an audio clip > Audio Analysis). Until then, Separate Stems shows a notification saying how. See that mod's README for what is downloaded and where.
- Framework: Actions and Menus 0.8.0-dev.1 or later, Framework: Settings 1.0.0 or later.

## Notes

- Bitwig has said stem separation is coming in its next version. This mod may then be unnecessary.
- The whole audio file is separated. The stems start where the clip starts, so a clip that plays its file from the beginning lines up; one that starts later in its file, or plays it stretched, doesn't.
- The stem tracks play the WAVs from the cache until the project is saved with **File > Collect and Save**.
- Separation uses Demucs (htdemucs model, MIT licence) on the computer's processor.
