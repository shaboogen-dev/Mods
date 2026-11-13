# Inspector: Show Chord Name

Names the chord the selected notes make, in the Note Inspector.

- Author: kalifrn
- Based on Bitwig Superpowers.
- Needs: Framework: Piano Roll and Framework: Inspector.

## Using it

Select notes in a note editor. The **Chord** block, right after Bitwig's NOTE block, shows the chord's name in its **Name** row. It follows the selection as you select and deselect notes, move them to other keys, and press Undo or Redo. It only reads: nothing in the project changes.

## What it shows

| Selected | Name |
|---|---|
| Nothing, or one note | `-` |
| One note in several octaves | The note and "octaves": `C octaves` |
| Two different notes (octaves counted once) | The interval up from the lower one: `Minor 3rd`, `Perfect 5th`, `Tritone` |
| A chord in root position | Its chord symbol: `C`, `Cm`, `C7`, `Cmaj7`, `Cm7b5`, `Cdim7`, `Csus4`, `C6/9`, `C13` |
| A chord with another note lowest | The chord, a slash and the lowest note: `C/E`, `Am7/E` |
| A chord with its fifth left out | The chord as if the fifth were there: C E Bb is `C7` |
| Anything else | `-` |

Only which notes sound, and which is lowest, count. Timing, octaves and repeated notes don't, so an arpeggio shows the chord it spells. Notes from several clips are named together.

**Which chord, when the notes could be several.** The lowest note is the root when the notes make a complete chord on it (C E G A is `C6`). Otherwise the more common chord on another note is named, over the lowest note (E G A C is `Am7/E`). A chord with its fifth left out is only named when the notes make no complete chord at all (C E A is `Am/C`, not C6 without its fifth).

**Spelling.** Sharps or flats follow Bitwig's key signature where the earliest selected note starts: keys on the flat side (F, Bb, Eb, Ab, Db major and their modes, such as D minor) use flats, keys on the sharp side (G, D, A, E, B, F# major and their modes, such as E minor) use sharps. Other scales are spelled as the major key of their root, or of their relative major when they have a minor third. In C major or A minor, and on Bitwig versions without a key signature, the names most chord charts use: Db, Eb, F#, Ab, Bb.

## Cost

The name is worked out when the Note Inspector is built and again only when the selected notes change or something Undo can undo changes, and only while a Chord block is on screen.
