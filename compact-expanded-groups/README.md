# Arranger: Compact Expanded Groups

While a group track is expanded (its tracks are shown), its own lane in the Arranger is compact and the summary of its tracks' clips is drawn faded. A collapsed group keeps its lane height and Bitwig's own summary.

- Mod ID: `compact-expanded-groups`
- Built against Bitwig 6.1.4, Windows x64.
- Needs Framework: Settings.

The track headers on the right and the default track height, once part of this mod, are now the mods Arranger: Track Headers on the Right and Arranger: Default Track Height.

## Settings

All in **Settings > Mods > Compact Expanded Groups**.

| Setting | Does | Default |
|---|---|---|
| **Compact height** | the lane height of an expanded group, 14–64 px | 21 |
| **Summary opacity** | how strongly an expanded group's clip summary is drawn, 0–100 % | 15 |

## Group tracks

Each group, nested ones included, follows its own expanded state. Changes apply at once. The group's saved lane height isn't changed by these settings: collapsing the group brings it back. While an expanded group is at the compact height its lane can't be resized; a collapsed group, or one shown at full height (below), is resized as usual.

## Full height button

An expanded group has a small chevron next to its folder button. Click it to show that group at its full track height (the chevron points down), and again to go back to the compact height (it points right). The choice is per group and lasts until the project is closed: nothing is saved in the project or in the settings. A collapsed group has no chevron.

The button is based on Bitwig Superpowers by kalifrn.
