# Piano Roll: MIDI Tools

One block in the Note Inspector for changing selected notes musically.

## Using it

Select notes in a note editor. At the end of the Note Inspector, in the **MIDI Tools** block, **Tool** starts on **None**: no rows, and **Apply** is greyed out.

1. **Choose a tool.** What it would do is drawn in at once, before anything changes:
   - every note it would make, and every note it would change (moved, shortened, on another key), is drawn as it would end up, with Bitwig's eraser stripes, the look the pencil's note has under Pencil Modifiers. These are Bitwig's preview notes: they can't be selected, and they aren't saved or put in the Undo history;
   - the notes it would remove are hidden, as Bitwig hides the notes under a drag, and a changed note shows only in its new form;
   - selected notes the tool leaves exactly as they are stay as they are, without stripes.

   What you see is exactly what Apply will leave. When Apply would move a selected note, as an inversion moves a chord's root, it disappears from its place and shows striped where it goes.
2. **Change its values.** The preview is drawn again with each change. Choosing another tool replaces it.
3. **Click away** (select other notes, or none), edit a note (or make any other change Bitwig can undo), or press Undo or Redo, and the preview goes: the hidden notes come back, the clip is exactly as it was, and the tool is back to None. Choosing None does the same.
4. **Press Apply** to make it permanent, as one Undo step named after the tool. The notes stay selected and the tool goes back to None, so choosing a tool again works on them as they now are.

**You can hear the preview.** With **Play the preview** on (Settings > Mods > MIDI Tools, on by default), while the preview shows, the clip plays as Apply would leave it: with the transport, in a loop or from the launcher. When the preview goes, the clip plays its real notes again. Turn it off and the preview is silent: the clip plays as it is, including the notes hidden from view. Exporting or bouncing while a preview shows may include it, so apply it or click away first. Bitwig's own pencil, dragging or pasting notes also ends the preview.

**What Apply does,** as one Undo step named after the tool:

1. **Clears the range** (Stack Chords, Arpeggiate, Strum, Chop and Chop Rhythm; not Quantize, Time Warp, Note Articulation, Voice Lead Chords or Extract Bass, which only change the selected notes or add a bass under them, and leave the notes around them alone). Every other note in the same clip that overlaps a selected note's time, from that note's start to its end, is removed, **on any key**. A bass note held under a chord, or a melody note crossing it, goes too. A note that only touches the range, ending where it starts or starting where it ends, stays. Each selected note clears its own range: notes in a gap between two selected notes stay.
2. **Applies the tool** to the selected notes.

The preview shows exactly this: the cleared notes are hidden too. A hidden note is hidden over its own time in its key's lane, so in the rare case of two overlapping notes on the same key and channel, the other one is hidden there as well while the preview shows. Where the tool changed a selected note itself, the next tool starts from that note as it now is: see each tool below.

Each tool keeps its own values until Bitwig closes. Number rows are Bitwig's own drag fields: drag up or down, or type a value; double-click one to reset it. A row that runs from below 0 to above 0 starts in the middle and fills up or down from 0. Rate and Phrase are drag fields over their lengths: drag up for longer, down for shorter.

## The tools

**Stack Chords** builds a chord on each selected note.

| Row | What it does |
|---|---|
| **Mode** | Chord builds by chord type. Degrees builds from scale degrees in Bitwig's key signature |
| **Chord** | With Mode on Chord: Major, Minor, Scale Triad, Diminished, Augmented, Sus2, Sus4, Major 7, Minor 7, Dominant 7, Add9, Add11, 9th, 11th. Scale Triad stacks thirds in the key signature and refuses a note outside the scale |
| **Degrees** | With Mode on Degrees: patterns such as 1-3-5. On D in C major, 1-3-5 makes D, F, A. A note outside the scale counts as the first degree |
| **Inversion** | -3 to +3 (default 0). Above 0 lifts that many of the lowest voices an octave; below 0 drops that many of the highest |
| **Octave** | Moves the whole chord, -2 to +2 octaves (default 0) |

The selected note becomes one of the chord's notes and stays selected: when the chord doesn't hold its key, it moves to its own note in another octave, or to the chord's lowest note, and a next Apply builds on that key. The preview shows it gone from its old place and striped where it goes. The other notes are copies of it, from the same start to the same end. A chord that would go outside the MIDI range is refused.

**Arpeggiate** turns held chords into arpeggios that fill each chord's length. Notes of one clip and channel that start together are a chord.

| Row | What it does |
|---|---|
| **Style** | The order of the notes: Up, Down, UpDown, DownUp, Up & Down, Down & Up, Converge, Diverge, Con & Diverge, Pinky Up, Pinky UpDown, Thumb Up, Thumb UpDown, Play Order, Chord Trigger, Random, Random Other, Random Once |
| **Rate** | The length of a step, from shortest to longest: 1/32T, 1/32, 1/16T, 1/16, 1/8T, 1/8, 1/4T, 1/4 (default 1/4). You can also type one, such as 1/16T |
| **Gate** | How much of a step each note fills, 10% to 100% |
| **Steps** | How many times the chord is spread upwards, 1 to 8 |
| **Distance** | How far the last of those steps goes, -24 to +24 semitones. Greyed out while Steps is 1, where there is no step to go anywhere; it keeps its value |

