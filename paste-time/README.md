# Arranger: Paste Time Makes Room

Makes Bitwig's **Paste Time** work as its name says. To also have Paste Time and Cut Time in the arranger's time-selection menus, add **Arranger: Paste Time and Cut Time in Menus**.

## Paste Time

Paste Time inserts empty time as long as the clipboard's content at the paste position, then pastes there. Everything after the paste position moves later instead of being pasted over. Inserting and pasting are one Undo step, named "Paste Time".

- **Clip lanes:** the target only sets the start time. Time is inserted on exactly the tracks that were copied (the whole project if the whole project was copied), and the copied content goes back onto those same tracks, whichever track is selected. To put it somewhere else, move it afterwards. If a copied track can't be found in the project (deleted since, or copied in another project), Paste Time does nothing. A clipboard that doesn't hold time copied in the arranger is pasted by Bitwig's own Paste Time.
- **Automation lanes:** time is inserted on the selected automation lane only, as Bitwig's Insert Silence does there. (Bitwig's own Paste Time does nothing in automation lanes.)

When a time range is selected, the paste goes to the start of the range. Whatever its length, the inserted time is the clipboard's length.

If the mod can't work out the clipboard's length or where to insert, Bitwig's own Paste Time runs unchanged. The mod logs each Paste Time and what it did (in a Debug session's mod log).

## Release

- Mod ID: `paste-time`
- Author: `shaboogen`
- Built against: Bitwig Studio 6.1.4 / Windows x64
