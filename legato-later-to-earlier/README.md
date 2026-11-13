# Piano Roll: Make Legato (Later → Earlier)

For Bitwig 6.0 to 6.1.4, on Windows, macOS and Linux. Needs Framework: Actions and Menus, Framework: Piano Roll and Framework: Notifications.

Select notes in a note editor (arranger or clip launcher), then right-click one of them, or open the note toolbar's dropdown menu, and choose **Make Legato (Later → Earlier)**.

Taking the selected notes in order of their starts, each note after the first starts where the note before it ends, whether they overlap or have a gap between them. Each note's end stays where it is, and pitch expression is left as it is, at the same times. A pair that already meets is left as it is. The edit is one Undo step.

Bitwig's own Make Legato goes the other way: each selected note's end moves to the start of the next note.

A note it moves earlier doesn't shorten or remove notes that aren't selected: a note of the same pitch in the gap stays, overlapping the moved note.

It's a Bitwig action: it can be given a shortcut (Settings > Shortcuts) and run from the Commander, on the notes selected in the note editor. From a shortcut, the Commander or the toolbar dropdown, the selection must be in exactly one note editor; otherwise nothing happens (right-click the notes instead).

## What it refuses

Nothing is changed, and the reason is shown in Bitwig's notification (for example "Make Legato (Later → Earlier): select two or more notes") and written to the session log, when the selection has fewer than two notes, notes from different clips, two notes starting together, a note lying inside another, or three notes overlapping at once, or when the notes all already meet.

Action ID: `bml_mpe_overlap_make_legato_later_to_earlier` (it keeps the ID it had in Piano Roll: Overlapping Note Operations, so shortcuts assigned to it still work).
