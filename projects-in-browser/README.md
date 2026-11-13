# Browser: Enable Project File Filter

Shows project files in Bitwig's Everything browser.

- Mod ID: `projects-in-browser`
- Built against Bitwig 6.1.4.
- Needs Framework: Browser (`browser-framework`) 0.1.0-dev.43 or later, which Patchbay ticks with it.

## What it does

- **Projects in Everything.** Project files (`.bwproject`) appear among Everything's results. Bitwig has a Projects file kind that Everything leaves out; the mod adds it through Framework: Browser, which also lists **Projects** as a kind (under BY KIND in Browser: Pre-5.0 Style Side Browser, and in Bitwig's source picker) with the tile of Bitwig's Templates kind.
- **Project filter.** A sixth filter, **Project**, after Bitwig's own. Its chooser lists the projects in Bitwig's Projects location under "All Projects" > "Projects", like the Device filter's chooser: every `.bwproject` file at the top of the location and in the top level of each project folder, also inside your own subfolders. A folder holding a `.bwproject` file is a project folder, and its subfolders (samples, recordings, bounces) aren't searched; hidden and system folders are skipped. Choosing a project opens it (below); "All Projects" closes it.
- **Opening a project.** A project row in the results has an arrow ("Show project contents"). Clicking it, or choosing the project in the Project filter, replaces the results with the project's tracks, and the Project chip shows the project's name. The arrow ("Back to search results") or the chip's X goes back to the results and the search text from before. You can switch straight from one project to another.
- **Track hierarchy.** Inside a project, group tracks have their own arrows ("Expand group" / "Collapse group"), and tracks are indented by depth, as in Bitwig's File Browser.
- **Drag and drop.** Tracks inside a project drag as Bitwig's own project contents, as from the File Browser.
- **Icon.** The Projects file kind refers to an icon Bitwig doesn't ship (`browser/project.svg`); Bitwig's document icon is shown instead. No Bitwig files are changed.

## Limitations

- The project list is found in the background and kept: a browser opened for the first time may show "Projects (scanning)" for a moment. When the list is more than 30 seconds old, opening a browser or the Project chooser refreshes it in the background, and the chooser shows the new list once it's ready (if the chooser is already open, at its next opening). So a project saved a moment ago can take one more opening to appear.
- The Project filter doesn't narrow Bitwig's search: it opens one project. Its chooser shows no hit counts for single projects.
