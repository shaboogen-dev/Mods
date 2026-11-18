# Browser (side): Search Bar Shortcut

> **Built against**: Bitwig 6.1.4 (Windows x64)
>
> **Will map to**: All Bitwig versions Patchbay supports on Windows, Mac and Linux
>
> **Mod ID**: focus-side-browser-search
>
> **Prerequisites**: **Framework: Actions and Menus** 0.8.0-dev.1 or later.

## What does this do?

Accessing the search bar in the side browser currently relies on actions that feel unpredictable. The current *Focus Browser Search Field* action only works when the browser already has focus, which you can use *Focus/toggle Browser Panel* to get but depending on what is selected will either focus the browser or toggle the browser's visibility.

**Browser (side): Search Bar Shortcut** adds a simple, predictable action that puts the cursor in the Side Browser's search field from anywhere in Bitwig. This allows you to more quickly search for whatever you need and to not have to consider what is focused or whether clicking the shortcut one too many times will close the browser accidentally.

## How do you use it?

This mod adds an action named *Focus Side Browser Search Bar*. Assign a shortcut to it to use this action.

## Things to note

* This action will not close the Side Browser when it is visible.
* If the Side Browser is hidden, the action does nothing.
* **Framework: Browser** gives modders a chance to declare a search bar when creating a different view. Assuming they've taken advantage of this, this shortcut will put the cursor in that view's search field.
