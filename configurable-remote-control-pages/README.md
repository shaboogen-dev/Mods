# Device: More Remote Control Pages

For Bitwig 6.1.4 (Windows x64). Needs **Framework: Inspector** 0.3.0 or later.

Shows 1, 2 or 3 of a device's Remote Controls pages side by side, set per device.

## Using it

Select a device. At the end of the **Device** block in the Device Inspector, **Remote Pages Visible** sets how many pages its Remote Controls panel shows: 1 (Bitwig's own view, the default), 2 or 3.

- The panel updates as soon as the value changes.
- The pages are shown in blocks, in the order of Bitwig's Pages menu: with 2 pages, pages 1–2, then 3–4, and so on. Choosing a page in the Pages menu shows the block it belongs to.
- **Previous** and **Next** move by a whole block.
- A last block with fewer pages is filled with empty space the size of a page, so the pages keep their size.
- Each page is drawn by Bitwig itself, as in Bitwig's own one-page view.
- The value is saved with the project.

Works with Bitwig's devices and with plug-ins.

## With other mods

- **Device: Always Show Remote Control Page Buttons**: works alongside; Previous and Next move by a block.
- **Device: Randomise Remote Controls**: randomises every page shown, not only the first.

## For other mods

```text
com.bitwig.mods.remotecontrols.ConfigurableRemoteControlPages
    getVisiblePageCount(Object device)   // 1 to 3
```

## Notes

- The value is stored as a device property with ID 16001 in Bitwig's device schema.
- Failures are logged once each (`[configurable-remote-control-pages] ...`) and the panel falls back to Bitwig's one-page view.
