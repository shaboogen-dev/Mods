package com.bitwig.mods.addtodrummachine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import bitwig.obf.BO; // a device or plug-in result
import bitwig.obf.BP; // a result with a device type
import bitwig.obf.CH; // a browser result
import bitwig.obf.IT; // a preset result
import bitwig.obf.Ix; // an overlay preset result
import bitwig.obf.Mi; // a plug-in preset result
import bitwig.obf.Mt; // a plug-in preset result
import bitwig.obf.qD; // a result's kind
import bitwig.obf.qF; // a device's type
import bitwig.obf.zA; // a Bitwig preset result, which knows its device
import bml.actions.ActionContext;

/** Which browser results the row works on. */
final class Rows {
    private static final UUID DRUM_MACHINE = com.bitwig.flt.packaging.core.Wkj.ejd;

    private Rows() {
    }

    /** The clicked result, or the selection when it holds the clicked result; only the suitable ones, in order. */
    static List<CH> eligible(ActionContext context) {
        List<?> targets = context.items() != null && context.items().contains(context.item())
                ? context.items() : Collections.singletonList(context.item());
        return eligible(targets);
    }

    /** The suitable ones of these browser results, in order. */
    static List<CH> eligible(List<?> targets) {
        List<CH> rows = new ArrayList<>();
        for (Object target : targets) {
            if (target instanceof CH row && suitable(row)) rows.add(row);
        }
        return rows;
    }

    static boolean isSample(CH row) {
        return row.hP_() == qD.UNj;
    }

    private static boolean suitable(CH row) {
        qD kind = row.hP_();
        if (kind == qD.UNj || kind == qD.hUR) return true; // samples, multisamples
        boolean device = kind == qD.cfM || kind == qD.MRY || kind == qD.Hnt || kind == qD.FzB; // devices, plug-ins, and their presets
        return device && deviceType(row) == qF.Hnt && !holdsDrumMachine(row); // Hnt: instrument
    }

    private static qF deviceType(CH row) {
        if (row instanceof BP r) return r.Lcd();
        if (row instanceof IT r) return r.E1b();
        if (row instanceof Mt r) return r.E1b();
        if (row instanceof Mi r) return r.UZr();
        if (row instanceof Ix r) return r.cfM;
        return null;
    }

    /** A Drum Machine device, or a Bitwig preset whose indexed device is one. */
    private static boolean holdsDrumMachine(CH row) {
        if (row instanceof BO device && device.cfM() instanceof com.bitwig.flt.device.Wkj id) {
            return DRUM_MACHINE.equals(id.Hnt());
        }
        return row instanceof zA preset && DRUM_MACHINE.equals(preset.Z1C()); // Z1C(): its "device_id"
    }
}
