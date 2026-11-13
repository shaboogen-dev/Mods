package com.bitwig.mods.pitchexpressionpointer;

import bitwig.obf.dJI; // a mouse release
import bitwig.obf.eoV; // a set of modifier keys
import com.bitwig.base.geom.DoublePoint; // a mouse position
import com.bitwig.flt.widget.core.timeline.interaction.Hv3; // the gesture: click adds a point, drag adds and moves it
import com.bitwig.flt.widget.core.timeline.interaction.pwQ; // a hit: a location on a curve
import com.bitwig.flt.widget.core.timeline.kig; // the piano roll's note grid
import java.util.Collections;

/** Bitwig's add-point gesture. A click adds a point, a drag adds and moves one. */
final class AddPointGesture extends Hv3 {
    AddPointGesture(kig grid, pwQ hit, DoublePoint at, eoV plain) {
        super(null, Collections.EMPTY_LIST, new AddPointAction(grid, hit, at, plain), grid.Hnt(), grid);
    }

    @Override
    public void MRY(dJI release) { // released without a drag: Bitwig adds the point here
        DoubleClick.addingPoint(qoU(), release, () -> super.MRY(release));
    }
}
