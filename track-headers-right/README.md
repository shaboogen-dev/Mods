# Arranger: Track Headers on the Right

Puts the Arranger's track headers on the right. Left to right, the Arranger reads: the timeline with its scrollbar, the clip launcher, the tool strip, the track headers.

- Mod ID: `track-headers-right`
- Built against Bitwig 6.1.4, Windows x64.

## What changes

- **Widths:** the headers keep their saved width and are resized from their left edge. The clip launcher keeps its saved width and its divider.
- **Clip launcher:** shows and hides as usual, sliding towards the headers. On its own it fills the area and has the scrollbar at its right edge.
- **Inside a header** nothing moves: the coloured bar and the meter stay in their places.
- **Arrows:** the arrow on the coloured bar that opens a track's automation lanes, and the arrow of a track's automation chooser, point left. The bar's arrow points down while the lanes are open, as usual.
- **Reordering tracks:** the insertion marker runs across the whole width of the Arranger.

The Mixer, the Detail Editor and other panels aren't changed. Turn the mod off to get Bitwig's own layout back.

## Limits

- While tracks are reordered, the insertion marker no longer shows how deeply the track will be nested: it runs the full width, over the tool strip too.
