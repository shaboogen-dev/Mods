# Piano Roll: Scale Fold

For Bitwig Studio 6.1.3 on Windows x64 and Patchbay Mod Loader 0.36.0-dev.9.

Click **Show Scale Editor** below **Drum/Hybrid** in the Detail Editor. The compact grid shows every pitch in the project's current scale, including pitches the clip does not use. Change the root or scale in the transport bar to update the grid. Chromatic shows all pitches.

The root is shaded darkest in every octave. The perfect fifth has a lighter shade when it is part of the scale.

Draw and drag notes using the visible rows. When you transpose with the arrow keys, the grid scrolls only when the destination row reaches an edge of the view. Notes outside the scale are hidden and still play. Recording, pasting, and keyboard editing follow Bitwig's own rules. Click **Piano Roll** or **Drum/Hybrid** to return to that editor.

Extract the release ZIP into Patchbay's `mods` folder so `mods/scale-fold/mod.json` is present. Open Patchbay, enable **Piano Roll: Scale Fold**, and click **Load**. To disable the mod, untick it and start Bitwig again.
