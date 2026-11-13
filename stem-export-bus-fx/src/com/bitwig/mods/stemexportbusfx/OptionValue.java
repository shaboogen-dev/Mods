package com.bitwig.mods.stemexportbusfx;

import bitwig.obf.bDS; // a plain boolean value a checkbox can show and change

/** The dialog checkbox's value: Bitwig's boolean value, saved whenever the user changes it. */
public final class OptionValue extends bDS {
    public OptionValue(boolean initial) {
        super(initial);
    }

    @Override
    public void setBoolValue(boolean value) {
        super.setBoolValue(value);
        StemExportBusFx.setEnabled(value);
    }
}
