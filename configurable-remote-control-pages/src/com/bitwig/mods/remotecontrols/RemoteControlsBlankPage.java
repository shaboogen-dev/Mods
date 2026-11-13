package com.bitwig.mods.remotecontrols;

import bitwig.obf.dnF; // a widget drawn by its own code
import bitwig.obf.dve; // a widget
import com.bitwig.base.geom.IBe; // an axis: horizontal or vertical
import com.bitwig.base.geom.tfK; // a size

/** An empty widget the size of a rendered Remote Controls page, filling an incomplete final block. */
final class RemoteControlsBlankPage extends dnF {
    private final tfK fixedSize;

    private RemoteControlsBlankPage(tfK size) {
        this.fixedSize = size.Hnt(); // a copy
    }

    /** A blank page as large as {@code template}; null without a template. */
    static RemoteControlsBlankPage sizedLike(dve template) {
        if (template == null) return null;
        long width = template.TtY(IBe.cfM); // its size along the horizontal axis
        long height = template.TtY(IBe.MRY); // along the vertical axis
        RemoteControlsBlankPage blank = new RemoteControlsBlankPage(new tfK(width, height));
        blank.PlX(); // lay it out
        blank.H_(); // and mark it for drawing
        return blank;
    }

    /** Every size Bitwig asks the widget for: the template's. */
    @Override
    public tfK iH_() {
        return fixedSize.Hnt();
    }

    @Override
    public tfK mL_() {
        return fixedSize.Hnt();
    }

    @Override
    public tfK nj_() {
        return fixedSize.Hnt();
    }

    @Override
    public tfK iI_() {
        return fixedSize.Hnt();
    }
}
