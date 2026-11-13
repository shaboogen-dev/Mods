# Piano Roll: Pencil Pitch Slides

For Bitwig 6.0 to 6.1.4, on Windows, macOS and Linux. Needs **Piano Roll: Overlaps to Pitch Slides** and **Framework: Piano Roll**.

A menu for the note you draw with the pencil over another note, in a piano roll, in a launcher clip or an arranger clip.

## Alt at release: an overlap menu

Draw a note with the pencil over another note of a different pitch, and hold **Alt** at the moment you release the mouse. A menu opens at the mouse, while the note you drew stays on screen:

- **Convert Overlap to Pitch Slide**
- **Merge Overlapping Notes with Pitch Slide**

They are the edits of Overlaps to Pitch Slides, and its README says what each does. Choosing one adds the note and applies the edit. **The note and the edit are one Undo step:** one Ctrl+Z returns to before you drew the note.

Closing the menu with Escape or a click elsewhere keeps the note as you drew it, as one Undo step, with no edit.

Only the moment of release counts. Holding Alt during the drag changes nothing about what is drawn, and Alt let go before the release gives no menu. You can press Alt at any time before you release, so a drag can start without it.

## The note's look

While you draw, the note shows Bitwig's own stripes, the look the eraser gives a note, whenever releasing right then would open the menu: Alt is held and at least one row would be offered. The stripes appear and go as you press and let go of Alt, even with the mouse still, and as the note comes to overlap another or stops doing so. They stay while the menu is open and go when it closes, whichever way it closes.

Only the note you are drawing has them. Nothing about the look is saved in the project or the Undo history.

Without Alt, with another tool, or when the note overlaps nothing, Bitwig does what it always does.

## Which notes

The edit takes the note you drew and every note of another pitch that sounds with it in the same clip. A note of the same pitch doesn't count, as Bitwig cuts it when the note goes in. Touching isn't overlapping.

So the note can start inside an earlier note, end inside a later one, or both, in a chain. The selection plays no part.

A row appears only if its edit accepts those notes. With nothing to offer, the note simply goes in as drawn, and the reason is written to the session log. That happens when the note and another start together, one lies inside the other, three notes sound at once or the pitches are more than 96 semitones apart. A note with curves in its pitch expression can't take part in Convert; Merge can still use it as the later note.

## Other platforms

Option is Alt on macOS. On Linux, some window managers take Alt-drag for moving windows, but only when Alt is already held at the press: start the drag, then press Alt.

## Log

The mod logs to the session log, as `[pencil-modifiers]` lines: why a menu didn't open, each row not offered, the edit applied, a dismissal, and failures.

## What it does in Bitwig

Three hooks for the menu, none of which changes anything unless the mod takes over a note:

- `Mld.cfM(dJI)`, at entry: notes the release, which carries the modifiers.
- `Mld.cfM(dJI)`, replacing its call to `bob.cfM(DDIID)`: Bitwig's insert of the drawn note.
- `dKW.wGc()`, at entry: a popup closes.

While the menu is open the note isn't in the project. Bitwig's own preview of it is what's on screen.

Four hooks for the look, which change nothing in the project:

- `Mld.cfM(dJH)`, at entry: a drawing begins, if the tool is the pencil and the widget a note grid.
- `Mld.MRY(dJK)`, replacing its call to `bob.MRY(DDIID)` and making that call: the drawing's latest values.
- `Mld.UNj()`, at entry: the drawing ends.
- `dnf.FzB(Z)`, at entry: Alt changed.

The stripes themselves are drawn through Framework: Piano Roll, which answers the note painter's question for the note with the look.
