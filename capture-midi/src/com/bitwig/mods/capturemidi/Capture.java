package com.bitwig.mods.capturemidi;

import bitwig.obf.FOn; // clipboard content holding clips
import bitwig.obf.JCt; // where a new track goes; cfM: among the main tracks
import bitwig.obf.PSn; // a track's arranger clip lane, which can list its clips in a time range
import bitwig.obf.QSF; // a project's transport
import bitwig.obf.Us; // a clip launcher slot
import bitwig.obf.dp3; // the arranger loop region
import bitwig.obf.udj; // a project's arranger timeline (holds the loop region)
import bitwig.obf.TGq; // a clip launcher slot's document object (knows its project)
import bitwig.obf.XuI; // paste mode
import bitwig.obf.g1d; // a note clip's content, built note by note
import bitwig.obf.nBn; // a clip, as clipboard content holds it
import bitwig.obf.pHE; // paste kinds; MRY is a plain paste
import bitwig.obf.qIC; // clipboard content
import bitwig.obf.uGG; // where a paste goes (an insertion point)
import bitwig.obf.vFg; // an arranger clip, with its start and end
import com.bitwig.flt.app.CHR; // the application (shows notifications, knows the active window)
import com.bitwig.flt.document.core.iface.WSA; // a track
import com.bitwig.flt.document.core.iface.clipboard.clip.ArrangerClipInsertionPoint;
import com.bitwig.flt.document.core.iface.clipboard.clip.LauncherClipInsertionPoint;
import com.bitwig.flt.document.core.iface.clipboard.track.ArrangerTrackInsertionPoint;
import com.bitwig.flt.document.core.iface.kWY; // a project's top group of tracks
import com.bitwig.flt.document.core.iface.pHd; // a project document
import com.bitwig.flt.document.core.master.g2L; // a project
import com.bitwig.flt.document.core.master.zwV; // a track

import java.util.List;

/**
 * Capture MIDI: what was just played becomes a note clip, through Bitwig's own paste, so it is one
 * Undo step like a paste. The clip goes into the clicked launcher slot (from the slot's menu), or
 * into the arranger at the play position on the selected track; on a new track when the selected
 * track can't take it or already has a clip there.
 */
final class Capture {
    private Capture() {
    }

    /** Runs on Bitwig's UI thread (an action). {@code slot}: the clicked empty launcher slot, or null. */
    static void run(Object slot) {
        try {
            long now = System.nanoTime();
            pHd document = CHR.Grk().E1b().ejd().hUR(); // the active window's project panel's document
            if (document == null) {
                notify("Capture MIDI: no project");
                return;
            }
            QSF transport = document.ifN(); // the transport
            double playhead = transport.wYK().getDoubleValue(); // the play position, in beats
            boolean playing = transport.luv().getBoolValue(); // is it playing
            double bpm = tempo(transport, playhead);
            double bar = barLength(transport);

            NoteBuffer.Snapshot snapshot = NoteBuffer.snapshot(now - Options.keepSeconds() * 1_000_000_000L);
            List<CapturePlan.Note> played = CapturePlan.notes(snapshot, now);
            double[] loop = playing ? loopSeconds(transport, playhead) : null;
            List<CapturePlan.Note> phrase = loop != null
                    ? CapturePlan.lastLoopPass(played, now, transport.IwV(playhead), loop[0], loop[1])
                    : CapturePlan.lastPhrase(played, Options.pauseSeconds() * 1_000_000_000L);
            CapturePlan.Clip clip = CapturePlan.place(phrase, now, bpm, bar, playing, playhead, beatAgo(transport, playhead, bpm));
            if (clip == null) {
                notify("Capture MIDI: nothing played since the last capture");
                return;
            }
            String where = slot != null ? toSlot(slot, clip) : toArranger(document, clip, bar);
            if (where == null) {
                notify("Capture MIDI: Bitwig refused the clip");
                return;
            }
            NoteBuffer.consume(snapshot.end());
            notify("Captured " + clip.notes().size() + (clip.notes().size() == 1 ? " note" : " notes") + where);
        } catch (Throwable t) {
            Options.MOD.log("capture failed", t);
            notify("Capture MIDI failed (see the log)");
        }
    }

    /**
     * The arranger loop's start and end in song seconds, when the loop is on and the play position is
     * inside it (so playback is going round it); otherwise null. The loop region is the timeline's
     * (IGd.jG(), udj) loop event (udj.luv(), dp3): dp3.cfM() whether the loop is on, x3t() / QWG() its
     * start and end in beats, as Bitwig's transport reads them before looping.
     */
    private static double[] loopSeconds(QSF transport, double playhead) {
        try {
            if (!(transport.jG() instanceof udj timeline)) return null;
            dp3 loop = timeline.luv(); // the arranger loop region
            if (loop == null || !loop.cfM()) return null; // off
            double start = loop.x3t(), end = loop.QWG(); // beats
            if (!(end > start) || playhead < start || playhead >= end) return null;
            double startSeconds = transport.IwV(start), endSeconds = transport.IwV(end); // beats to seconds
            if (!Double.isFinite(startSeconds) || !(endSeconds > startSeconds)) return null;
            return new double[] {startSeconds, endSeconds};
        } catch (Throwable t) {
            Options.MOD.log("could not read the arranger loop; capturing as without a loop", t);
            return null;
        }
    }

