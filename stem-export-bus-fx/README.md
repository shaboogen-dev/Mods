# Export: Stems with Bus Effects

Author: kalifrn. Based on Bitwig Superpowers.

Bitwig's Export Audio renders each selected track from its own output: a stem has the track's own devices, but none of the effects on the groups it's in, the effect tracks it sends to, or the master. Added together, such stems don't sound like the mix.

This mod adds one option to the Export Audio dialog, below Bitwig's own options:

**Stems include group, return and master effects**

With it ticked, each stem is what the master plays when that track is soloed: the track through its group(s), its sends through the effect tracks they feed, and all of it through the master's devices and fader. A selected group gives its whole contents the same way. The option is remembered for the next export; it's off at first.

## How it exports

- The stems are rendered one after another, not all at once: the progress window shows "Exporting stem 2 of 5". Export takes about as many times longer as there are stems (in real time, each stem plays through once).
- For each stem the mod solos that track, and only that track, and renders the master output to the stem's file, with the same names, formats, sample rate, bit depth, dither and range you chose. A **Master** entry in the same export is rendered with nothing soloed: the full mix.
- When the export ends (finished, cancelled or failed) every track's solo, and the project's Solo as Cue, are put back as they were. Solo isn't an Undo step in Bitwig, so the export leaves nothing in Undo, and nothing else in the project is touched: mutes, volumes, sends, routing, automation and devices stay as they are. A project saved afterwards is the same as without the mod.
- The export folder opens once, after the last stem. A warning about missing plug-ins appears once, before the first stem.
- With only Master selected, or with the option off, the export is Bitwig's own.

## What it can't do exactly

- **Effects that react to level** (compressors, limiters, saturation, gates) on a group, effect track or the master hear only one track at a time. Each stem is right on its own, but the stems added together differ from the mix wherever such an effect works harder on the full mix. No per-stem render can avoid this.
- **Sidechains:** a stem gets what Bitwig does with that track soloed. A compressor keyed from another track hears that key only if Bitwig keeps feeding it while the key track is muted by solo; the mod doesn't change it.
- **Effect tracks as stems:** soloing an effect track silences the tracks that send to it, so its stem holds only what the effect track plays itself. Its effect on the other tracks is already in their stems.
- **Overlapping stems:** selecting a group and also tracks inside it exports those tracks twice (once in the group's stem), as in Bitwig.
- **Your solos** are set aside during the export: each stem is that track alone, and Master is the full mix, even if tracks were soloed when you started.
- **Pre-fader** doesn't apply with the option on: a stem is the master's output after its fader, so each track's own fader and the master fader are included.
- If Bitwig closes or crashes during the export, the solos stay as the export left them (one track soloed). Unsolo, or close the project without saving.

## Needs

Nothing else: no framework.
