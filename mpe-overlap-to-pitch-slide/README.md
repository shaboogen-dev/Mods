# Piano Roll: Overlaps to Pitch Slides

For Bitwig 6.0 to 6.1.4, on Windows, macOS and Linux. Needs **Framework: Actions and Menus**, **Framework: Piano Roll** and **Framework: Notifications**.

Two edits that turn overlapping notes into pitch slides, for example notes played legato on an MPE controller.

## Using it

Select the notes in a note editor (arranger or clip launcher), then right-click one of them, or open the note toolbar's dropdown menu, and open **Overlapping Notes**:

- **Convert Overlap to Pitch Slide**: for each pair of overlapping notes, the earlier note gets a pitch slide (Micro-pitch expression) across the overlap that reaches the later note's pitch, and the later note is trimmed to start where the earlier one ends. Both notes are kept.
- **Merge Overlapping Notes with Pitch Slide**: each run of overlapping notes becomes its first note, lengthened to the run's end, with a pitch slide across each overlap to the next note's pitch, holding that pitch until the next slide. The other notes of the run are removed.

Each edit is one Undo step. Convert and Merge leave alone selected notes that don't overlap another, and handle separate overlaps independently, including a chain of notes each overlapping the next.

The two are Bitwig actions: they can be given shortcuts (Settings > Shortcuts) and run from the Commander, on the notes selected in the note editor. From a shortcut, the Commander or the toolbar dropdown, the selection must be in exactly one note editor; otherwise nothing happens (right-click the notes instead).

## Existing pitch expression

- **Convert:** the earlier note keeps its pitch points before the overlap; points from the overlap on are replaced by the slide. The later note keeps its points after the trim, at the same times.
- **Merge:** the first note keeps its pitch points before the first overlap; the removed notes' expressions are lost.

## What it refuses

Nothing is changed, and the reason is shown in Bitwig's notification (for example "Overlaps to Pitch Slides: no selected notes overlap") and written to the session log, when the selection has fewer than two notes, notes from different clips, two notes starting together, a note lying inside another, three notes overlapping at once, no overlapping notes, overlapping notes of the same pitch or more than 96 semitones apart, or pitch expression with curves (only straight lines between points are supported, so curves are never flattened).

## Using the edits from another mod

A mod that requires this one can call Convert and Merge in `com.bitwig.mods.overlappingnotes`. `PitchSlide.LABEL` and `Merge.LABEL` are their names. `PitchSlide.plan(notes, edit)` and `Merge.plan(notes, edit)` add the edit of `notes`, sorted by start, to a `bml.pianoroll.Edit` of the caller's, and return what it will do, for a log. They throw `Refused` for the reasons above, with nothing changed, and the caller applies the `Edit`.

Action IDs: `bml_mpe_overlap_convert_to_pitch_slide`, `bml_mpe_overlap_merge_with_pitch_slide`. Make Legato (Later → Earlier) is a separate mod, Piano Roll: Make Legato (Later → Earlier).
