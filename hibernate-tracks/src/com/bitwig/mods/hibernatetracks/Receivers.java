package com.bitwig.mods.hibernatetracks;

import bitwig.obf.lbA; // an audio source selector: a sidechain, a track's input, an audio receiver
import bitwig.obf.qy; // an audio source
import bitwig.obf.xJo; // a track
import com.bitwig.flt.document.core.master.g2L; // a project
import com.bitwig.ramona.core.zjj; // walks a project's objects of one class

import java.util.ArrayList;
import java.util.List;

/**
 * Everything in the project that listens to a track's audio, moved to another track. Moving them
 * back needs no record: on Wake, whatever listens to the copy goes to the source.
 */
final class Receivers {
    private Receivers() {
    }

    /** Returns how many listeners are tapped inside the track's chain and can't move. */
    static int move(g2L project, xJo from, xJo to) {
        List<lbA> selectors = new ArrayList<>();
        zjj.MRY(project, lbA.class, null, object -> selectors.add((lbA) object));
        int stuck = 0;
        for (lbA selector : selectors) {
            qy source = selector.MIO(); // what it listens to
            if (source == null || source.rGR() != from) continue; // rGR: the track the source belongs to
            if (selector.IwV(xJo.class) == from) continue; // inside the track itself
            qy counterpart = counterpart(source, from, to);
            if (counterpart == null) {
                stuck++;
                continue;
            }
            selector.cfM(counterpart); // change the source, as the routing chooser does
        }
        return stuck;
    }

    private static qy counterpart(qy source, xJo from, xJo to) {
        if (source == from.LIl()) return to.LIl(); // pre-fader
        if (source == from.SLC()) return to.SLC(); // post-fader
        if (source == from.Reg()) return to.Reg(); // post-fader with delay compensation
        return null;
    }
}
