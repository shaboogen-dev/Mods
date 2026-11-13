# Arranger: Cue-Based Arrangement Helper

Moves whole sections of an arrangement, as cue markers define them: a section runs from a cue marker to the next one, and the last one to the end of the arrangement. Moving a section moves its cue marker and everything on the tracks in that time, as one Undo step, "Move Song Section".

- Mod ID: `cue-based-arrangement-helper`

## In the Project Panel

In the Project Panel's cue marker list, drag a cue marker's row up or down: a line shows where its section will go, between two cue markers or after the last one. Release to move the section there. Releasing where it already is changes nothing, and a drag shorter than 8 px is a click.

Dragging a cue row doesn't start Bitwig's own drag and drop of the row.

## In the arranger overview

In the overview above the arranger (the cue marker sections), hold **Alt** and drag a section left or right: a line shows the nearest cue marker boundary (or the end of the arrangement) where it will go, and the help line reads **Move Section**. Release to move it there.

Alt-click without dragging still zooms to the section, and Alt-double-click does what it does in Bitwig.

## Rules

- The last section can move earlier but not later.
- The section's time is inserted at the destination, the tracks' content is copied there (as Bitwig's Duplicate Time does), the cue marker moves, and the old time is deleted. Undo restores all of it at once.
