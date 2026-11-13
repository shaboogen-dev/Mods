# Browser (side): Search Bar Shortcut

- Adds one action, **Focus Side Browser Search Bar**, listed under **Browser**, that you can assign a shortcut to in Settings > Shortcuts or run from the Commander.
- Running it focuses the search field of the docked Side Browser in the window you are in.
- When a browser view with its own content shows there (chosen in Settings > Mods > Browser Configuration > Browser view, such as Browser: Folders), it focuses that view's own search field instead, if the view has one. This needs **Framework: Browser Configuration** 0.2.0-dev.2 or later; without it the shortcut works as above.
- It does not change the query or open, close or toggle the browser.
- When that window has no docked Side Browser showing, it does nothing.

## Requirements

- Bitwig Studio 6.1.4 (Windows x64).
- BML loader 0.34.0-dev.7 or later.
- **Framework: Actions and Menus** 0.5.0 or later, which registers the action.
- Optional: **Framework: Browser Configuration** 0.2.0-dev.2 or later, for browser views' own search fields. It isn't required: the mod looks for it when the shortcut runs.
