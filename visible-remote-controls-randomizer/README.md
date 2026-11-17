# Device: Randomise Remote Controls

> **Built against**: Bitwig 6.1.4 (Windows x64)
>
> **Will map to**: All Bitwig versions Patchbay supports on Windows, Mac and Linux
>
> **Mod ID**: visible-remote-controls-randomizer
>
> **Prerequisites**: **Framework: Actions and Menus** 0.8.0-dev.1 or later.

## What does this do?

**Device: Randomise Remote Controls** allows you to randomise the values of all visible Remote Controls simultaneously.

## How do you use it?

This mod registers a menu entry that shows up when right-clicking a device and a shortcut action that you can assign a keystroke to in the Shortcuts menu. These actions are both named *Randomise Visible Remote Controls*. This also adds a ctrl-click (cmd-click on Mac) action to the Show / Hide Remote Controls button that will trigger this functionality as well.

## Things to note

* If a Remote Control can't be changed for some reason, *Randomise Visible Remote Controls* will leave it as is.
* When *Randomise Visible Remote Controls* is triggered via keyboard shortcut, or the Commander, if multiple devices are selected the action will trigger on the most recently selected device.
* The word *visible* has been chosen specifically, it's important to note what this means in certain contexts.
  * When running the **Device: More Remote Control Pages** mod, *Randomise Visible Remote Controls* will trigger on all shown pages simultaneously.
  * Despite the word *visible* being used, the action does not require the device's Remote Control pages to be open. It will act on the page/s that have been selected.
