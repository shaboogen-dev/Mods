# Panel: Choose Sides

Puts the Inspector and the Browser panel each on the left or the right of the window. The Browser panel is the panel area with the Browser, Project, Studio I/O and Mappings tabs.

Two settings in **Settings > Mods > Choose Sides**:

- **Inspector side**: Left (Bitwig's layout) or Right.
- **Browser panel side**: Left or Right (Bitwig's layout).

Each panel's buttons in the bottom bar follow it: a panel on the left has its buttons at the bar's left end, and a panel on the right at its right end.

A change applies at once: the window is rebuilt, as when a display profile is chosen in Settings, and open plug-in windows close and open again. The four layouts, left to right:

| Inspector | Browser panel | Window |
|---|---|---|
| Left | Right | Inspector, main area, Browser panel (Bitwig's layout) |
| Right | Left | Browser panel, main area, Inspector |
| Right | Right | main area, Browser panel, Inspector |
| Left | Left | Inspector, Browser panel, main area |

- **Every display profile** follows the settings, in every window that has an Inspector or a Browser panel beside the main area.
- **One panel for both:** where the Inspector is a tab in the Browser panel (Single Small Monitor, Tablet, and some windows of the dual and triple monitor profiles), it follows the Browser panel setting.
- **Panel widths** stay with their panels: a project's saved Inspector width is still the Inspector's width on the other side.
- **With Panel: Separate Browser Panels**, the columns follow the Browser panel setting as one, mirrored: the Browser stays at the window's edge.
- With both settings at their defaults, or with the mod off, Bitwig's layout is not changed. Nothing is saved in projects.

If a display profile isn't laid out as expected, it stays as Bitwig builds it and the mod log says so.

Built against Bitwig 6.1.4 (Windows). Needs **Framework: Panels** and **Framework: Settings**.
