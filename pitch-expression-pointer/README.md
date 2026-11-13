# Piano Roll: Pitch Expression Pointer

For Bitwig 6.1 to 6.1.4, on Windows, macOS and Linux.

While you hold **Alt** with the pointer tool, you edit pitch on notes with Bitwig's own gestures. The pitch expression mode stays off, and so do its button and chooser. Nothing is saved except the points you edit.

| With Alt held | Click | Double-click | Drag |
|---|---|---|---|
| On a point (within 6 pixels of its centre) | Selects it | Deletes it | Moves it, at normal speed and with normal pitch snap |
| Around a point (6 to 12 pixels from its centre) | Selects it | Resets the curve on both sides of it | Bends the curve on both sides of it, in an S |
| Close to a line (within 6 pixels) | Adds a point | Resets the line | Adds a point and moves it |
| Further from a line (out to about 24 pixels) | Adds a point | Resets the line | Bends the line |
| On a note with no points | Adds a point | Nothing | Adds a point and moves it |
| On a note, further from its line than that | Bitwig's usual note behaviour | Bitwig's usual note behaviour | Bitwig's usual note behaviour |
| Empty grid, out of reach of any line | Bitwig's normal behaviour | Bitwig's normal behaviour | Bitwig's normal behaviour |

A line can be reached along its whole length, wherever it is on screen, even far from its note. Distances from a line are measured vertically, distances from a point in a straight line, and both follow the interface scale. A point's zones come first, so they take over from a line's near the point. A first or last point has a line on one side only.

New points snap to the grid in time, including while you drag them. Hold **Shift** too to place them off the grid. A new point takes the curve's pitch at that time, not the mouse's height: drag to change it.

While dragging a point, hold **Ctrl** too (**Cmd** on macOS) to turn pitch snapping off for as long as you hold it. Speed and time snapping stay as they are. Dragging to add a point works the same, but pressing with Ctrl held makes it a hold point.

A double-click is whatever Bitwig counts as one. Its first click adds a point as usual, and the second takes that point out again with Bitwig's own Undo before resetting the line, so nothing stays behind. A click, a double-click and a drag are each one Undo step. A line reset by a double-click is the part between two points: outside the first and last point there is no curve to reset.

If the expression mode is on, Bitwig's own Alt behaviour applies. With any other tool, Alt does nothing here: the pencil, the eraser and the rest behave as they do without this mod. A tool held down by its key counts as the tool in use, and a gesture you've started carries on if the tool changes during it.

The look follows on the next repaint, so the point handles may wait for the next mouse move.

There are no settings: install it and hold Alt.

If one of its steps fails, the mod logs it once, as a `[pitch-expression-pointer]` line, and Bitwig's own behaviour takes over.
