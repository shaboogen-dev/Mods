# Grid: Straight and Triplet Only

Takes the 5t and 7t sub-divisions out of Bitwig's beat grid. With the mod on:

- The grid button's menu and the other sub-division choosers, including the Quantize dialog's custom grid, list only Straight and Triplet.
- Bitwig's Smaller Beat Grid Subdivision and Larger Beat Grid Subdivision actions each go to the other one in one press.
- The drag control in the snap settings keeps its four positions, which give Straight, Triplet, Straight and Triplet.
- A project saved with a 5t or 7t grid uses Straight, so both actions take it to Triplet. The saved value stays until the sub-division is changed, so without the mod the project shows it again.

There are no settings: turning the mod off gives Bitwig's four sub-divisions back. Projects saved with the mod open in Bitwig without it.

## Cost

Each time Bitwig reads the sub-division, for example to draw the grid, the mod makes up to two comparisons. It adds nothing per note.

- Mod ID: `straight-triplet-grid`
- Author: `kalifrn`.
- Based on Bitwig Superpowers.
