# Device: VST / CLAP Plugin Undo Toggle

> **Built against**: Bitwig 6.1.4 (Windows x64)
>
> **Will map to**: All Bitwig versions Patchbay supports on Windows, Mac and Linux
>
> **Mod ID**: plugin-undo
>
> **Prerequisites**: **Framework: Settings** 1.0.0 or later.

## What does this do?

A lot of users have issues with Bitwig's implementation of adding undo for VST and CLAP plugins.

**Device: VST / CLAP Plugin Undo Toggle** adds a setting that allows you to turn this system off entirely.

## How do you use it?

* The setting is in **Settings > Mods > VST / CLAP Plugin Undo > Disable VST / CLAP plugin undo**.
* While it's ticked (the default when the mod is installed), changes made inside a VST / CLAP plugin will not be added to the Undo history.
* While it's unticked, Bitwig acts as it does normally.

## Things to note

* Only changes to VST and CLAP plug-ins are affected. Changes to Bitwig's own devices and modulators are still Undo steps.
* Plugin changes already added to the Undo history when you tick the setting stay there.
