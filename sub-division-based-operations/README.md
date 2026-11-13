# Piano Roll: Nudge and Resize by Grid

Actions that move the selected notes, or change their length, by the grid step.

## Actions

Four actions, with no keys. Assign them in Settings > Shortcuts, under Detail Editor. Their keys work while focus is in a note editor; elsewhere they go to whatever else has them. Bitwig's own nudge and length actions keep their keys and what they do.

| Action | What it does to the selected notes |
|---|---|
| **Nudge Notes Earlier by Grid** | Moves them one grid step earlier |
| **Nudge Notes Later by Grid** | Moves them one grid step later |
| **Shorten Notes by Grid** | Takes one grid step off their ends. A note of one step or less collapses to a sliver of 1/960 of a beat. A note whose length isn't a whole number of steps goes to the whole number below it instead |
| **Lengthen Notes by Grid** | Adds one grid step to their ends. A sliver becomes exactly one step, so notes collapsed together come back the same length. A note whose length isn't a whole number of steps goes to the whole number above it instead |

The grid step is the editor's current one: its fixed size and sub-division, or the size the adaptive grid shows at the current zoom. For an adaptive grid, the straight size is used.

- After the grid has changed, the first nudge brings a note that is off the new grid to the nearest line in that direction, keeping its length. A note on the new grid moves one step, and so does every later nudge.
- Shorten and Lengthen go by each note's length, not by where its end sits on the grid. A note of 1.1 steps shortens to 1 or lengthens to 2, and that is the whole press; at 0.9 steps it shortens to the sliver or lengthens to 1. A note starting off the grid keeps its start and gets a whole number of steps.
- Each action is one Undo step: Nudge Notes by Grid or Resize Notes by Grid.
- Notes keep everything they hold, and a nudged note's expressions move with it. Other notes aren't cut or moved, even when a note lands on them.
- These four do nothing while a time selection is active.

## Cost

The actions run only when used. They add nothing to drawing.

- Mod ID: `sub-division-based-operations`
- Requires Framework: Piano Roll and Framework: Actions and Menus.
- Author: `kalifrn`.
- Based on Bitwig Superpowers.
- Split Notes at Grid and the 5t/7t limit are separate mods: Piano Roll: Split Notes at Grid and Grid: Straight and Triplet Only.
