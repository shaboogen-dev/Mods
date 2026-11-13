package com.bitwig.mods.hibernatetracks;

import bitwig.obf.FNX; // a track's mixer
import bitwig.obf.bDH; // a parameter
import bitwig.obf.euX; // a send
import bitwig.obf.xJo; // a track
import com.bitwig.flt.document.core.iface.hgq; // a list of effect tracks
import com.bitwig.flt.document.core.iface.z2y; // what an automation lane automates
import com.bitwig.flt.document.core.master.TL; // a channel

import java.util.ArrayList;
import java.util.List;

/** Carries a track's mixer settings to another track: fader, pan, mute, sends and output. */
final class Mixer {
    private Mixer() {
    }

    /** True if the track has an automation lane on its fader, pan, mute or a send. */
    static boolean hasAutomation(xJo track) {
        FNX mixer = track.TbN();
        if (lane(track, mixer.tSS()) || lane(track, mixer.nfq()) || lane(track, mixer.Coi())) return true; // volume, pan, mute
        for (Object send : mixer.iW2()) { // the sends
            if (send instanceof euX s && lane(track, s.jhH())) return true; // its amount
        }
        return false;
    }

    private static boolean lane(xJo track, bDH parameter) {
        return track.mvz().Hnt((z2y) parameter.Mph()) != null; // the lane for its automation target, if any
    }

    /**
     * Bitwig adds a track's sends later, not when the track joins the project. This makes them now,
     * inside the open undo step, the way Bitwig's own step does: one for each effect track the
     * track can send to, skipping any it has. Bitwig's own step then finds nothing to add.
     */
    private static void makeSends(xJo track) {
        FNX mixer = track.TbN();
        for (Object list : track.ESa()) { // the effect track lists of its group and the groups above
            hgq targets = (hgq) list;
            for (int i = 0; i < targets.cU_(); i++) {
                TL target = (TL) targets.cfM(i);
                if (mixer.cfM(target) != null) continue; // its send to that effect track
                euX send = euX.cfM(target); // a new send
                mixer.MRY(send); // add it
                send.fFs(); // settle its pre or post state
            }
        }
    }

    /** Copies the settings. Returns the names of send targets with no counterpart on the other track. */
    static List<String> copy(xJo from, xJo to) {
        makeSends(to);
        FNX a = from.TbN();
        FNX b = to.TbN();
        b.tSS().d_(a.tSS().iCS()); // volume
        b.nfq().d_(a.nfq().iCS()); // pan
        b.Coi().cfM(a.Coi().hUR()); // mute
        List<String> missing = new ArrayList<>();
        for (Object item : a.iW2()) {
            euX send = (euX) item;
            TL target = send.QNk(); // the channel it sends to
            if (target == null) continue;
            euX other = b.cfM(target); // Bitwig's lookup of the send to that channel
            if (other == null) {
                missing.add(target.cb_());
                continue;
            }
            other.jhH().d_(send.jhH().iCS()); // amount
            other.cfM(send.hUR()); // enabled
            other.Hnt(send.jFL()); // pre or post fader
            other.fFs(); // settle its pre or post state
        }
        if (from.OnS().tp2() != null) to.OnS().IwV(from.OnS().tp2()); // the track's output
        return missing;
    }
}
