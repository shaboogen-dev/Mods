package com.bitwig.mods.hibernatetracks;

import bitwig.obf.Jsq; // an arranger clip
import bitwig.obf.RN1; // sample formats
import bitwig.obf.Yhp; // bounce options
import bitwig.obf.gx3; // bounce points
import bitwig.obf.xJo; // a track
import com.bitwig.flt.document.core.master.g2L; // a project

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Hibernate Track: checks everything first, then starts one render for all selected tracks. */
final class Hibernate {
    /** How far past the last clip the render goes, in beats: eight bars of 4/4. */
    private static final double TAIL_BEATS = 32.0;

    private Hibernate() {
    }

    static void run(List<Object> items) {
        try {
            if (items.isEmpty()) {
                Tracks.note("Select a track to hibernate.");
                return;
            }
            g2L project = null;
            for (Object item : items) if (item instanceof xJo track) project = track.cc_(); // its project
            Map<xJo, double[]> ranges = new LinkedHashMap<>();
            for (Object item : items) {
                String refusal = Tracks.refusal(item);
                if (refusal == null) refusal = checkTrack((xJo) item, ranges);
                if (refusal != null) {
                    Tracks.note(refusal + " Nothing was changed.");
                    return;
                }
            }
            String unavailable = project.FXc(); // why Bitwig can't bounce now: recording, already bouncing, no engine
            if (unavailable != null) {
                Tracks.note(unavailable + ". Nothing was changed.");
                return;
            }
            Yhp options = Yhp.cfM(gx3.MRY); // after the effects, before the fader
            options.ERO().cfM(RN1.Hnt); // 32-bit float
            new HibernateTask(project, ranges).MRY(options); // start
        } catch (Throwable failure) {
            Tracks.MOD.log("could not start the render", failure);
            Tracks.note("Hibernate Track could not start: " + failure);
        }
    }

    private static String checkTrack(xJo track, Map<xJo, double[]> ranges) {
        String name = "\"" + track.cb_() + "\"";
        if (Pairing.sourceId(track) != null) return name + " is already a hibernated copy.";
        if (!track.jhH()) return name + " is deactivated.";
        if (Mixer.hasAutomation(track)) return name + " has automation on its fader, pan, mute or a send, which Hibernate can't carry over yet.";
        double start = Double.POSITIVE_INFINITY;
        double end = Double.NEGATIVE_INFINITY;
        for (Object event : track.HVx().iAj().cfM()) { // the track's arranger events
            if (event instanceof Jsq clip) {
                start = Math.min(start, clip.x3t()); // clip start
                end = Math.max(end, clip.QWG()); // clip end
            }
        }
        if (!(end > start)) return name + " has no clips in the arranger.";
        ranges.put(track, new double[]{start, end + TAIL_BEATS});
        return null;
    }
}
