# Automation: Quantize Points

Lets Bitwig's own **Quantize** move selected automation points to the grid, as it does for notes.

- Mod ID: `quantize-automation-points`
- Built against Bitwig 6.1.4, Windows x64.
- Needs BML loader 0.35.0-dev.17 or later. No frameworks.

## Using it

Select automation points and quantize them the way you quantize notes:

- **Quantize** (the shortcut or the Edit menu), with the last settings;
- **Quantize…**, which opens Bitwig's Quantize dialog;
- **the right-click menu** of a selected point, which has the Quantize group.

It works in:

- the arranger's **automation lanes**;
- **clip automation in the detail editor**, for launcher clips and arranger clips.

Each Quantize is one Undo step. The mod adds no actions, menu entries or settings.

## How points behave

- **Grid and settings:** as for notes. Quantize uses its last setting: with the editor grid, points on arranger lanes use the arranger's grid and clip automation uses the detail editor's grid; with a grid chosen in Quantize…, they use that grid. Swing and the dialog's other settings apply as for notes.
- **Amount and humanize** apply as for notes. **End** settings have no effect: a point has no length.
- **Two points on one grid line:** both stay, the later one after the earlier, which gives a vertical step (a jump in value). A point already on that line is removed only when it adds nothing to the curve, so the curve's shape is kept.
- **Several lanes at once:** points selected on several lanes are each quantized in their own lane, in one Undo step. The arranger and the detail editor are separate selections: one Quantize works on one of them.
- **Points crossing a neighbour:** as with notes, a selected point can move past an unselected point between it and its grid line (dragging stops at the neighbour). Selected points keep their order among themselves, except with humanize, which can swap points that are very close.
- **Arranger automation clips:** their points snap to the grid as Bitwig computes it for the clip, from the clip's own time. In a clip that starts off the arranger's grid, they can land off the arranger's grid lines.
- **Note expressions** (per-note automation in the piano roll) aren't covered: Quantize stays unavailable for them.

Quantize only moves existing points, so a project quantized with this mod opens in Bitwig without it.
