# Panel: Shared Tool Mode

Choose a tool in one editor and every other editor switches to it too: the arranger and each Detail Editor mode (piano roll, drum editor, audio editor, automation editor, comping editor). It covers the pointer, time selection, pencil, spray, knife, eraser, audition and step input tools, however you choose them: the tool buttons, the tool shortcuts and Commander, or holding a tool's key to use it for a moment (when you let go, every editor goes back to the previous tool together).

The clip launcher has no tools of its own, so nothing changes there.

## Several windows and display profiles

Bitwig keeps one set of editors per display profile (Settings > User Interface > Display profile), and every window of a profile shows the same ones. With a Dual or Triple monitor profile, choosing a tool in one window sets it in the editors of every window. The mod also sets it in the project's other display profiles, so switching profile keeps the tool you chose. A profile the project has never been opened with starts with Bitwig's default tool, the pointer.

## Tools an editor doesn't have

Step input is only in the piano roll and the drum editor. Choosing it there leaves the other editors on the tool they had. Every other tool is in every editor.

## What it leaves alone

- Opening a project: each editor starts with the tool saved in the project, as in Bitwig without the mod. The tools come together the next time you choose one.
- Tools inside devices (for example drawing in a device's own editor) are not editor tools and are left alone.

## Setting

Settings > Mods > Shared Tool Mode > **Same tool in every editor** (on by default). Turn it off and each editor keeps its own tool, as in Bitwig without the mod.

## Needs

- Framework: Settings (`preferences-composition-framework`)

Install it next to this mod.
