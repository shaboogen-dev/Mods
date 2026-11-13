package com.bitwig.mods.panelsides;

import bitwig.obf.akq; // the main view area: one layout per mode
import bitwig.obf.akw; // a part of a window's layout

/**
 * The main view area, with one layout for every mode. A layout part's saved values (sizes,
 * visibility) are keyed by its path of names; this area names its parts' paths itself, so that
 * the views keep their paths when the device panel moves out to the full width: the views' split
 * is then named as if it were still inside the device panel's split.
 */
final class MainArea extends akq {
    private akw throughChild;   // a part placed here in place of the split it is named through
    private String throughName; // that split's name

    private MainArea() {
    }

    static MainArea create(String name) {
        MainArea area = new MainArea();
        area.MRY(name); // its name
        area.H_();      // Bitwig's initialisation
        return area;
    }

    /** From now on, {@code child} sits here directly but is named as a part of the split {@code splitName}. */
    void nameThrough(akw child, String splitName) {
        throughChild = child;
        throughName = splitName;
    }

    /** The path of a part of the area's one layout, as Bitwig names it ({@code <area>.1.<name>}). */
    String casePath(String name) {
        return jG() + ".1." + name; // jG(): the area's own path
    }

    /** Bitwig's path rule for the parts of this area. */
    @Override
    public String cfM(akw child) {
        if (child == throughChild) return casePath(throughName) + "." + child.tSS(); // tSS(): its name
        return casePath(child.tSS());
    }
}
