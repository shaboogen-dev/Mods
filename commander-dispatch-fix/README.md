# Commander: Run Order Crash Fix

For Bitwig 6.1.4 (Windows x64).

When you pick an action in Commander, Bitwig closes Commander first and then runs the action. For some actions, that order crashes Bitwig: run **Cut Time** from Commander with a time selection in the arranger, and Bitwig crashes, usually on the second run.

With this mod, Commander runs the action first and closes afterwards. Every action you start from Commander gets this order. Commander still closes if the action fails.

Nothing to set up: install it and use Commander as usual.
