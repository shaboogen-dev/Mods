# Commander: Assign Aliases to Actions

> **Built against**: Bitwig 6.1.4 (Windows x64)
>
> **Will map to**: All Bitwig versions Patchbay supports on Windows, Mac and Linux
>
> **Mod ID**: shortcut-aliases
>
> **Prerequisites**: None

## What does this do?

Although Bitwig offers an extremely flexible shortcut assignment system, there are a couple of inherent flaws in assigning a keystroke to every action you may wish to use. For starters, you only have a finite number of keystrokes available (even with modifiers) but more importantly it can be difficult to remember the keystroke for an action that may be important enough to assign a keystroke to but that you may not use all the time.

Bitwig offers the Commander as a way to trigger any action, but using it requires you to remember the name of the action specifically in order to trigger it.

**Commander: Assign Aliases to Actions** lets you give any action a short alias of your own that can be used to find actions in the Commander, allowing you to potentially do something like referring to something by a keyword, or perhaps an acronym.

## How do you use it?

1. Open **Settings > Shortcuts**. A new column appears, pushing the shortcuts column/s further to the right side.
2. Click the **+** in an action's Alias column, type an alias and click **Add**.
3. Open the Commander and type the alias. The action will be found based on the alias.

You can remove an alias by clicking the **X** exactly like removing a shortcut.

## Things to note

* Each action can have one alias. Each alias can belong to only one action. If an alias is already taken, the popup says which action has it as you type.
* Aliases are not case sensitive, so `Sil` and `sil` are the same alias.
* An alias works like an action name in the Commander in that the results list will continually filter down as you type.
* Like shortcuts, aliases are active straight away and the file is updated as soon as you add or remove one.
* Aliases are kept in Patchbay's settings folder (**Settings > Settings Folder** in Patchbay), in `mods/shortcut-aliases/config/commander-aliases.cfg`. These are kept separate from your Bitwig keymap and the file is created when you add your first alias.
  * If you edit that file by hand and it can't be read, aliases are switched off until it's fixed and Bitwig is restarted. The reason is in the log.
* This is a good mod to pair with **Commander: Enter Selects First Result** as it helps you trigger actions more quickly.
