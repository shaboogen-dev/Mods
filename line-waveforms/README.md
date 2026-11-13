# Interface: Line Waveforms

Audio is drawn as one continuous line through the samples, crossing zero, instead of solid shapes filled from the zero line. Zoomed out, where a line can't show anything more, it fades into Bitwig's own drawing. **This option may lead to slower performance, and it's up to you to turn it on or off.** It is on when the mod is ticked, in **Settings > Mods > Line Waveforms**, and changes apply straight away.

- Mod ID: `line-waveforms`
- Built against Bitwig 6.1.4, Windows x64.
- Needs BML loader 0.35.0-dev.10 or later and Framework: Preference Menu Registration 0.3.0-dev.7 or later.

## Draw waveforms as a line

It applies to audio in the Arranger, clip launcher, Detail Editor, Sampler and the audio behind notes in the piano roll. It follows Bitwig's **Waveform display** setting, Linear or Perceptual, and each clip's colour, gain and fades. Nothing is saved in your projects.

Bitwig's own drawing stays for:

- audio scopes
- audio zoomed far out

## Other mods

Another mod that replaces the same waveform reading or drawing call stops preparation and is named, so the two can't be ticked together.
