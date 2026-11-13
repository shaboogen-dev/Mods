# Browser: Add to Drum Machine

For Bitwig 6.0 to 6.1.4, on Windows, macOS and Linux. Needs Framework: Actions and Menus.

Adds a row, **Add to Drum Machine**, to the right-click menu of results in the side browser. It puts the selected results on pads of the selected track's Drum Machine. There are no settings, and it shows no message: the browser's selection, the Device Panel and the pad view stay as they are.

## Where the row appears

Only in the side browser, only when the selected track is an instrument or hybrid track, and only on:

- samples and multisamples
- Bitwig instrument devices and instrument presets
- plug-in instruments and plug-in instrument presets

It isn't offered on the Drum Machine device or on a preset of it, so Drum Machines don't end up inside each other. If a selection also holds other results, such as effects, those are left out.

## Shortcut and Commander

The same action, **Add to Drum Machine**, can be assigned a shortcut in Settings > Shortcuts and run from the Commander; both list it under Browser. It adds what's selected in the side browser of that window, by the rules on this page, with the same results left out. If the side browser is closed, or nothing suitable is selected, it does nothing.

## What it does

- **The track has a Drum Machine:** the first one at the top level of the track gets the content. Drum Machines inside layers or pads don't count.
- **The track has no Drum Machine:** a new one is made, holding all the content. If the track has another instrument, the new Drum Machine replaces it, as an insert from the browser does.

Each result goes on its own empty pad, in order. A pad with an empty chain counts as empty.

| Selected | Pads used |
|---|---|
| Up to 92 results | From C1 (note 36) upward |
| More than 92 results | From C-2 (note 0) upward |

If there aren't enough empty pads for the whole selection in that range, nothing is added.

One use is one Undo step, however many results are selected. If a result can't be loaded, the others are still added.

## Log

Failures (a result that can't be loaded, a pad that stays empty) are written to the log as `[add-to-drum-machine]` lines.
