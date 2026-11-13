# Parameter: Randomise Hovered Parameter

For Bitwig 6.1.4 (Windows x64). Needs **Framework: Actions and Menus** 0.8.0-dev.1 or later.

Sets one parameter to a random value across its range.

## Using it

The action **Randomise Hovered Parameter** is a Bitwig action:

- **A shortcut** (Settings > Shortcuts): randomises the parameter under the mouse. Point at a knob, slider or value field and press the shortcut.
- **A parameter's right-click menu** (device knobs, Remote Controls, modulator knobs, volume, pan, sends): randomises that parameter. It appears only when the parameter can be set.

The action is also listed in the Commander, but it does nothing there: while the Commander is open, no parameter is under the mouse.

Each change is one Undo step, as when you move the parameter yourself. A parameter that can't be set right now is left alone.

Its action ID is `bitwig_re_randomize_current_parameter`.