Each chord note becomes the first step at its own key, and the other steps are copies of it, so a next Apply works on those shortened notes. A chord note the arpeggio has no step for is removed. A random style gives the same order for the same chord each time.

**Strum** offsets the notes of a chord in time by their keys. A chord is notes of one clip and channel starting within a 1/64 note of each other, with at least two different keys.

| Row | What it does |
|---|---|
| **Low** | How far the chord's lowest note moves, up to half a beat either way |
| **High** | The same for its highest note. Notes between move in proportion to their keys |
| **Tension** | Bends the spread between Low and High. Above 0 it starts slow and speeds up, below 0 it starts fast and slows down. Greyed out while Low and High are equal, both 0 or any other same value, since every note then moves by the same amount and there is no spread to bend; it keeps its value |

A chord that would start before the clip's beat 0 moves later as a whole, keeping its shape. The strummed notes stay selected where they now are, so a next Apply strums them again from there.

**Chop** cuts each note into equal parts.

| Row | What it does |
|---|---|
| **Parts** | How many, 2 to 64 |
| **Gaps** | Above 0, plays that many parts and rests one. Below 0, plays one and rests that many |
| **Variation** | Moves each part's start and end, up to 40% of a part, inside the note. Where two parts would overlap, the earlier ends where the later begins, as Bitwig keeps no two notes of one key over each other. The same note always varies the same way |

**Chop Rhythm** fits a named 16-step rhythm to the notes, from the earliest selected note's start, so a chord's notes stay together. Notes must be at least a sixteenth long.

| Row | What it does |
|---|---|
| **Family**, **Rhythm** | Eight families of eight rhythms each |
| **Gate** | How far each hit runs towards the next, 0% to 100% |
| **Swing** | Delays every second step by up to almost half a step |
| **Accent** | Moves each hit's velocity towards the rhythm's own. At 0% velocities don't change |
| **Tail** | Short, or Hold Last, where each note's last hit runs to the note's end |
| **Evolve** | Off, or On: the rhythm varies over a phrase, with the rows below |

**Evolve** is for a selected chord progression in one clip. The lowest note sounding in each channel is the bass and keeps the rhythm as it is; the notes above it vary. Keys never change, and the same settings always give the same result.

| Row | What it does |
|---|---|
| **Phrase** | 1, 2, 4 or 8 bars (default 4). The variation answers at the end of each phrase, and a roll leads into the next |
| **Variation** | How much the notes above the bass vary, 0% to 100%: added and moved hits, ratchets (quick repeats) above about 80%, and short bursts at phrase ends with Roll Off |
| **Cycles** | Locked: every voice uses the rhythm as it is. Upper 12 and Upper 15 squeeze the whole 16-step rhythm into 12 or 15 sixteenths for the notes above the bass, so they drift against it. 7-12-16: the second voice from the bottom at 12, the ones above at 7 |
| **Roll** | Off, or a roll in the last half beat of each phrase: Stepped (slow then fast), Accelerate, Decelerate, Triplet |
| **Harmony** | Strict, or the notes above the bass change chord a sixteenth or an eighth early or late, where one chord runs straight into the next. The bass changes where it was written |

A bar is four beats here: Evolve doesn't read the project's time signature, so in another time signature a phrase still counts four-beat bars. Hits closer together than a hundredth of a beat join into one. A setting that would leave a note with no hit is refused.

In both Chop tools, the first slice of a note is the note itself and the others are copies, so each keeps everything the note holds. Only the first slice stays selected, so a next Apply chops that slice. A note with no hit in a rhythm is removed. A result of more than 8192 notes is refused.

**Quantize** pulls the selected notes towards the grid. It clears nothing: only the selected notes move, and they stay selected.

