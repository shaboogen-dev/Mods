# Browser (side): Enter Inserts Selected Result

Makes Enter insert results from the docked Side Browser.

- **In the result list:** Enter (Bitwig's Activate and Launch actions) inserts the selected result.
- **In the search field:** type a name and press Enter once: the first result is inserted, on its own. Bitwig's own Enter still runs as well (it commits the search text and moves focus to the results).
- Only Enter pressed in the search field counts: clicking away from the field inserts nothing.
- The first result is the first visible one that refers to a resource (such as a file). That is not a device-only filter: the browser decides what it can insert where.
- **What goes in:** Enter does what dropping the result on the track does, with no keys held, and goes by what the track holds, not by where a drop would land.
  - An instrument, or a sample on an instrument or hybrid track, replaces the track's instrument if it has one; a preset for the instrument already there loads into it.
  - A note effect goes before the instrument, and an audio effect at the end of the chain.
  - A sample on an audio track gives a Convolution, at the end of the chain. An impulse always does.
  - Several selected samples, when the row Enter acts on is one of them, go in together as one drop. On an instrument or hybrid track they make one Sampler holding them all as a round-robin multisample; on an audio track each gives a Convolution. A selection with an impulse or anything other than samples inserts the row Enter acts on, alone.
  - A wavetable gives a Polymer.
  - A modulator goes on the track's selected device. With no selected device, or one that takes no modulators, nothing happens.
- **What a drop refuses** keeps Bitwig's own Enter: a project opens, and modules, clips, curves and templates do nothing.
- Undo removes what Enter inserted in one step.
- Only the docked Side Browser is affected.

## Requirements

- Bitwig Studio 6.1.4 (Windows x64).
- BML loader 0.34.0-dev.5 or later.
