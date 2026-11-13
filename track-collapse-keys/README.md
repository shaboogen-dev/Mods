# Arranger: Collapse and Expand Shortcuts

Two shortcuts for the tracks selected in the arranger: **Collapse Track** and **Expand Track**.

- A track goes to the smallest row height. Expand Track brings back the height it had. A track with no remembered height (collapsed before Bitwig started) opens to two rows.
- A group folds and unfolds, as its folder button does.
- Every selected track takes part. Tracks already in the wanted state stay as they are.

## Assigning keys

The actions have no key of their own. Assign one in **Settings > Shortcuts**, category **Arranger**. Left and Right suit them: in the arranger's track list Bitwig has no use of its own for those keys, apart from moving focus to the next panel.

Left and Right already belong to other actions, so the dialog asks what to do with them. Choose **Add** (from **Shortcuts: Share a Key**): the key does this mod's action in the track list and everything it did before everywhere else. **Reassign** takes the key away from all those actions.

The actions work while the arranger's track list has focus: click a track header first. Anywhere else the key goes to Bitwig. From the Commander, they are listed and work when the track list had focus before the Commander opened.

Requires **Framework: Actions and Menus** and **Shortcuts: Share a Key**.

## Limits

- The remembered heights last until Bitwig quits; they aren't saved with the project.

## Release

- Mod ID: `track-collapse-keys`
- Author: `kalifrn`
- Built against: Bitwig Studio 6.1.4 / Windows x64
- Based on Bitwig Superpowers.
