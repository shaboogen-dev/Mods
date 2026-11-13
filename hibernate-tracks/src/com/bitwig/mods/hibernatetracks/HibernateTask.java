package com.bitwig.mods.hibernatetracks;

import bitwig.obf.B1W; // the project's time selection context a clip is placed with
import bitwig.obf.DLy; // Bitwig's bounce task
import bitwig.obf.MmS; // one arranger render of a track
import bitwig.obf.SNO; // what a render makes
import bitwig.obf.Yhp; // bounce options
import bitwig.obf.fP; // track types
import bitwig.obf.xJo; // a track
import com.bitwig.flt.document.core.iface.WSA; // a track, as the group lists it
import com.bitwig.flt.document.core.master.g2L; // a project
import com.bitwig.flt.document.core.master.ruS; // a group track
import com.bitwig.flt.document.core.master.zwV; // a channel

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders the tracks with Bitwig's bounce and changes the project only after the render has
 * succeeded, in one undo step: a failed or cancelled render leaves the project untouched.
 */
final class HibernateTask extends DLy {
    private final g2L project;
    private final List<xJo> sources = new ArrayList<>();
    private final long started = System.nanoTime();

    HibernateTask(g2L project, Map<xJo, double[]> ranges) {
        super(project);
        this.project = project;
        B1W context = project.EV().iAj(); // as Bitwig's own arranger bounces use
        ranges.forEach((track, range) -> {
            sources.add(track);
            cfM(new MmS(track, range[0], range[1], context)); // add a render to the task
        });
    }

    @Override
    public String cfM() { // the progress window's title
        return "Hibernate Track";
    }

    @Override
    public void cfM(Yhp options) { // the tracks the options apply to
        options.MRY(sources);
    }

    @Override
    public SNO MRY() { // make an audio clip for each render
        return SNO.Hnt;
    }

    @Override
    public void Hnt() { // the render has started: nothing changes yet
    }

    @Override
    public void cfM(boolean success, String message) { // the render ended and the engine is real time again
        super.cfM(success, message);
        if (!success) return;
        for (xJo source : sources) {
            if (source.zjb() == null) { // deleted while it rendered
                Tracks.note("A track was deleted while it rendered. Nothing was changed.");
                return;
            }
        }
        List<String> sends = new ArrayList<>();
        int stuck = 0;
        boolean began = false;
        try {
            project.cfM("Hibernate Track", false); // open one undo step
            began = true;
            for (Object job : TMF()) { // the renders
                MmS render = (MmS) job;
                xJo source = render.TMF();
                xJo copy = newTrack(source);
                List<double[]> clips = Pieces.ranges(Pieces.clips(source));
                render.cfM(copy); // put the rendered clip on it
                String mark = Pieces.cut(project, copy, render.iHb(), clips, render.wGc());
                copy.TtY(copy.AgD() + " " + mark);
                sends.addAll(Mixer.copy(source, copy));
                stuck += Receivers.move(project, source, copy);
                placeUnder(source, copy);
                source.qoU(false); // Bitwig's Deactivate
            }
            project.cfM(false); // close the undo step
            began = false;
        } catch (Throwable failure) {
            Tracks.MOD.log("hibernate failed", failure);
            if (began) {
                project.cfM(false);
                project.Mb2(); // undo what was done
            }
            Tracks.note("Hibernate Track failed, and nothing was changed: " + failure);
            return;
        }
        Hide.afterHibernate(project);
        Tracks.MOD.log("hibernated " + sources.size() + " tracks in " + (System.nanoTime() - started) / 1_000_000 + " ms");
        Tracks.noteMissingSends(sends);
        Tracks.noteStuck(stuck, "listening inside the track's device chain now hears nothing.");
    }

    /**
     * The source goes directly below its copy, in the same group, so the pair stays together when
     * deactivated tracks are shown or the project is opened without the mod. The copy was inserted
     * at the source's place, which already puts the source right after it; this only moves it if
     * that isn't so. One track at a time, so several hibernated together each sit under their own copy.
     */
    private static void placeUnder(xJo source, xJo copy) {
        ruS parent = copy.zjb();
        if (source.zjb() != parent) return; // never moves a source between groups
        if (parent.cfM((WSA) source) == parent.cfM((WSA) copy) + 1) return; // already right below
        parent.MRY((zwV) source); // out of the group's track order; the group still owns it
        parent.cfM((zwV) source, parent.cfM((WSA) copy) + 1); // back in, right below the copy (index read after removal)
    }

    /** A new audio track in the source's exact position. */
    private static xJo newTrack(xJo source) {
        ruS parent = source.zjb();
        int index = parent.cfM((WSA) source); // its position in the group
        xJo copy = parent.MRY(fP.MRY); // an audio track
        copy.cfM(source.PD3()); // colour
        String name = source.cb_();
        copy.d_(name.startsWith("^") ? name : "^" + name); // Bitwig's name for a bounced track
        copy.TtY(Pairing.comment(source));
        parent.cfM((zwV) copy, index); // zwV: the method for main tracks; the one taking xJo is for effect tracks
        return copy;
    }
}
