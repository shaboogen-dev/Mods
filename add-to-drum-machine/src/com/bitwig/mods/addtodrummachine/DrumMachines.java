package com.bitwig.mods.addtodrummachine;

import java.util.UUID;

import com.bitwig.base.io.z2y; // a file
import com.bitwig.flt.document.core.master.device.AxS; // a Bitwig device
import com.bitwig.flt.document.core.master.device.QkI; // makes Samplers
import com.bitwig.flt.document.core.master.device.zZo; // a device's list of chains
import com.bitwig.flt.document.core.master.qjn; // a sample, as a Sampler holds it

/**
 * Drum Machines and Samplers, built from calls every mapped Bitwig version has (6.0 to 6.1.4), so the
 * mod needs no version-specific code. Bitwig 6.1 added a helper class for Drum Machines and a
 * Sampler-from-file shortcut; these are the same few steps, through the calls those helpers make.
 */
final class DrumMachines {
    /** The Drum Machine's device ID. */
    private static final UUID DRUM_MACHINE = com.bitwig.flt.packaging.core.Wkj.ejd;
    /** The Drum Machine's pad list, by its preset identifier. */
    private static final String DRUM_PADS = "DRUM_PADS";

    private DrumMachines() {
    }

    /** Whether the device is a Drum Machine. */
    static boolean is(AxS device) {
        return DRUM_MACHINE.equals(device.AVR().Hnt()); // AVR(): its device ID; Hnt(): as a UUID
    }

    /** A new Drum Machine, not in the project. */
    static AxS create() throws Exception {
        return AxS.TMF(DRUM_MACHINE); // a new device of that ID
    }

    /** The Drum Machine's pads. */
    static zZo pads(AxS drum) {
        Object pads;
        try {
            pads = drum.puu().f_(DRUM_PADS); // puu(): its contents; f_: the child with that preset identifier
        } catch (Exception e) {
            throw new IllegalStateException("no pad list in the Drum Machine", e);
        }
        if (pads instanceof zZo list) return list;
        throw new IllegalStateException("no pad list in the Drum Machine");
    }

    /** A Sampler holding one sample file, as Bitwig's Alt-drag of a sample makes it. */
    static AxS sampler(z2y file) throws Exception {
        return QkI.cfM(qjn.cfM(file), false); // the sample, then a Sampler of it
    }
}
