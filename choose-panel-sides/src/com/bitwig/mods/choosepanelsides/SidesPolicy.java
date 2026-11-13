package com.bitwig.mods.choosepanelsides;

import bml.panels.Layout;
import bml.panels.LayoutPolicy;
import bml.panels.PanelProvider;
import bml.panels.Panels;

/**
 * The mod's registration with Framework: Panels: a layout policy with nothing to build, whose
 * arrange step (after every layout mod has built its layout and the views are placed) puts the
 * Inspector and the Browser panel on their sides.
 */
public final class SidesPolicy implements PanelProvider, LayoutPolicy {
    @Override
    public void declare(Panels panels) {
        panels.policy(new SidesPolicy());
    }

    @Override
    public void layout(Layout layout) {
        // nothing to build: the sides are arranged around whatever the other layout mods built
    }

    @Override
    public void arrange(Layout layout) {
        ChoosePanelSides.arrange((bitwig.obf.akC) layout.profile());
    }
}
