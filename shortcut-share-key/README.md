# Shortcuts: Share a Key

Bitwig lets one key do different things in different places. Out of the box, Left moves the focus in most of the window, moves the cursor in a text field and moves a selection in an editor. Bitwig's shortcut dialog doesn't let you set up keys like that yourself: when the key you press already belongs to another action, it offers only **Reassign**, which takes the key away from every other action, or **Cancel**.

This mod adds a third button, **Add**. It gives the action the key and leaves the other actions with theirs.

<!-- image: Bitwig's shortcut dialog with a key that's already taken, showing Reassign, Add and Cancel -->

## Using it

1. Open **Settings > Shortcuts** and click an action's shortcut field.
2. Press a key that another action already uses. The dialog lists the actions that have it.
3. Choose **Add**. The action gets the key, and the actions that had it keep it.

**Add** is there for every action, Bitwig's own and mods' alike, whenever the key you press is taken. With a free key the dialog is the same as without the mod. Controller shortcuts keep Bitwig's own two buttons.

To undo an Add, remove the key from that action's row in Settings > Shortcuts. The other actions keep theirs.

## What a shared key does

Bitwig runs one action per key press, never two. It starts at the part of the window that has the focus and works outwards, and the first action bound there that uses the key runs.

- **Two actions that apply in different places both work.** Each one runs where it applies. For example, a mod's action for the track list on Left runs while the track list has the focus, and Bitwig's Focus widget to the left runs everywhere else.
- **Where both apply, Bitwig runs one of them,** and the other doesn't run there. Choose keys for actions that apply in different places.

Keys follow the focus, not the mouse: click in a panel to give it the focus. Mods that are meant to share a key with Bitwig's own actions say so in their READMEs.

## Turning it off

Without the mod the dialog has Bitwig's two buttons again. Keys you've added stay as they are. They're saved in Bitwig's own shortcut settings, which already allow one key on several actions, and Bitwig keeps using them. Remove a key in Settings > Shortcuts as usual.

## Details

- Mod ID: `shortcut-share-key`.
- Built against Bitwig 6.1.4; prepares on Bitwig 6.0 to 6.1.4.
- No options. Stores nothing of its own: Bitwig saves the shortcuts.
