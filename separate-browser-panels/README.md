# Panel: Separate Browser Panels

Shows the **Browser Panel**, **Project Panel**, **Studio Monitoring Panel** (Studio I/O) and **Mappings Panel** as panels of their own, side by side, each a full-height column with its own width, instead of as tabs of one panel area. Turning the mod on or off applies when Bitwig starts again.

- **Order:** always Browser Panel, Project Panel, Studio Monitoring Panel, Mappings Panel, from the window's edge towards the arranger. With the Browser panel on the left (Panel: Choose Sides) it is mirrored: the Browser at the left edge.
- **Opening a panel** adds its column in its place in that order; the arranger (the main area) gets narrower, and the columns already open keep their widths. **Closing** one gives its width back to the arranger.
- **A column's first width** is the Browser column's width at the moment it is first opened. After that it keeps its own width.
- **The Inspector** stays its own column, as Bitwig has it, so it can be open together with all four.
- **Their buttons** are Bitwig's own, where Bitwig puts them in the bottom bar.
- **Click** a panel's button: that panel alone. **Click it again** while it is shown alone: it closes, and with it the whole side.
- **Cmd-click** (macOS) or **Ctrl-click** (Windows, Linux): add the panel's column, or remove it. The last one can be removed too.
- **Widths:** drag the divider beside a column to change its width. Each column's width, and whether it is shown, are saved with the project's view settings. The Browser keeps the width the Browser panel had.
- **Bitwig's own actions** that show or hide these panels (Toggle Browser Panel and the others, and their focus versions) and controllers that show or hide the Browser still work, on each panel on its own.
- **Display profiles:** the first window of each profile whose Browser panel area holds exactly these four. Where the area also holds the Inspector (Single Small Monitor, Tablet), and in further windows, it stays as tabs.
- **Projects saved before** (or without the mod) open once with only the Browser shown; from then on the project keeps its own panels and widths.

## Shortcuts

In Bitwig's **Panel Management** category in Settings > Shortcuts and the Commander, from Framework: Panels. None has a key until you give it one.

| Action | Does |
|---|---|
| Show Browser Panel / Project Panel / Studio Monitoring Panel / Mappings Panel | that panel alone; it never closes it |
| Add or Remove Browser Panel / Project Panel / Studio Monitoring Panel / Mappings Panel in the Split | as Cmd/Ctrl-click on its button |

They replace Bitwig's Select Sub-panel 1-4 and Select Next / Previous Sub-panel, which no longer reach these panels.

## Scale

With **Panel: Scale Each Panel**, each column has its own scale: right-click its button, or Settings > Mods > Scale Each Panel.

Built against Bitwig 6.1.4 (Windows). Needs **Framework: Panels**.
