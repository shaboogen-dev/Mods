package com.bitwig.mods.stepsequencer;

import bitwig.obf.KdW; // editor layer
import bitwig.obf.VJL; // native color
import bitwig.obf.WGw; // track editor layer
import bitwig.obf.XCq; // named drum pad
import bitwig.obf.bjf; // Detail Editor controller
import bitwig.obf.boJ; // a clip's note editor layer
import bitwig.obf.jkg; // note drawing state
import bitwig.obf.ooB; // a clip's note lane for one pitch and channel
import bitwig.obf.wdk; // track
import bitwig.obf.wwN; // MIDI clip
import bitwig.obf.ycA; // track's note and drum-pad model
import bml.pianoroll.Clip;
import bml.pianoroll.PianoRoll;
import com.bitwig.base.sound.BkR; // pitch names used in the native note rows
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

record EditorClip(Clip clip, StepGrid.Range range, int channel, double velocity, List<Row> rows, VJL color) {
    private static final List<Row> CHROMATIC = chromaticRows();

    record Row(int key, String name) {}

    static EditorClip read(bjf editor) {
        boJ lane = clipLane(editor);
        if (lane == null || !(lane.mV_() instanceof wwN nativeClip) || !nativeClip.Fho()) {
            return null;
        }
        Clip clip = PianoRoll.clip(nativeClip);
        double start = nativeClip.dC_();
        double end = nativeClip.Gtj();
        if (nativeClip.Ptj()) {
            end = Math.max(end, nativeClip.Cvo().rqH());
        }
        if (clip == null || !Double.isFinite(start) || !Double.isFinite(end) || end <= start) {
            return null;
        }
        jkg drawing = lane.IHn();
        double velocity = drawing == null ? 0.0 : drawing.SkU();
        return new EditorClip(clip, new StepGrid.Range(start, end), lane.mb_(),
                velocity > 0 ? velocity : 100.0 / 127.0, rows(lane, nativeClip), nativeClip.znu());
    }

    private static boJ clipLane(bjf editor) {
        if (editor.Ptj() == null) {
            return null;
        }
        if (editor.Gtj() instanceof boJ focused) {
            return focused;
        }
        boJ onlyClip = null;
        for (Object layer : editor.QNK()) {
            if (!(layer instanceof boJ clip)) {
                continue;
            }
            if (onlyClip != null && onlyClip.mV_() != clip.mV_()) {
                return null;
            }
            onlyClip = clip;
        }
        return onlyClip;
    }

    private static List<Row> rows(boJ lane, wwN clip) {
        KdW layer = lane.wvR();
        KdW trackLayer = layer == null ? null : layer.Xzi();
        wdk track = trackLayer instanceof WGw nativeTrack ? nativeTrack.gj_()
                : clip.pS() instanceof wdk clipTrack ? clipTrack : null;
        if (track == null || !(track.Jq() instanceof ycA pads)) {
            return CHROMATIC;
        }
        TreeMap<Integer, Row> named = new TreeMap<>();
        for (Object item : pads.BXY()) {
            if (item instanceof XCq pad && pad.THY() >= 0 && pad.THY() < 128) {
                String pitch = BkR.vhT(pad.THY(), true);
                String name = pad.vhT();
                named.put(pad.THY(), new Row(pad.THY(), name == null || name.isBlank() || name.equals(pitch)
                        ? pitch : pitch + "  " + name));
            }
        }
        if (named.isEmpty()) {
            return CHROMATIC;
        }
        for (Object item : clip.A1().JJF().A1()) {
            if (item instanceof ooB noteLane && !noteLane.vhT().isEmpty()) {
                int key = noteLane.THY();
                named.putIfAbsent(key, new Row(key, BkR.vhT(key, true)));
            }
        }
        return List.copyOf(named.descendingMap().values());
    }

    boolean chromatic() {
        return rows == CHROMATIC;
    }

    int playbackColumn(bjf editor, int page, long frameTime) {
        if (!editor.Msw().RGN()) {
            return -1;
        }
        // The clip's native playback provider reports content time, including launcher and loop playback.
        wwN nativeClip = (wwN) clip.bitwigClip();
        double beat = nativeClip.E3I(frameTime);
        return beat == -1.0 ? -1 : range.playbackColumn(page, beat);
    }

    List<StepGrid.Event<StepNote>> notes(double start, double end) {
        return clip.notesBetween(start, end).stream()
                .map(note -> new StepGrid.Event<>(StepNote.read(note), note.key(), note.channel(), note.start(), note.end(), note.velocity()))
                .toList();
    }

    private static List<Row> chromaticRows() {
        List<Row> rows = new ArrayList<>(128);
        for (int key = 127; key >= 0; key--) {
            rows.add(new Row(key, BkR.vhT(key, true)));
        }
        return List.copyOf(rows);
    }
}
