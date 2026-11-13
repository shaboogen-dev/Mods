package com.bitwig.mods.panelsides;

import java.util.function.Supplier;

import bitwig.obf.akZ; // a splitter between two parts of a window's layout
import bitwig.obf.akq; // a layout part used here as a stand-in
import bitwig.obf.akw; // a part of a window's layout

/**
 * A top/bottom split added above an existing part without changing that part's saved values.
 * A layout part's saved values (sizes, visibility) are keyed by its path of names; this split
 * gives its upper part the path it has without the split, so nothing saved resets. Optionally
 * the split takes another split's path for itself (and so that split's saved divider position).
 *
 * Used for the device panel across the full width (in the path of the device panel's split
 * inside the main area) and for the split that adds a mod's view under the Mixer.
 */
final class PassThroughSplitter extends akZ {
    private final Supplier<String> ownPath; // null: Bitwig's path rule

    private PassThroughSplitter(Supplier<String> ownPath) {
        this.ownPath = ownPath;
    }

    /** A split named {@code name}, with stand-ins as its parts until they are set. */
    static PassThroughSplitter create(String name, Supplier<String> ownPath) {
        PassThroughSplitter split = new PassThroughSplitter(ownPath);
        split.MRY(name, false, akq.cfM("panel-sides-stand-in"), akq.cfM("panel-sides-stand-in")); // name, top/bottom, two stand-ins
        split.H_(); // Bitwig's initialisation
        return split;
    }

    /** Its path (also the key of its saved divider position). */
    @Override
    public String jG() {
        return ownPath != null ? ownPath.get() : super.jG();
    }

    /** Bitwig's path rule for its two parts: the upper one keeps the path it has without this split. */
    @Override
    public String cfM(akw child) {
        if (child == E3()) { // E3(): the upper part
            akw parent = uva();
            return parent != null ? parent.jG() + "." + child.tSS() : child.tSS();
        }
        return jG() + "." + child.tSS();
    }
}
