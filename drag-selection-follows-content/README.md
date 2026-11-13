# Detail Editor: Drag Selection Follows Content

When events are selected in the detail editor, notes, audio events or both, the highlighted range stays exactly on them, from the earliest start to the latest end.

- Drag a selection around events with empty space around them: when you release the mouse, the highlight trims to the events.
- Duplicate moves by the events' span, so the copy starts where the last event ends.
- Move or shorten a selected event and the range follows it.
- Bitwig's commands that use the range, such as Reverse, work from the events' span.

A time range with nothing selected, clips in the Arranger, and selections of automation, markers or anything else work as in Bitwig.

The mod has no settings.

- Mod ID: `drag-selection-follows-content`
- Built against Bitwig 6.1.4, Windows x64.
- Author: `kalifrn`.
- Based on Bitwig Superpowers.