| Row | What it does |
|---|---|
| **Grid** | Editor, the grid the note editor shows (its fixed size and subdivision, or the adaptive grid's size at the current zoom, straight), or a fixed size: 1/4 to 1/32, with triplets. Grid lines count from the clip's beat 0 |
| **Snap** | Start moves each note so it starts on the nearest grid line, keeping its length. End moves only its end to the nearest line. Both moves its start and its end, each to its own nearest line |
| **Amount** | How far of the way to the grid line, 0% to 100%. At 50% a note goes half way |

A note is never left shorter than a hundredth of a beat. Notes already on the grid don't change and aren't striped. A note that lands on another note of its key, selected or not, doesn't cut it: both stay, over each other, as the preview shows them; the same goes for Time Warp. With Editor chosen and an editor showing no grid, Quantize says so and does nothing. The preview is drawn with the grid as it was when it was drawn: if you zoom an adaptive grid while it shows, Apply still does what the preview shows.

Bitwig has its own Quantize (the Note Inspector's Quantize, or Edit > Quantize...), with a start amount, an end amount, its own grid settings, Shuffle and Humanize. This one does less, but shows its result before you apply it.

**Time Warp** bends the timing of the selected notes along a curve, between the earliest selected note's start and the latest one's end, separately in each clip. Those two points stay where they are; everything between moves. It clears nothing, and the notes stay selected.

| Row | What it does |
|---|---|
| **Mode** | Speed Up spreads the notes near the start of the range apart and squeezes those near its end together, so the notes come faster and faster, an accelerando. Slow Down is the reverse: the notes start crowded and spread out, a ritardando |
| **Amount** | How strongly the timing bends, 0% (no change) to 100% |
| **Lengths** | Keep moves each note and keeps its length. Warp bends its end along the same curve, so notes get longer where the timing spreads and shorter where it squeezes |

A note's place in the range, from 0 at its start to 1 at its end, is raised to a power: at 100%, 1/4 for Speed Up and 4 for Slow Down, and less the lower the amount. The notes keep their order. A note is never left shorter than a hundredth of a beat. With one note, or with every note starting together and Lengths on Keep, there is nothing to warp.

**Note Articulation** changes how long the selected notes are held. Only their ends move; their starts stay. It clears nothing.

| Row | What it does |
|---|---|
| **Style** | Legato holds each note up to the next later start among the selected notes of its clip, whatever its key (the last ones keep their end). Tenuto keeps the written length. Staccato holds each note for half of the shorter of its own length and the shortest distance between two of the selection's starts (with only one start, half its own length) |
| **Offset** | Adds to every note's length, or takes from it, up to a whole grid step, -100% to +100% |
| **Variation** | Moves each note's end by up to a grid step: above 0 only shorter, below 0 only longer. The same note always varies the same way |
| **Grid** | The step Offset and Variation measure in: Editor, the grid the note editor shows, or a fixed size, 1/4 to 1/32 with triplets. Greyed out while Offset and Variation are both 0, where it isn't used |

A note is never left shorter than a hundredth of a beat. A lengthened note that reaches another note of its key doesn't cut it: both stay, over each other, as the preview shows. Piano Roll: Overlapping Note Operations has Make Legato too; this one shows its result first and adds Tenuto, Staccato, Offset and Variation.

**Voice Lead Chords** re-voices a selected chord progression so each chord moves as little as it can from the one before. Every note keeps its pitch class and its timing and only changes octave: no note is added or removed. Notes of one clip and channel starting within a 1/50 of a beat of each other are a chord; a note held from one chord into the next keeps its key and the next chord is voiced around it. The first chord stays as it is unless Bass, Inversion or Voicing asks otherwise.

| Row | What it does |
|---|---|
| **Leading** | Smooth: each chord as close as it can be to the one before. Off: each chord only follows the rows below |
| **Bass** | Free: any note of the chord may end up lowest. Keep Lowest: each chord's lowest note stays where it is. Root: the chord's root goes to the bass (a chord it doesn't recognise keeps its lowest note). Lowest -1 Oct: after everything else, each chord's lowest note goes down an octave |
| **Inversion** | Keep, or which of the chord's notes, counted from the lowest, goes to the bass: 0 the lowest, 1 the next, -1 the highest, and so on |
| **Voicing** | Keep, or a jazz layout of each chord: Close, Open, Drop 2, Drop 3, Drop 4, Drop 2 & 3, Drop 2 & 4. These need chords of enough different notes (3, or 4 for Drop 4 and Drop 2 & 4) without octave doublings |

A moved note that lands on a note already on its new key, selected or not, cuts it there, as Bitwig's own note insert does: what's left of that note before and after stays, and the preview shows the cut. Settings that can't be met (a held note in the way, too few notes for a voicing) are refused with a notification, and so is a progression that already moves as little as it can.

**Extract Bass** finds the root of each chord of a selected progression, using the chords around it and Bitwig's key signature, and makes a bass line close to the chords that moves smoothly. Notes of one clip and channel starting within a 1/50 of a beat are a chord. With no key signature to read, the chords alone decide.

| Row | What it does |
|---|---|
| **Output** | Add Below: the chords stay and each gets a bass note, a copy of its root note (or its lowest note) with its start, end and velocity, striped as new in the preview. A chord that already has a note on the bass's key gets none. Replace Chords: each chord becomes its bass note, as the original did for a copy of the chords on a bass track: the note that carries the root moves to the bass's key and the chord's other notes are removed |
| **Octave** | Where the bass is looked for, -2 to 0: -1 (default) about an octave under each chord's lowest note, -2 two octaves under, 0 at the chord's lowest note |

A bass note that lands on a note already on its key cuts it there, as Bitwig's own insert does, and the preview shows it.

## Undo and notes

Saved notes are plain notes: anything that opens the project without this mod sees them as they are. Each note keeps everything else it holds: chance, expressions and so on.

Each Apply is one Undo step, named after the tool, and brings back any cleared notes too.

With None chosen, nothing runs. While a tool is chosen, the mod listens for a new selection, edits, Undo and Redo, to end the preview.

## Credit

Based on Bitwig Superpowers by kalifrn.

Needs Framework: Piano Roll, Framework: Inspector and Framework: Notifications.
