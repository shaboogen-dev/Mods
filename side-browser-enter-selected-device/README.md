# Browser (side): Enter Inserts Selected Result

> **Built against**: Bitwig 6.1.4 (Windows x64)
>
> **Will map to**: All Bitwig versions Patchbay supports on Windows, Mac and Linux
>
> **Mod ID**: side-browser-enter-selected-device
>
> **Prerequisites**: None

## What does this do?

Using the side browser, loading a device, a sample or any other file type that it supports requires you to drag the item to the track. This is unlike the popup browser that allows you to use the Enter key.

**Browser (side): Enter Inserts Selected Result** makes Enter insert a result exactly as if you had dragged it onto the selected track, allowing you to better take advantage of the search bar and add devices, presets and samples without leaving the keyboard.

## How do you use it?

* **From the search field:** type a search and press Enter. The first result is inserted.
* **From the results:** pick a result with the arrow keys and press Enter. The selected result is inserted.

## Things to note

* As this mod mirrors the drag functionality, there are several different outcomes depending on the scenario:
  * Hitting Enter on an Instrument or a Preset file will load as the first device in most cases.
    * If an instrument is already on the track, hitting Enter will replace it.
    * If a Note FX device is already on the track, the Instrument will be placed after it.
  * Hitting Enter on a Note FX device will place it first in the chain.
    * If a Note FX device already exists, the Note FX device will be placed after it.
  * Hitting Enter on a Sample file has a different outcome depending on the type of track that is selected.
    * On an instrument track, the file will be loaded in a **Sampler**.
    * On an Audio or FX track, the file will be loaded in a **Convolution** device.
      * This only occurs on more recent versions of Bitwig.
  * Hitting Enter if multiple Sample files are selected has a different outcome depending on the type of track that is selected.
    * On an instrument track, a single **Sampler** device with all samples loaded simultaneously will be instantiated, conforming to the same rules as instantiating an Instrument.
    * On an Audio or FX track, each sample will load in its own **Convolution** device.
  * Hitting Enter on a Wavetable file opens a Polymer device with a Wavetable module selected as its oscillator type.
  * Hitting Enter on a Modulator file loads it onto a selected device.
    * If no device is selected or the device selected cannot accept modulators, nothing happens.
* This only affects the Side Browser, not the pop-up browser.
* This was built with the mod **Browser (side): Search Bar Shortcut** in mind as you can press a shortcut to immediately focus the search bar, type in what you want then press **Enter** to load a device straight away.
