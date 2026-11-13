# Notifications: Tidy Task Rows

Every task in the notification centre (the dot at the top right of the window), Bitwig's own and other mods', reads top to bottom: title, status, progress bar, then Cancel at the right, with the title, status and bar starting at the same left edge (a task Bitwig shows its spinning wheel for keeps it before the title). The status is in the same font and colour as the title and wraps onto more lines instead of being cut in the middle. The bar shows for as long as the task runs, with Bitwig's busy animation while how far it is isn't known. Sub-tasks and images stay where Bitwig puts them, and messages keep Bitwig's layout.

It costs nothing while drawing: the layout is set once, when Bitwig builds a row. Turning the mod off gives the rows back to Bitwig.

- Mod ID: `tidy-notification-rows`
- Built against Bitwig 6.1.4, Windows x64.
- Needs Framework: Notifications 0.3.0-dev.1 or later.

## Other mods

When another mod also lays out task rows, one layout is used (the last mod in order of mod ID), and the log says which.
