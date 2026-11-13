# Clips: Edit New Clips Reliably

Bitwig's setting **Settings > Behaviour > Automatically open new clips in Detail Editor Panel** opens the Detail Editor when you draw or create a clip. Sometimes the Detail Editor opens with the new clip nowhere in sight, so there is nothing to edit. This mod makes the Detail Editor that opens show the new clip.

## What it changes

When you draw a clip in the arranger or create one in a clip launcher slot, Bitwig picks the Detail Editor of the view you're in, hands it the new clip, and only then opens the Detail Editor. When opening it switches to the Edit view, the Edit view has its own Detail Editor, which never got the clip. That happens in Bitwig's display profiles that edit in the Edit view, and with Panel: Streamlined Views' "Automatically open Detail Editor maximised".

With this mod, the Detail Editor opens first and the new clip goes to the editor that is showing then. Bitwig already works in this order when you double-click a clip and no Detail Editor is open.

Nothing else changes:

- With Bitwig's setting off, nothing opens, as without the mod.
- Only a clip you have just drawn or created is handed to the Detail Editor, as in Bitwig.
- Double-clicking an existing clip and Bitwig's "edit in Detail Editor" actions work as before.

The mod has no settings of its own.

## Works with

- Panel: Streamlined Views (`panel-sides`), with "Automatically open Detail Editor maximised" on or off. The mod opens the Detail Editor through Bitwig's own call, so Streamlined Views decides how it opens.
- Needs no frameworks.
