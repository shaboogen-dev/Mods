package com.bitwig.mods.audioanalysis;

import bitwig.obf.FOn; // clipboard content: clips
import bitwig.obf.GfJ; // clipboard content: audio files, as dropped from the browser
import bitwig.obf.ICh; // a view context (none needed here)
import bitwig.obf.JCt; // a track list position: the neighbouring track
import bitwig.obf.OjS; // a display context for an insertion (none needed here)
import bitwig.obf.QkI; // a lane for an insertion (none needed here)
import bitwig.obf.XuI; // how pasted content will be inserted
import bitwig.obf.g1d; // builds note content
import bitwig.obf.j2e; // a prepared insertion
import bitwig.obf.nBn; // a clip made from content
import bitwig.obf.t3q; // a timeline object with editable content (name, colour)
import bitwig.obf.uGG; // where pasted content goes
import com.bitwig.base.async.oRV; // a result listener (none needed here)
import com.bitwig.base.io.z2y; // a file, as clipboard content holds it
import com.bitwig.flt.document.core.iface.clipboard.track.ArrangerTrackInsertionPoint; // new tracks at a time in the arranger
import com.bitwig.flt.document.core.iface.clipboard.track.TrackInsertionPoint; // a place in the track list
import com.bitwig.flt.document.core.iface.kWY; // a track group (the list a track is in)
import com.bitwig.flt.document.core.master.IOJ; // an undoable change
import com.bitwig.flt.document.core.master.zwV; // a track
import com.bitwig.ramona.core.oDc; // a document object that undoable changes belong to

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The project changes the audio tools make, each through Bitwig's own code and each one Undo step:
 * new tracks from audio files (Bitwig's drop of files on the arranger), a new instrument track with
 * one MIDI clip (Bitwig's paste of a clip), and a clip rename. Call on Bitwig's UI thread.
 */
public final class Insert {
    /** One note: start and length in beats from the clip's start, key 0-127, velocity 0-1. */
    public record Note(double start, double length, int key, double velocity) { }

    private static final double MIN_LENGTH = 1.0 / 960.0;

    private Insert() { }

    /**
     * New audio tracks below the clip's track, one per file, each starting where the clip starts;
     * Bitwig's insertion of dropped files, so it is one Undo step. Returns false when nothing was
     * inserted.
     */
    public static boolean audioTracks(AudioSelection at, List<Path> files) {
        if (at == null || files == null || files.isEmpty()) return false;
        List<z2y> content = new ArrayList<>();
        for (Path p : files) {
            File f = p.toFile();
            if (!f.isFile()) throw new IllegalStateException("missing " + f);
            content.add(new z2y(f)); // a clipboard file
        }
        GfJ payload = new GfJ(content); // files, as a drop from the browser carries them
        ArrangerTrackInsertionPoint point = trackPoint(at, 1);
        XuI how = ((uGG) point).UNj(payload); // how the files will go in (declared on uGG, for every version)
        j2e insertion = ((uGG) point).cfM(payload, how); // prepare the insertion
        if (insertion == null) throw new IllegalStateException("Bitwig refused to insert the files");
        insertion.MRY((oRV) null); // insert
        return true;
    }

    /**
     * A new instrument track next to the clip's track with one clip from the clip's start, as long
     * as the clip, holding {@code notes}; Bitwig's paste of a clip, so it is one Undo step.
     */
    public static boolean midiTrack(AudioSelection at, List<Note> notes) {
        if (at == null || notes == null || notes.isEmpty()) return false;
        double length = Math.max(MIN_LENGTH, at.lengthBeats());
        g1d builder = g1d.cfM(0.0, length); // note content from 0 to the clip's length
        int count = 0;
        for (Note n : notes) {
            double start = Math.max(0.0, n.start());
            double len = Math.min(n.length(), length - start);
            if (!(len > 0) || !Double.isFinite(start) || n.key() < 0 || n.key() > 127) continue;
            builder.MRY(0, n.key(), start, Math.max(MIN_LENGTH, len), clamp(n.velocity())); // channel, key, start, length, velocity
            count++;
        }
        if (count == 0) return false;
        nBn clip = nBn.cfM(builder); // a clip from that content
        FOn payload = new FOn(clip); // clipboard content: that clip
        ArrangerTrackInsertionPoint point = trackPoint(at, 0);
        XuI how = ((uGG) point).UNj(payload); // how the clip will go in
        j2e insertion = payload.cfM(point, how); // prepare: a new track for the clip
        if (insertion == null) throw new IllegalStateException("Bitwig refused to insert the clip");
        insertion.MRY((oRV) null); // insert
        return true;
    }

    /** Renames the clip, as one Undo step labelled {@code undoLabel}. */
    public static boolean rename(AudioSelection at, String newName, String undoLabel) {
        if (at == null || newName == null || newName.isBlank()) return false;
        if (!(at.named() instanceof t3q timelineObject) || timelineObject.gCt() == null) return false;
        if (!(at.named() instanceof oDc owner)) return false;
        IOJ change = new IOJ();
        if (!change.cfM(owner, undoLabel)) return false; // begin the Undo step
        try {
            timelineObject.gCt().cfM(newName); // set the name
            return true;
        } finally {
            change.MRY(); // finish the Undo step
        }
    }

    /**
     * Bitwig's insertion point for new tracks at the clip's start: in the clip's track list, at the
     * clip's track ({@code offset} 0: before it) or after it ({@code offset} 1).
     */
    private static ArrangerTrackInsertionPoint trackPoint(AudioSelection at, int offset) {
        if (!(at.track() instanceof zwV track)) throw new IllegalStateException("no track");
        uGG place = track.cfM((ICh) null); // the track's place in the track list
        if (!(place instanceof TrackInsertionPoint header)) throw new IllegalStateException("the track's place is unknown");
        kWY parent = header.nVd(); // the list it is in
        JCt neighbour = header.ucQ(); // its neighbour there
        int index = header.qoU(); // its position
        if (parent == null || index < 0) throw new IllegalStateException("the track's place is unknown");
        return new ArrangerTrackInsertionPoint(at.startBeat(), parent, neighbour, index + offset, (QkI) null, (OjS) null);
    }

    private static double clamp(double v) {
        return Double.isFinite(v) ? Math.max(1.0 / 127.0, Math.min(1.0, v)) : 0.8;
    }
}
