# Piano Roll: Split Notes at Grid

**Split Notes at Grid** cuts each selected note on the grid lines inside it.

It is in the right-click menu of selected notes, and it is an action with no key: assign one in Settings > Shortcuts, under Detail Editor. The key works while focus is in a note editor; elsewhere it goes to whatever else has it. Bitwig's own Split keeps its key and what it does.

The grid step is the editor's current one: its fixed size and sub-division, or the size the adaptive grid shows at the current zoom. For an adaptive grid, the straight size is used.

- A note no longer than one grid step isn't cut, even if it crosses a line.
- Each piece keeps everything the note holds: velocity, chance, expressions and so on.
- Expressions belong to each note, so every piece is a note with its own full copy, played from its beginning: a pitch slide, for example, plays again in each piece.
- The cut is one Undo step, named Split Notes at Grid.
- The notes stay selected, and every new piece joins the selection. If the selection also holds anything that isn't a note, the notes are still cut, and the selection stays as it was.

## Cost

The action runs only when used. It adds nothing to drawing.

- Mod ID: `split-notes-at-grid`
- Requires Framework: Actions and Menus and Framework: Piano Roll.
- Author: `kalifrn`.
- Based on Bitwig Superpowers.
