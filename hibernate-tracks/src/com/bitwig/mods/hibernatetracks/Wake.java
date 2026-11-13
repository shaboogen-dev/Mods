package com.bitwig.mods.hibernatetracks;

import bitwig.obf.xJo; // a track
import com.bitwig.flt.document.core.iface.WSA; // a track, as the group lists it
import com.bitwig.flt.document.core.master.g2L; // a project
import com.bitwig.flt.document.core.master.ruS; // a group track
import com.bitwig.flt.document.core.master.zTW; // Bitwig's track operations
import com.bitwig.flt.document.core.master.zwV; // a channel

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Wake Track: finds every pair first; if one is missing it says so and changes nothing. */
final class Wake {
    private Wake() {
    }

    static void run(List<Object> items) {
        g2L project = null;
        try {
            long started = System.nanoTime();
            Map<xJo, xJo> pairs = new LinkedHashMap<>();
            for (Object item : items) {
                if (!(item instanceof xJo track)) {
                    Tracks.note("Wake Track works on tracks. Nothing was changed.");
                    return;
                }
                project = track.cc_();
                Pairing.Pair pair = Pairing.find(track);
                if (pair.problem() != null) {
                    Tracks.note(pair.problem() + " Nothing was changed.");
                    return;
                }
                pairs.put(pair.source(), pair.copy());
            }
            if (pairs.isEmpty()) {
                Tracks.note("Select a hibernated track to wake.");
                return;
            }
            if (project.oQj() != null) { // a bounce is running
                Tracks.note("Audio is being rendered. Nothing was changed.");
                return;
            }
            if (project.TD2()) { // recording
                Tracks.note("Can't wake a track while recording. Nothing was changed.");
                return;
            }
            apply(project, pairs, started);
        } catch (Throwable failure) {
            Tracks.MOD.log("wake failed", failure);
            Tracks.note("Wake Track failed: " + failure);
        }
    }

    private static void apply(g2L project, Map<xJo, xJo> pairs, long started) {
        boolean began = false;
        boolean elsewhere = false;
        int stuck = 0;
        List<String> sends = new ArrayList<>();
        List<String> done = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        try {
            project.cfM("Wake Track", false); // open one undo step
            began = true;
            for (Map.Entry<xJo, xJo> pair : pairs.entrySet()) {
                xJo source = pair.getKey();
                xJo copy = pair.getValue();
                Pieces.Mark render = Pieces.mark(copy);
                String rebuilt = render == null ? "" : ", " + Pieces.rebuild(project, source, copy, render, notes);
                sends.addAll(Mixer.copy(copy, source)); // mixer changes made while hibernated carry back
                stuck += Receivers.move(project, copy, source);
                source.qoU(true); // Bitwig's Activate
                ruS group = copy.zjb(); // the group holding the copy
                if (source.zjb() == group) {
                    group.MRY((zwV) source); // out of the group's track order; the group still owns it
                    group.cfM((zwV) source, group.cfM((WSA) copy)); // back in, right where the copy is
                } else {
                    elsewhere = true; // the copy was moved to another group: the source stays in its own
                }
                List<zwV> doomed = new ArrayList<>();
                doomed.add(copy);
                HibernateHooks.OWN_DELETE.set(true);
                try {
                    zTW.cfM(doomed, project); // Bitwig's delete tracks
                } finally {
                    HibernateHooks.OWN_DELETE.remove();
                }
                done.add("\"" + source.cb_() + "\"" + rebuilt);
            }
            project.cfM(false); // close the undo step
            began = false;
        } catch (Throwable failure) {
            if (began) {
                project.cfM(false);
                project.Mb2(); // undo what was done
            }
            throw failure;
        }
        Hide.afterWake(project);
        Tracks.MOD.log("woke in " + (System.nanoTime() - started) / 1_000_000 + " ms: " + String.join("; ", done));
        for (String note : notes) Tracks.note(note);
        if (elsewhere) Tracks.note("The copy had moved to another group, so the woken track stayed in its own group.");
        Tracks.noteMissingSends(sends);
        Tracks.noteStuck(stuck, "listening inside the hibernated track lost its source.");
    }
}
