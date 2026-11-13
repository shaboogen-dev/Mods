# Device: Optional Plug-in Undo Steps

For Bitwig 6.1.4 (Windows x64). Needs **Framework: Settings**.

Bitwig turns every change a VST or CLAP plug-in reports into an Undo step. **Settings > Mods > Optional Plug-in Undo Steps > Plug-in changes make Undo steps** switches this:

- **Off** (default): plug-in changes make no Undo steps, so Undo goes straight back through your own edits. Everything else Bitwig undoes is unchanged.
- **On**: Bitwig's behaviour.

The setting applies at once and is kept across restarts.
