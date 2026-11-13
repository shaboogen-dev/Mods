package com.bitwig.mods.stemexportbusfx;

import bitwig.obf.Jmw; // a track's mixer section (volume, pan, mute, solo)
import bitwig.obf.Yhp; // the render settings an export task runs with
import bitwig.obf.gx3; // where a render taps a track: before the effects, before the fader, after it ...
import bitwig.obf.xJo; // a track in the project (audio, instrument, hybrid, group, effect, master)
import com.bitwig.flt.document.core.master.g2L; // the project document
import com.bitwig.ramona.core.Rzi; // the document's host: runs work later on the document thread
import com.bitwig.ramona.core.XKG; // a document context, entered while that work runs

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One export with the option on: the stems rendered one after another.
 *
 * Each pass solos one stem's track (nothing else) and renders the master output, after
 * the master fader, to that stem's file. A Master entry in the export renders with nothing
 * soloed: the full mix. When the queue ends (done, cancelled or failed) every track's solo
 * and the project's Solo as Cue are put back as they were. Solo isn't an Undo step in
 * Bitwig, so the passes leave nothing in the Undo history; mutes, faders, sends, routing,
 * automation and devices are never touched.
 *
 * Everything here runs on the document thread: the first pass from the export call itself,
 * later passes from the host's queue, inside the document's context.
 */
final class StemQueue {
    private static volatile StemQueue active;

    final g2L document;
    final List<xJo> tracks;
    final String[] paths;
    final boolean wave;
    final double from;
    final double to;
    final xJo master;

    private final Rzi host;
    private final XKG context;
    private final Map<xJo, Boolean> solos = new LinkedHashMap<>();
    private final boolean soloAsCue;
    private Yhp settings;
    private int index;
    private boolean cancelled;
    private boolean finished;
    private boolean restored;

    private StemQueue(StemExportBusFx.Request request, List<xJo> tracks, xJo master) {
        this.document = request.document();
        this.tracks = tracks;
        this.paths = request.paths().clone();
        this.wave = request.wave();
        this.from = request.from();
        this.to = request.to();
        this.master = master;
        this.host = document.ULc(); // the document's host
        this.context = host.vpw(); // the context the document's work runs in
        for (Object item : document.spd().BBT()) { // every track in the project, groups' contents included
            if (item instanceof xJo track) {
                solos.put(track, mixer(track).LaE()); // its solo state
            }
        }
        solos.putIfAbsent(master, mixer(master).LaE());
        this.soloAsCue = document.oPz(); // the project's Solo as Cue switch
    }

    /**
     * The queue for this request, or null when Bitwig's own export does the same thing
     * (only the master, nothing selected) or when the request can't be split per stem.
     */
    static StemQueue plan(StemExportBusFx.Request request) {
        if (active != null) {
            StemExportBusFx.MOD.log("a stem export is already running; exporting as Bitwig does");
            return null;
        }
        List<?> items = request.tracks();
        String[] paths = request.paths();
        if (items == null || paths == null || items.isEmpty() || paths.length != items.size()) {
            return null;
        }
        Object masterChannel = request.document().VpW().TD2(); // the master track
        if (!(masterChannel instanceof xJo master)) {
            return null;
        }
        List<xJo> tracks = new ArrayList<>(items.size());
        boolean anyStem = false;
        for (Object item : items) {
            if (!(item instanceof xJo track)) {
                return null;
            }
            tracks.add(track);
            anyStem |= track != master;
        }
        if (!anyStem) { // only the master: the same as Bitwig's export
            return null;
        }
        StemQueue queue = new StemQueue(request, List.copyOf(tracks), master);
        for (xJo track : tracks) {
            if (!queue.solos.containsKey(track)) {
                StemExportBusFx.MOD.log("a track to export isn't in the project's track list; exporting as Bitwig does");
                return null;
            }
        }
        return queue;
    }

    int count() {
        return tracks.size();
    }

