# Browser: Pre-5.0 Style Side Browser

A side browser in the style of Bitwig 4.x: a pane beside the results, where you choose what to browse, and a type column.

- Mod ID: `pre-5-style-side-browser`
- Built against Bitwig 6.1.4.
- Needs Framework: Browser (`browser-framework`) 0.3.0-dev.1 and Framework: Settings 1.0.0 or later, which Patchbay ticks with it.

## Turning it on

Settings → Mods → Browser → **Browser view: Pre-5.0 Style Side Browser**. The browser rebuilds at once. **Standard** goes back to Bitwig's own side browser.

## What it does

- **Header.** Bitwig's All Sources button, the search field and the tabs **PACKAGES / COLLECTIONS / BY KIND / LOCATIONS** (Bitwig's own tabs and labels) go across the whole browser. The filters that apply show below them as Bitwig's chips; a chip's X clears its filter.
- **The pane** beside the results lists the chosen tab's sources in Bitwig's compact rows:
  - Packages: the installed packages.
  - Collections: Everything, Favorites, then your collections.
  - By Kind: Bitwig's kinds (Instruments, Audio FX, Samples, ...), and kinds other mods add (Projects, with Browser: Enable Project File Filter).
  - Locations: Bitwig's location tree, browsing Everything.

  Choosing a row narrows the results to it and shows a chip (Package, Collection, Kind or Location); the chip's X clears that tab's choice, and Everything in Collections clears Collections'. The tabs combine: a package, a kind and a location together show only what matches all three, and the type column narrows them further. The pane opens on the leftmost tab.
- **Type column** at the bottom of the pane, Bitwig's own filter column: **Any, Instrument, Audio FX, Note FX, Samples** (audio files), **Other** (everything else: clips, multi-samples, templates, ...). Choosing one narrows the results; Any clears it. It shows no counts.
- **All Sources button** at the left of the search field: opens Bitwig's All Sources view, where you create a collection (its Collections page, then Create Collection; the new collection joins the COLLECTIONS tab). The button closes it again. Choosing a source there browses that source, with no chip; choosing a row in the pane goes back to browsing everything.
- **Hidden:** the Quick Sources toolbar, the source header and the filter area, which the pane replaces. Their shortcuts still work.

Settings → Mods → **Pre-5.0 Style Side Browser** has **Packages tab / Collections tab / By Kind tab / Locations tab**, to leave tabs out (with none on, Locations). Changing one rebuilds the browser while the view is chosen.

## Width

Each column moves on its own, as Bitwig's panels do:

- **The divider between the pane and the results** sets the pane's width. It is kept between launches and is the same in every project.
- **The browser panel's outer edge** changes only the results; the pane keeps its width. Bitwig keeps the panel's width whatever the window's size and saves it in the project.
- **Minimums:** the pane keeps at least 80 and the results 120 of Bitwig's layout units (about 120 and 180 px at 150 %). Dragging the panel's edge further, the pane gives way once the results are at their minimum; below half the two together Bitwig collapses the panel, as it does any panel.
- **Starting width:** the pane starts at 110 units. Bitwig saves the panel's width in each project; a project that has none of its own (a new one, or one whose panel was never resized) opens at the width you last dragged the panel to, or 270 units before you ever have, as far as the window has room. A width saved in the project is kept.
