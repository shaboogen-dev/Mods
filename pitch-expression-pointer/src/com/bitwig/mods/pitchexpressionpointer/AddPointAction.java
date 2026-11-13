package com.bitwig.mods.pitchexpressionpointer;

import bitwig.obf.eoV; // a set of modifier keys
import com.bitwig.base.geom.DoublePoint; // a mouse position
import com.bitwig.flt.widget.core.timeline.XzI; // the point and curve engine
import com.bitwig.flt.widget.core.timeline.interaction.InsertAndMoveExpressionEventDragAction; // adds a point, then moves it
import com.bitwig.flt.widget.core.timeline.interaction.pwQ; // a hit: a location on a curve

/**
 * Bitwig's add-point action, snapping new points the way Bitwig snaps a note's first point.
 *
 * Bitwig's own version keeps a point's distance from the grid, which is right for moving an
 * existing point but leaves a new one where it was pressed when relative snapping is on.
 */
final class AddPointAction extends InsertAndMoveExpressionEventDragAction {
    AddPointAction(XzI grid, pwQ hit, DoublePoint at, eoV mods) {
        super(grid, hit, at, mods, true); // true: as Bitwig builds it, time can be edited
    }

    /** Runs from Bitwig's constructor too, so it uses only its arguments and the widget. */
    @Override
    public double quantizeTime(DoublePoint at, eoV mods, double time) {
        return getWidget().MRY(time, at, mods); // the snap Bitwig uses for a note's first point
    }
}
