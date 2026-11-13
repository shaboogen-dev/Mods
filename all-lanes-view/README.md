# Device: All Lanes View

For Bitwig 6.1.4 (Windows x64); built to run on Bitwig 6.0 to 6.1.4 on Windows, macOS and Linux. Needs no other mod.

Shows every lane of a multi-chain container at once: one row per lane, each with the lane's name and its full device chain, on the device panel's black background.

## Which devices

- FX: FX Layer, FX Selector, Multiband FX-2, Multiband FX-3, Mid-Side Split, Stereo Split and XY FX.
- Spectral: Freq Split, Loud Split, Transient Split and Harmonic Split.
- Instruments: Instrument Layer, Instrument Selector and XY Instrument.
- Note FX: Note FX Layer and Note FX Selector.

Every other device, Reverb and other devices with FX slots included, works as in Bitwig.

What a row is depends on the device:

| Device | Rows |
|---|---|
| FX, Instrument and Note FX Layer; FX, Instrument and Note FX Selector | One per layer or selector lane, with the lane's name and colour |
| Multiband FX-2 | Low, High |
| Multiband FX-3 | Low, Mid, High |
| Mid-Side Split | Mid, Side |
| Stereo Split | Left, Right |
| XY FX | The four corners A, B, C, D, then Post FX |
| XY Instrument | Pre, A, B, C, D, Post |
| Freq Split | Pre FX, 1, 2, 3, 4, Post FX |
| Loud Split | Pre, Low, Mid, High, Post |
| Transient Split | Pre, Transient, Harmonic, Post |
| Harmonic Split | Pre, Harmonics A, Harmonics B, Nons, Post |

On every device with fixed chains the rule is the same: Pre first, the bands, Post last. Rows show the name Bitwig gives each chain. The FX chain of Instrument Layer and Instrument Selector isn't shown: it stays in the device panel.

## Opening it

- **The expanded-view button** in the device's header (the same button The Grid and other devices with an expanded view have). It is lit while the view is open; click it again to close the view.
- **Toggle Expanded Device View of Selected Device** (Settings > Shortcuts), with the container selected. Pressed again, or while the view has focus, it closes the view.

## Docked or in its own window

The view behaves like The Grid's expanded view:

- Opened from the device panel, it docks in the main window where Bitwig docks expanded device views.
- The **undock** button in its header moves it into its own window; that window's **dock** button moves it back. The choice is remembered for the next time it opens, for every device.
- Holding **Shift** while opening it does the opposite of the remembered choice.
- Opened by the shortcut from anywhere other than the device panel, it opens in its own window, as Bitwig's expanded views do.

The window's size and place are remembered too.

## In the view

- Each row starts with a narrow header, drawn like a device's header: the lane's name, reading bottom to top, under a bar in the lane's colour. **Solo** and **Mute** stay in the device itself.
- Next to it is the lane's device chain, as the device panel shows it and at the device panel's height: edit parameters, add devices with **+**, drag devices, open nested chains.
- A line separates the rows.
- The rows follow the container: lanes added, removed, renamed or reordered show up at once. Bands, sides and corners are fixed.
- The view keeps the size of the place it is shown in, docked or in its own window, also when the device has no lanes, and scrolls with Bitwig's scroll bars: up and down when the rows don't fit, sideways when a row is wider than the view.
- The view stays open when you select another track. It closes when the container is deleted.

The lane, band or side selected in the device still shows in the device panel as well.

## What is kept

Nothing is saved into the project, so the project opens unchanged without the mod. The docked or floating choice and the window's size and place are kept in the mod's settings.
