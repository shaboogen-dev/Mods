package com.bitwig.mods.minimiseblocks;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import bitwig.obf.AQw; // an on/off value
import bitwig.obf.OpO; // Bitwig's fill size policy
import bitwig.obf.Sxr; // Bitwig's fixed size policy
import bitwig.obf.VGL; // a value's change event
import bitwig.obf.dAd; // a toggle widget
import bitwig.obf.foz; // a listener

/** The minimise arrow: Bitwig's own tree disclosure, built the way Bitwig builds its header rows. */
final class Arrows {
    private Arrows() {
    }

    /**
     * Bitwig's toggle bound to an on/off value ("expanded"), styled "Tree expand down" (the theme's fold arrows) and
     * sized with Bitwig's policy, its height fixed to fit the title row and centred in it. The toggle handles its own
     * clicks and redraws from the value. {@code onChange} applies a new state and returns false to refuse it (the
     * arrow then stays as it was).
     */
    static dAd create(boolean expanded, Predicate<Boolean> onChange) {
        dAd arrow = dAd.cfM(new Value(expanded, onChange)); // a toggle showing the value
        arrow.iHb("Tree expand down"); // its style
        arrow.a_(OpO.cfM()); // width: Bitwig's fill policy
        // Fixed height 13: the style's own minimum height, and the fill policy too, make block headers taller than
        // Bitwig's.
        arrow.MRY(new Sxr(13)); // height
        arrow.E3(0.5f); // vertical alignment: centred
        return arrow;
    }

    /** The on/off value behind an arrow. */
    private static final class Value implements AQw {
        private volatile boolean expanded;
        private final Predicate<Boolean> onChange;
        private final List<foz> listeners = new CopyOnWriteArrayList<>();

        Value(boolean expanded, Predicate<Boolean> onChange) {
            this.expanded = expanded;
            this.onChange = onChange;
        }

        @Override
        public boolean getBoolValue() {
            return expanded;
        }

        @Override
        public void setBoolValue(boolean next) {
            if (next == expanded || !onChange.test(next)) return;
            boolean old = expanded;
            expanded = next;
            VGL event = new VGL(this, old, next); // the change, as Bitwig's values report it
            for (foz listener : listeners) {
                try {
                    listener.eventOccurred(event);
                } catch (Throwable t) {
                    MinimiseBlocks.MOD.log("a listener of a minimise arrow failed", t);
                }
            }
        }

        @Override
        public Object getValue() {
            return expanded;
        }

        @Override
        public void setValue(Object value) {
            setBoolValue(Boolean.TRUE.equals(value));
        }

        @Override
        public boolean isSettable() {
            return true;
        }

        @Override
        public boolean isObservable() {
            return true;
        }

        @Override
        public void addListener(foz listener) {
            if (!listeners.contains(listener)) listeners.add(listener);
        }

        @Override
        public void removeListener(foz listener) {
            listeners.remove(listener);
        }

        @Override
        public String toString() {
            return "InspectorArrow[expanded=" + expanded + "]";
        }
    }
}
