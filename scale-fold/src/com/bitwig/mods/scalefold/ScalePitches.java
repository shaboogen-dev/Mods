package com.bitwig.mods.scalefold;

import java.util.BitSet;

final class ScalePitches {
    private ScalePitches() {}

    static BitSet of(int root, BitSet intervals) {
        BitSet pitches = new BitSet(128);
        for (int pitch = 0; pitch < 128; pitch++) {
            if (intervals.isEmpty() || intervals.get(Math.floorMod(pitch - root, 12))) {
                pitches.set(pitch);
            }
        }
        return pitches;
    }
}
