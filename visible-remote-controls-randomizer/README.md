# Device: Randomise Remote Controls

For Bitwig 6.1.4 (Windows x64). Needs **Framework: Actions and Menus** 0.8.0-dev.1 or later.

Randomises a device's Remote Controls: only the ones visible right now, on the page (or pages) the device shows, not every page it has.

## Using it

The action **Randomise Visible Remote Controls** is a Bitwig action. It can be run from:

- **the device's right-click menu**: randomises that device;
- **the device header menu** (the dropdown in a device's header), **a shortcut** (Settings > Shortcuts) or **the Commander**: randomises the device selected in the Inspector. When several devices on a track are selected, it's the last one;
- **Ctrl + left-click (Cmd + left-click on macOS) on the Remote Controls button** in a device's panel: randomises that device. A plain click works as before.

Its action ID is `bitwig_re_randomize_visible_remote_controls`.

## What is randomised

With stock Bitwig, the parameters on the Remote Controls page the device shows. If **Device: More Remote Control Pages** is installed and shows several pages, the parameters of all of them.

Only parameters Bitwig reports as settable are changed, each to a random value across its range. A parameter that appears on more than one visible page is changed once.

## For other mods

A mod that shows Remote Controls pages itself can tell this mod which pages are visible:

```text
com.bitwig.mods.remotecontrols.VisibleRemoteControlsRandomizer
    publishVisiblePagesForDevice(Object device, Object[] pages)
    publishVisiblePagesFromChooser(Object chooser, Object[] pages)
```

An empty or null `pages` clears the device's pages, and the page its chooser shows is used again.
