# Panel: Scale Each Panel

Gives each of Bitwig's main panels its own scale: 75%, 100% (the default), 125%, 150%, 175% or 200%.

## The panels

- **Tracks** (the arranger), the **Detail Editor**, **Mixer** (the big mixer and the small one) and the **device panel**.
- The **Inspector**.
- Each panel of the browser side on its own: **Browser Panel**, **Project Panel**, **Studio Monitoring Panel** and **Mappings Panel** (Studio I/O). They share the side's width, so a panel at a larger scale shows less of itself in the same width. The Project panel's own tabs (Settings, Info and the others) follow the Project panel's scale.
- The **On-Screen Keyboard**. Larger keys mean fewer of them fit in its width; its lowest note stays where it is.

## How it works

- **Where to set it:** **Settings > Mods > Scale Each Panel**, or right-click the panel's button in the bottom bar (for example the Detail Editor, Devices and Mixer buttons of a mode's lower panel, or the Inspector and Browser buttons).
- **Where it applies:** the same setting wherever the panel is shown: as a mode's main content, as a lower-panel tab, as a panel beside the main area, as a tab of the browser side, or in a view stack or a side column (Panel: Streamlined Views).
- **When it applies:** at once, in every window.
- **How it scales:** relative to Bitwig's own UI scale, in quarter steps. With Bitwig at 100% every choice is exact; with Bitwig at 125%, a panel at 125% shows at 150%.
- **What follows it:** everything in the panel: text, icons, knobs and their hit areas. Menus, tooltips and pop-up windows opened from a scaled panel appear at Bitwig's own scale.
- **Exceptions:**
  - A larger device panel gets taller, as far as its content needs.
  - A larger Inspector gets wider, as far as its content needs.
  - The pop-up browser (the "+" in a device chain) is its own window and keeps Bitwig's scale.

The scales are kept in your Patchbay settings, not in projects. Turned off, every panel shows at Bitwig's own scale again.

Built against Bitwig 6.1.4 (Windows). Needs **Framework: Panels** and **Framework: Settings**.
