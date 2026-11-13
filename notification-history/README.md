# Notifications: History

Keeps what Bitwig's notification centre showed during the session, so a message that closed or dismissed itself can still be read.

- **Click the dot** at the top right of the window: a panel opens with the live entries (running tasks keep their progress bar and Cancel), then the history, newest first. Click the dot again, or anywhere outside the panel, to close it.
- **Clear** in the panel's header empties the history. Each history row's close button removes that row.
- **Right-click the dot** for its menu: **Show notifications**, ticked while Bitwig's notification overlay is shown, turns the overlay on or off (what clicking the dot does without this mod).

What the history keeps: every message and task the centre showed, from Bitwig and from every mod, once it has gone (closed, dismissed itself, finished, cancelled or removed). A message keeps its title, text and severity, with the time it was first shown ("14:32 · The disk is full."). A task reads "Finished · 14:32" or "Cancelled · 14:32". Action buttons are left out.

Limits:

- At most 100 entries, for this session only.
- Background tasks Bitwig keeps behind the spinner next to the dot (such as re-indexing the library) aren't in the centre, so they aren't recorded.
- An entry Bitwig never showed (a task that ended within 300 ms, a message its mod replaced at once) isn't recorded.
- On macOS, Ctrl-click may not open the dot's menu; use a right-click or a two-finger click.

Requires Framework: Notifications 0.3.0-dev.1 or later. Turning the mod off gives the dot back its own click.
