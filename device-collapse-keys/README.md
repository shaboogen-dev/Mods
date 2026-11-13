# Device: Collapse and Expand Shortcuts

Two shortcuts for the selected devices of a device chain: **Collapse Device** and **Expand Device**. They do what each device's own collapse button does.

## Assigning keys

The actions have no key of their own. Assign them in **Settings > Shortcuts**, category **Editing**. `-` for Collapse Device and `=` or `+` for Expand Device suit them. On some keyboards `+` is Shift+`=`, so assign the key you press.

Plain `-` already belongs to Bitwig's **Erase tool** (Zoom Out is Ctrl+`-`; plain `=` has no default), so the dialog asks what to do with it. Choose **Add**: the key does this mod's action while a device chain has focus and stays Erase tool everywhere else. **Reassign** takes the key away from Erase tool.

The actions work while a device chain has focus: click a device first. Anywhere else the key goes to Bitwig. From the Commander, they are listed and work when the chain had focus before the Commander opened.

Requires **Framework: Actions and Menus** (`action-menu-composition-framework`) 0.8.0-dev.1 or later and **Shortcuts: Share a Key** (`shortcut-share-key`), which adds the dialog's **Add** button.

To switch the selected devices on or off with a key, see **Device: Toggle Enabled Shortcut**.

## Release

- Mod ID: `device-collapse-keys`
- Author: `kalifrn`
- Built against: Bitwig Studio 6.1.4 / Windows x64
- Based on Bitwig Superpowers.