    boolean isLast(int pass) {
        return pass + 1 == tracks.size();
    }

    /** Starts the first pass. Called from the document's export call. */
    void start(Yhp exportSettings) {
        active = this;
        settings = exportSettings;
        StemExportBusFx.MOD.log("exporting " + tracks.size() + " stems with group, return and master effects");
        try {
            runPass(0);
        } catch (Throwable t) {
            stop("could not start the stem export", t);
        }
    }

    /** The user cancelled the running pass. */
    void cancel() {
        cancelled = true;
    }

    /**
     * A pass finished (its task already did Bitwig's own completion). True when the next
     * pass should run; false when the queue has ended and solos must be restored.
     */
    boolean passDone(int pass, boolean success) {
        if (finished || pass != index) {
            return false;
        }
        if (!success || cancelled || isLast(pass)) {
            finished = true;
            if (!success && !cancelled) {
                StemExportBusFx.MOD.log("stem " + (pass + 1) + " failed; stopped");
            }
            return false;
        }
        index++;
        return true;
    }

    /**
     * Starts the next pass on the host's queue, once the finished task has unwound
     * (Bitwig clears the document's current task as the completion's last step).
     */
    void scheduleNext() {
        host.asyncExec(() -> {
            host.cfM(context); // enter the document's context
            try {
                runPass(index);
            } catch (Throwable t) {
                stop("the stem export stopped", t);
            } finally {
                host.ERO(); // leave it
            }
        });
    }

    private void runPass(int pass) {
        isolate(pass);
        settings.MRY(gx3.Hnt); // render the master after its fader, whatever Pre-fader says
        new StemPassTask(this, pass).MRY(settings); // start Bitwig's export task for this stem
    }

    /** Solos only this pass's track (none for the master) and turns Solo as Cue off. */
    private void isolate(int pass) {
        List<?> current = document.spd().BBT();
        xJo track = tracks.get(pass);
        if (track != master && !current.contains(track)) {
            throw new IllegalStateException("a track was removed during the export");
        }
        if (document.oPz()) {
            document.ai3(false); // Solo as Cue off, or solo wouldn't silence the other tracks on the master
        }
        for (xJo each : solos.keySet()) {
            if (each == master || current.contains(each)) {
                mixer(each).MRY(false); // unsolo
            }
        }
        if (track != master) {
            mixer(track).MRY(true); // solo
        }
    }

    /** Puts every solo and Solo as Cue back. Safe to call more than once. */
    void restore() {
        if (restored) {
            return;
        }
        restored = true;
        finished = true;
        if (active == this) {
            active = null;
        }
        List<?> current;
        try {
            current = document.spd().BBT();
        } catch (Throwable t) {
            StemExportBusFx.MOD.log("could not restore the solos: the project is gone", t);
            return;
        }
        for (Map.Entry<xJo, Boolean> entry : solos.entrySet()) {
            xJo track = entry.getKey();
            if (track != master && !current.contains(track)) {
                continue; // removed meanwhile
            }
            try {
                mixer(track).MRY(entry.getValue());
            } catch (Throwable t) {
                StemExportBusFx.MOD.log("could not restore a track's solo", t);
            }
        }
        try {
            if (document.oPz() != soloAsCue) {
                document.ai3(soloAsCue);
            }
        } catch (Throwable t) {
            StemExportBusFx.MOD.log("could not restore Solo as Cue", t);
        }
    }

    private void stop(String what, Throwable t) {
        StemExportBusFx.MOD.log(what, t);
        cancelled = true;
        finished = true;
        restore();
        try {
            document.Grk("Stem export stopped: " + (t.getMessage() == null ? what : t.getMessage())); // Bitwig's export error message
        } catch (Throwable ignored) {
            // the message is a courtesy
        }
    }

    private static Jmw mixer(xJo track) {
        return track.MIO(); // the track's mixer section
    }
}
