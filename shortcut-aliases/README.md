# Commander: Assign Aliases to Actions

Allows you to trigger actions in Commander by using configurable aliases.

## Behavior

- One alias per action.
- Commander's own matching comes first; an alias only adds results Commander would not show.
- Commander runs the original Bitwig action; no extra actions are registered.
- An **Alias** column in **Settings -> Shortcuts**, built from Bitwig's own widgets: the alias shows in Bitwig's shortcut chip with its remove button, and Bitwig's `+` button opens a text field to add one.
- Duplicate aliases are rejected case-insensitively when **Add** is pressed.
- Adding or removing an alias updates Commander immediately.
- Aliases persist across restarts.

## Where aliases are stored

Aliases are kept apart from Bitwig's `.bwkeymap`, in BML's settings folder (**Settings > Settings Folder** in BML's window shows where):

    mods/shortcut-aliases/config/commander-aliases.cfg

One `alias=actionId` per line. The file is created when you add the first alias. An update of the mod leaves it alone.

## Scope

This mod does not change Commander's Enter behavior. Install **Commander: Enter Selects First Result** separately if you want Enter to execute the first visible result when nothing is selected.

## Compatibility

Built against Bitwig Studio 6.1.4 (Windows x64) with the BML mod kit; needs BML loader 0.34.0-dev.5 or later.
