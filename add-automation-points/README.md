# Arranger: Add Automation Points

Needs **Framework: Actions and Menus**.

Adds automation points at the start and the end of a time selection on an automation lane, with the values and the curve you choose, as one Undo step.

## Using it

Make a time selection on an automation lane (in the arranger, or in a clip's automation in the detail editor), then run **Add Automation Points…** from:

- **the time selection's right-click menu**;
- **the time-selection toolbar's dropdown**;
- **a shortcut** (Settings > Shortcuts) or **the Commander**: it runs on the last time selection made on an automation lane.

The menu entry is shown only while there is such a time selection and its points have one place to go (see below). Its action ID is `add_automation_points`.

## Automation clips

On an arranger automation lane, a time selection inside an **automation clip** edits the clip's own points, as Bitwig plays them: the selection is mapped into the clip's time (its position, play start and loop). The clip's content is changed, so a looped clip changes in every pass, and clips that share content change together.

A time selection that is only partly inside an automation clip, covers more than one clip, or runs across the end of a clip's loop pass has no single place for its points, so the entry isn't shown (from a shortcut or the Commander, nothing happens and the reason is logged). Outside clips, the lane's own automation is edited.

## The dialog

- **Point 1** and **Point 2**: each can be switched on or off. Their values are in the automated parameter's own units (dB, Hz, %...) and start at the automation's current values at the start and the end of the selection.
- **Curve**: the curve from Point 1 to Point 2, edited like the curve of an ADSR segment: drag it, Ctrl+click to type a value, double-click to reset it to 0%. The preview follows the two values, so a falling range previews a falling curve, and dragging follows the preview: dragging up bends the curve up, whichever way it runs.

On **OK**:

- With **both points** on, the points already inside the selection (its edges included) are replaced by the two new ones.
- With **one point** on, it is added and the existing points stay. With only Point 2, the curve is applied to the last existing point inside the selection.
- With **both off**, nothing changes.

A point is added after any point already at the same time. If the lane has no automation yet, it is created. The whole edit is one Undo step, "Add Automation Points".
