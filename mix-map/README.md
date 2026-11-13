# Panel: Mix Map

For Bitwig 6.1.4 (Windows x64). Needs **Framework: Panels** 0.2.0 or later, which places the panel and gives it its button, its Show action and its scale, and **Framework: Settings** 1.0.0 or later, for the scale's setting.

A panel that shows every track as a dot, left to right by pan and top to bottom by volume. Drag the dots to mix.

**Use it with Panel: Streamlined Views.** Together, Mix Map is a view of its own in the view stack:
- Shown alone or split with Tracks, Detail Editor or Mixer, at any height.
- With Show and split actions, and Equalise View Sizes.

**Without Streamlined Views**, Mix Map lives beside Bitwig's own panels:
- **In MIX** it takes turns with the mixer as the main content: Mix Map does the mixer's job, so the two don't share the screen.
  - The bottom bar's row for MIX has a Mixer and a Mix Map button; a click on either shows it full size.
  - The mixer is shown by default, and Bitwig's own Toggle Mixer Panel brings it back.
  - MIX remembers which of the two was shown, saved with the project.
- **In ARRANGE and EDIT** it is one more tab of the mode's lower panel, next to Bitwig's own Detail Editor, Devices and Mixer tabs.
  - Its button sits in the bottom bar beside theirs. A click shows Mix Map in the lower panel (under the arranger or the Detail Editor), or closes the panel if Mix Map is already shown.
  - One tab is shown at a time, and each mode remembers its own lower panel.
- **Limits:**
  - It can't be split or resized beside other views.
  - Display profiles without such a lower panel don't show it: Tablet, Dual Monitor Arranger/Mixer, Triple Monitor, and some windows of the dual-monitor profiles.

## The panel

- **The stage:** every main track is a dot in its track colour. Group tracks and each of their tracks are included; effect tracks and the master are left out. Left to right is pan, top to bottom is volume (loud at the top), with more room near the top, as on a fader. A ring around a dot shows its level while audio plays. Each selected track's name is shown beside its dot.
- **Scales:**
  - The stage's top edge is +6 dB and its bottom edge -inf. Between them are gridlines at 0, -6, -12, -24 and -48 dB, all the same distance apart, labelled beside the centre line.
  - Its left and right edges are L100 and R100, with gridlines at L75, L50, L25, C, R25, R50 and R75, labelled along the bottom.
  - The 0 dB and C lines are a little stronger.
- **The list** on the right names the same tracks in Bitwig's order, with their colours. Click a name to select the track; **Shift-click** a name to add its track to the selection or take it out; drag in the list to scroll it.
- **Hiding a dot:** click a track's colour square in the list to hide its dot, and again to show it.
  - A hidden track's square is an outline and its name is dimmed. Its dot isn't drawn and can't be clicked or dragged.
  - Hiding changes nothing in the project or the track. The hidden tracks are Mix Map's own, saved with the project's view settings (as panel sizes are).
  - It is one set per display profile, the same wherever Mix Map is shown: in each mode's tab and in the view stack.
  - A hidden track can still be selected in Bitwig, but Mix Map doesn't move or reset its hidden dot.
- **Selecting:** Mix Map's selection is Bitwig's track selection.
  - Click a dot, or a name, to select its track alone. Selecting tracks anywhere else in Bitwig selects their dots here too (several with Ctrl- or Shift-click in the mixer or the arranger).
  - Selected dots are ringed and their names highlighted in the list. Every selected dot has its track's name beside it; the current track's (the one the device panel shows) is brighter.
  - Names are drawn under the dots, so a name never hides a dot; where selected dots sit close together, their names can overlap.
- **Selecting several dots:**
  - Drag a rectangle on empty space to select the tracks of the dots in it; hold **Shift** to add them to the selection.
  - **Shift-click** a dot to add its track or take it out.
  - Clicking a dot that is already selected makes it the current track and keeps the others selected.
  - Click empty space to clear the selection. **Escape** while drawing the rectangle puts the previous selection back.
- **Overlapping dots:** dots aren't spread out. Later tracks are drawn above earlier ones, selected dots above the rest, and the current track above all. A click takes the dot drawn on top. Use the list to reach a track whose dot is covered.
- **Dragging** a dot sets pan and volume together, as one move (one undo step).
  - Dragging a selected dot moves all the selected dots by the same distance, across and up or down, so they keep their spacing on the stage.
  - The group stops when its first dot reaches an edge (L100, R100, +6 or -inf).
  - As dots cross into the scale's wider or narrower bands, the dB differences between them change with the scale.
  - Hold **Shift** while dragging to move along one axis only. The direction you move first decides it (left-right: pan only, up-down: volume only), and releasing Shift frees both again.
- **Resetting:** **double-click** a dot to reset pan and volume to their defaults. **Cmd-click** (macOS) or **Ctrl-click** resets the volume, **Alt-click** the pan. On a selected dot, these reset every selected dot.

## Scale and actions

These come from Framework: Panels.
- **Scale:** Mix Map's scale is in **Settings > Mods > Mix Map**, or right-click its button: 75% to 200%.
- **Show Mix Map:** an action in Bitwig's **Panel Management** category. With Streamlined Views it shows Mix Map alone. Without, in MIX it shows Mix Map in the mixer's place, and in ARRANGE and EDIT it shows or hides its tab.
- **With Streamlined Views** there is also **Add or Remove Mix Map in the Split**.
