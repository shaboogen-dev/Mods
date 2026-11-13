# Helper: Audio Analysis

Author: kalifrn. Based on Bitwig Superpowers.

A helper mod: the shared part of the audio clip tools **Audio Clip: Separate Stems**, **Audio Clip: Audio to MIDI** and **Audio Clip: Detect Key**: their Python analysis engine and its setup, background jobs in Bitwig's notification centre with Cancel, and the project changes they make. Needs **Framework: Notifications** and **Framework: Actions and Menus**. On its own it adds only an **Audio Analysis** submenu to an arranger audio clip's right-click menu.

## Setting it up

The tools run a Python engine (Basic Pitch for notes, Demucs for stems). Nothing is downloaded or installed until you ask for it:

1. Right-click an audio clip in the arranger > **Audio Analysis** > **Set Up Audio Analysis…**
2. Bitwig's dialog says what will be downloaded and the folder it goes into: Python 3.12 from GitHub (python-build-standalone, 25 to 68 MB depending on the platform; you don't need a Python of your own), then from the Python Package Index Basic Pitch 0.4.0 with its ONNX runtime, numpy, librosa, scikit-learn and ffmpeg (imageio-ffmpeg), about 150 to 230 MB (about 750 MB on disk in all). **OK** starts it; **Cancel** does nothing.
3. It runs in the background (a few minutes); Bitwig stays usable. It appears in Bitwig's notification centre (the dot at the top right of the window): one entry with each step in its status line ("Downloading Python (47 MB)", "Installing packages (12 of 44)", ...), a progress bar for the whole setup, and **Cancel**. **Audio Analysis > Cancel Audio Analysis Job** stops it too. When it ends, a message there says so: "set up" (it goes after 15 seconds), "cancelled", or "failed" with the reason (it stays until you close it, with **Open Log Folder**). The Python download is checked against the SHA-256 recorded in this mod before it is unpacked, and the packages are a fixed list of exact versions, installed as ready-made wheels (nothing is compiled on your computer).

Separate Stems needs a second part: **Audio Analysis > Set Up Stem Separation…** (shown once the first part is set up). Its dialog lists PyTorch (2.11.0; 2.2.2 on Intel Macs; on Linux from PyTorch's CPU-only index) and Demucs 4.1.0, about 110 to 230 MB, and the htdemucs model from Hugging Face (about 84 MB); about 700 MB more on disk. The model is downloaded here, during setup, never while separating.

On Windows, Microsoft's Visual C++ Redistributable (x64) must be installed, as it is on most PCs; Set Up says so in a warning in the notification centre if it isn't. Runs on Windows (x64), macOS 12 or later (Apple silicon and Intel) and Linux (x64 and ARM64, glibc 2.28 or later: Ubuntu 20.04, Debian 10, Fedora 29 and later).

## Where it keeps things

Everything is in this mod's folder in BML's per-user settings folder, `mods/audio-analysis-runtime/runtime/`:

| Platform | Folder |
|---|---|
| Windows | `%LOCALAPPDATA%\BML\mods\audio-analysis-runtime\runtime` |
| macOS | `~/Library/Application Support/BML/mods/audio-analysis-runtime/runtime` |
| Linux | `$XDG_CONFIG_HOME/bml/mods/audio-analysis-runtime/runtime` (or `~/.config/bml/...`) |

Inside: `python/` (the downloaded Python), `venv/` (the Python environment made from it), `models/` (the stem model), `cache/` (results, so a clip analysed once is quick the next time; stems added to a project play from here until the project is saved with **File > Collect and Save**), `logs/` (the last output of each step) and `state.properties`.

**To remove it:** **Audio Analysis > Remove Audio Analysis Setup…** (asks first), or delete that folder. Nothing is installed anywhere else.

## For mod authors

The three tools, and any other mod, use it by compiling against its `helper.jar` (`"classpath_mods": ["audio-analysis-runtime@<version>"]`) and requiring it in `mod.json`. The API, in `com.bitwig.mods.audioanalysis`: `AudioSelection.of(context)` (the right-clicked arranger audio clip: its file, position and track), `AudioAnalysis.requireCore/requireStems` (true when set up, else a toast saying how), `Jobs.start(title, work)` (one background job at a time, shown as a task in Bitwig's notification centre and cancellable there; `Control.phase` sets its status line (keep it short; a trailing "…" is dropped), `Control.progress` its progress bar (the whole job, 0 to 1; Bitwig's busy bar until set), `Control.run` runs a command (an engine run that writes a fraction to the file named by `AUDIO_DNA_PROGRESS_FILE`, as the engine's stem separation does, fills the bar by itself), `Control.onUi` changes the project at the end; a failure or cancel becomes a message in the centre), `Commands.engine/script` and `AudioAnalysis.engineEnvironment()`, `Insert.audioTracks/midiTrack/rename` (Bitwig's own insertion and an Undo step each), `Dialogs.confirm`, `Ui.notify` (a toast: Bitwig's transient notification, for replies to a click) and, for a job's outcome in the notification centre, `Ui.info` (goes after 15 seconds), `Ui.result` (stays until closed) and `Ui.warning` (stays until closed). A job that throws shows its message as an error there, with Open Log Folder.

## Notes

- Bitwig has said stem separation is coming in its next version; when it does, Separate Stems may become unnecessary.
- The engine analyses the clip's whole audio file. A clip that plays only part of its file, or plays it stretched, gets results for the whole file spread over the clip.