    /** The tempo at the play position, from the transport's beats-to-seconds mapping. */
    private static double tempo(QSF transport, double beat) {
        double step = 1.0 / 64;
        double seconds = transport.IwV(beat + step) - transport.IwV(beat); // beats to seconds
        double bpm = 60.0 * step / seconds;
        return Double.isFinite(bpm) && bpm >= 1 && bpm <= 1000 ? bpm : 120;
    }

    /**
     * The song beat that played a number of seconds before now, through the project's tempo map
     * (QSF.IwV: beats to seconds; QSF.FzB: seconds to beats, its inverse), so tempo changes during
     * the phrase are followed. Before the song's start, and if the map gives no number, the tempo at
     * the play position is used.
     */
    private static java.util.function.DoubleUnaryOperator beatAgo(QSF transport, double playhead, double bpm) {
        double nowSeconds = transport.IwV(playhead); // the play position in song seconds
        return secondsAgo -> {
            double constant = playhead - secondsAgo * bpm / 60;
            double at = nowSeconds - secondsAgo;
            if (!Double.isFinite(nowSeconds) || at < 0) return constant;
            double beat = transport.FzB(at); // seconds to beats
            return Double.isFinite(beat) ? beat : constant;
        };
    }

    /** A bar's length in beats (quarter notes), from the project's time signature. */
    private static double barLength(QSF transport) {
        try {
            int numerator = transport.uva().Grk().getIntegerValue(); // time signature numerator
            int denominator = transport.uva().iCS().getIntegerValue(); // and denominator
            double bar = numerator * 4.0 / denominator;
            if (Double.isFinite(bar) && bar > 0 && bar <= 64) return bar;
        } catch (Throwable t) {
            Options.MOD.log("time signature unreadable; using 4/4", t);
        }
        return 4;
    }

    /** The clip as clipboard content: a note clip of the clip's length holding its notes. */
    private static qIC content(CapturePlan.Clip clip) {
        g1d notes = g1d.cfM(0.0, clip.length()); // note content from 0, clip.length() beats long
        for (CapturePlan.Placed n : clip.notes()) {
            notes.MRY(n.channel(), n.pitch(), n.start(), n.length(), Math.max(1 / 127.0, Math.min(1, n.velocity()))); // add a note
        }
        return new FOn(nBn.cfM(notes)); // a clip of that content, as clipboard content
    }

    /** Pastes into the clicked launcher slot, as dropping a clip there does. */
    private static String toSlot(Object slot, CapturePlan.Clip clip) {
        if (!(slot instanceof Us target) || !(slot instanceof TGq own)) return null;
        g2L project = own.abo(); // the slot's project
        uGG point = new LauncherClipInsertionPoint(target, project.EV().qFI()); // the slot, in the launcher's paste context
        return paste(point, content(clip)) ? " into the launcher slot" : null;
    }

    /** Pastes onto the selected track at the clip's beat, or onto a new track. */
    private static String toArranger(pHd document, CapturePlan.Clip clip, double barLength) {
        qIC content = content(clip);
        WSA selected = document.wIw().E3().tp2(); // the cursor track
        if (selected instanceof zwV track && free(track, clip)) {
            // As Bitwig's drop onto a track's arranger lane builds it.
            uGG point = new ArrangerClipInsertionPoint(track.JJY().HVx(), clip.start(), track.cc_().EV().iAj(), null);
            if (paste(point, content)) return " on the selected track at bar " + bar(clip, barLength);
        }
        kWY root = document.emh(); // the project's tracks
        uGG point = new ArrangerTrackInsertionPoint(clip.start(), root, JCt.cfM, root.ugU(), null, null); // a new track, where Bitwig would insert one
        return paste(point, content(clip)) ? " on a new track at bar " + bar(clip, barLength) : null;
    }

    /** Whether the track's arranger lane has no clip in the clip's time span (a paste would replace it). */
    private static boolean free(zwV track, CapturePlan.Clip clip) {
        try {
            double start = clip.start(), end = clip.start() + clip.length();
            if (!(track.JJY().HVx().E3() instanceof PSn lane)) return false; // its clips
            for (Object c : lane.Hnt(start, end)) { // clips in or touching the span
                if (!(c instanceof vFg placed)) return false;
                if (placed.x3t() < end && placed.QWG() > start) return false; // start, end
            }
            return true;
        } catch (Throwable t) {
            Options.MOD.log("could not read the selected track's clips; using a new track", t);
            return false;
        }
    }

    /** Bitwig's own paste, as its Paste action runs it: one Undo step. */
    private static boolean paste(uGG point, qIC content) {
        if (!point.hUR(content)) return false; // can it take this content
        XuI mode = pHE.MRY.IwV(); // a plain paste
        return point.cfM(content, mode, null);
    }

    private static String bar(CapturePlan.Clip clip, double barLength) {
        return String.valueOf((long) Math.floor(clip.start() / barLength + 1e-9) + 1);
    }

    private static void notify(String text) {
        try {
            CHR.Grk().hUR(text); // Bitwig's on-screen notification
        } catch (Throwable ignored) {
            // no window to show it in
        }
    }
}
