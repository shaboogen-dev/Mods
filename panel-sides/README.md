# Panel: Streamlined Views

For Bitwig 6.1.4 (Windows x64). Needs:
- **Framework: Panels** 0.2.0 or later: the view stack, its buttons and actions.
- **Framework: Settings** 1.0.0 or later: the setting.
- **Framework: Actions and Menus** 0.8.0 or later: the actions.

**Views instead of modes** (Single Large Monitor display profile): the main area is one stack of views, top to bottom: **Tracks** (the arranger), **Detail Editor** (piano roll and the other clip editors) and **Mixer** (the big mixer). The **device panel** sits below them, or below the whole window width. The small mixer is gone.

Goes well with **Panel: Choose Sides** (the Inspector and the Browser panel on either side), **Panel: Separate Browser Panels** (the browser side's panels as columns), **Panel: Scale Each Panel** (a scale for each view and panel) and **Detail Editor: Open Maximised**.

**Use it with Panel: Mix Map.** Mix Map then becomes a fourth view of the stack: shown alone, or split with Tracks, Detail Editor or Mixer. Other mods' panels made for Framework: Panels join the stack the same way.

Its one setting, **Device panel across the full width**, is in **Settings > Mods > Streamlined Views**, and applies at once.

## Views

The ARRANGE / MIX / EDIT chooser in the bottom bar is replaced by the views' buttons: Tracks, Detail Editor and Mixer with Bitwig's own icons, then the buttons of other mods' views, such as Mix Map.

- **Click** a view's button: that view alone.
- **Cmd-click** (macOS) or **Ctrl-click** (Windows, Linux): add the view to the split, or remove it. The views keep their order, Tracks above Detail Editor above Mixer, then the other mods' views. The last view shown can't be removed.
- **Right-click** a view's button: **Equalise View Sizes**, which gives every shown view the same height (and, with Panel: Scale Each Panel, the view's scale).
- The **device panel** button beside them shows or hides the device panel at the bottom in one click, whatever views are shown and wherever the focus is. It keeps its height. With Panel: Scale Each Panel, right-click it for its scale.

A button is lit while its view is shown, and its tooltip says what a click does. Views show and hide at once, without Bitwig's sliding animation, and a view shown alone fills the whole area above the device panel. Each view's size and visibility are saved with the project, as Bitwig's own panels are. A view keeps them when another mod's view is added to the stack or taken away.

### Shortcuts

Listed under Bitwig's **Panel Management** category in Settings > Shortcuts and the Commander. None has a key until you give it one.

| Action | Does |
|---|---|
| Show Tracks / Detail Editor / Mixer | as a click on the view's button: that view alone |
| Add or Remove Tracks / Detail Editor / Mixer in the Split | as Cmd/Ctrl-click on the view's button |
| Equalise View Sizes | the same height for every shown view |
| Show or Hide Device Panel | as a click on the device panel button: one press shows it, one press hides it |

Every other mod's view gets its own Show and Add or Remove actions too. All of these except Show or Hide Device Panel come from Framework: Panels.

"Show Tracks / Detail Editor / Mixer" chooses the view's mode, so it is the same as Switch to Mode 1 / 3 / 2 below.

Bitwig's own panel shortcuts under this layout:

| Bitwig action | Under this layout |
|---|---|
| Switch to Mode 1 / 2 / 3 | Tracks alone / Mixer alone / Detail Editor alone |
| Select Next Mode, Select Previous Mode | goes through Tracks, Mixer and Detail Editor alone |
| Toggle Device Panel, Focus/toggle Device Panel | better unbound: use Show or Hide Device Panel. Focus/toggle only focuses the device panel on the first press when it isn't focused; Toggle reaches the mixer's own device section while the Mixer is shown |
| Toggle Inspector Panel, Toggle Browser Panel (and the Browser panel's tabs) | unchanged |
| Toggle Arranger Timeline Panel, Toggle Clip Editor Panel, Toggle Mixer Panel (and their Focus/toggle versions) | still show or hide that view, but can hide the last one and leave the area empty: use the actions above instead |

Bitwig's three modes still exist behind the scenes, as presets: **ARRANGE** shows Tracks alone, **MIX** the Mixer alone, **EDIT** the Detail Editor alone; the device panel is left as it is. Other mods' views have no mode, so the modes hide them. So "Switch to Mode 1-3", controller scripts that ask for a mode, and projects saved in a mode all give that view.
- Choosing EDIT again no longer jumps back to the previous mode, and "next / previous mode" goes through all three.
- A project that opens with no view shown (for example one saved with only a view whose mod is now off) gets its mode's view.

The other display profiles keep Bitwig's modes. There, other mods' views appear as tabs of each mode's lower panel (see Framework: Panels).

### Device panel across the full width

The setting **Device panel across the full width** (Settings > Mods > Streamlined Views) puts the device panel under the whole window: under the Inspector, the views and the Browser panel, above the on-screen keyboard. Off (the default), it sits under the views, between the Inspector and the Browser panel.

The device panel button and Show or Hide Device Panel work the same either way, and there is still one device panel button. The device panel's height, and every other size and view, stay as they were when the setting is switched. Expanded device views dock into Tracks either way.
