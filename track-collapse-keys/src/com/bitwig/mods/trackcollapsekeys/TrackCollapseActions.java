package com.bitwig.mods.trackcollapsekeys;

import bitwig.obf.aFq; // the arranger's track list widget
import bml.actions.ActionProvider;
import bml.actions.Actions;

/**
 * Declares the two actions: real Bitwig actions, with no key until the user assigns one.
 *
 * They are bound on the arranger's track list and nowhere else, so a key they share with one of
 * Bitwig's actions stays Bitwig's everywhere outside the track list.
 */
public final class TrackCollapseActions implements ActionProvider {
    @Override
    public void declare(Actions actions) {
        actions.action("track_collapse_keys_collapse", "Collapse Track")
               .category("arranger")
               .bindOn(aFq.class)
               .onRun(context -> TrackCollapseKeys.apply((aFq) context.widget(), false));
        actions.action("track_collapse_keys_expand", "Expand Track")
               .category("arranger")
               .bindOn(aFq.class)
               .onRun(context -> TrackCollapseKeys.apply((aFq) context.widget(), true));
    }
}
