# Parameter: Randomise Hovered Parameter

> **Built against**: Bitwig 6.1.4 (Windows x64)
>
> **Will map to**: All Bitwig versions Patchbay supports on Windows, Mac and Linux
>
> **Mod ID**: single-parameter-randomizer
>
> **Prerequisites**: **Framework: Actions and Menus** 0.8.0-dev.1 or later.

## What does this do?

Parameter: Randomise Hovered Parameter does exactly what the name suggests. This adds functionality to Bitwig that allows you to randomise the value of a parameter that your mouse pointer is hovering over.

## How do you use it?

This mod registers a menu entry that shows up when right-clicking a parameter and a shortcut action that you can assign a keystroke to in the Shortcuts menu. These actions are both named *Randomise Hovered Parameter*. Perform either of these actions to assign a randomised value to a parameter your mouse is hovering over.

## Things to note

* Though the obvious usage is device parameters, remote controls and modulator parameters, this will also work on mixer parameters such as volume, pan and FX sends.

* Assuming the parameter can be moved, it doesn't matter if the parameter is a slider, a knob, a value field or even a drop-down, the keyboard shortcut should still work.

* The *Randomise Hovered Parameter* action also shows up in the Commander, but this action will not work as there is no parameter under the mouse when Commander triggers.

* *Randomise Hovered Parameter* does not set a shortcut key by default. You will need to set this yourself.

* If a parameter cannot be changed for some reason, the action will do nothing and the random value will not be assigned.
