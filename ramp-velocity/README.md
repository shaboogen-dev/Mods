# Piano Roll: Ramp Velocity

Sets the selected notes' velocities as a ramp, from a block in the Note Inspector.

Author: kalifrn. Based on Bitwig Superpowers.

Needs Framework: Piano Roll, Framework: Inspector and Framework: Notifications.

## Using it

Select at least two notes in a note editor. At the end of the Note Inspector, the **Velocity Ramp** block has:

| Row | What it does |
|---|---|
| **From** | The velocity of the earliest selected note, 1 to 127 |
| **To** | The velocity of the latest selected note, 1 to 127 |
| **Curve** | The shape between them. 0 is a straight line. Above 0 the ramp changes slowly at first and quickly towards the end; below 0, quickly at first and slowly towards the end. Greyed out while From and To are equal, where there is nothing to shape; it keeps its value |
| **Apply** | Sets the velocities, as one Undo step named Ramp Velocity. Greyed out with fewer than two notes selected |

The ramp goes by each note's start: notes that start together, such as a chord, get the same velocity. With notes selected in several clips, each clip ramps over its own selected notes, from its earliest start to its latest.

1. **Change From, To or Curve.** What Apply would do is drawn at once, before anything changes: each note whose velocity would change is drawn with its new velocity, with Bitwig's eraser stripes, as MIDI Tools draws its outcome. These are Bitwig's preview notes: not selectable, not saved and not in the Undo history. With **Play the preview** on (Settings > Mods > Ramp Velocity, on by default), each change auditions the notes once at their new velocities, and while the preview shows, the clip plays with them when it plays; when the preview goes, it plays its real velocities again. Turn it off and the preview is silent. Notes already at their velocity stay as they are.
2. **Click away** (select other notes, or none), edit a note, or press Undo or Redo, and the preview goes; the notes are as they were.
3. **Press Apply** to set the velocities. The notes stay selected.

The values last until Bitwig closes. Double-click a number to reset it: From to 1, To to 127, Curve to 0.