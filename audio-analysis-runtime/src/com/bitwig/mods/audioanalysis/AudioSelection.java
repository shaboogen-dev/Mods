package com.bitwig.mods.audioanalysis;

import bitwig.obf.TWN; // an audio event
import bitwig.obf.XIF; // a selection target that lists its selected objects
import bitwig.obf.big; // a view's selection
import bitwig.obf.la1; // an arranger clip (a container of events)
import bitwig.obf.ocL; // one region of an audio event, playing part of a sample file
import bitwig.obf.pBi; // a timeline event
import bitwig.obf.t3q; // a timeline object with editable content (name, colour)
import bitwig.obf.vFg; // something with a start and an end on the timeline
import bml.actions.ActionContext;
import com.bitwig.flt.document.core.master.qjn; // a sample file resource
import com.bitwig.flt.document.core.master.zwV; // a track

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

/**
 * The one audio clip a right-click menu was opened on: the audio event, its sample file, where it
 * sits in the arranger, its track, and the object whose name is the clip's name.
 *
 * @param source  the sample file the event plays (one file only)
 * @param startBeat where it starts in the arranger, in beats
 * @param endBeat where it ends in the arranger, in beats
 * @param event   Bitwig's audio event
 * @param named   the selected object (the arranger clip, or the event when it has none); renamed by Detect Key
 * @param track   the track it is on
 */
public record AudioSelection(File source, double startBeat, double endBeat, Object event, Object named, Object track) {

    /** Kinds of arranger selection whose right-click menu shows the audio tools. */
    public static final List<String> KINDS = List.of("arranger_clip", "arranger_audio_note");

    public double lengthBeats() {
        return endBeat - startBeat;
    }

    /** The clip's name as shown, or the file's name when it has none. */
    public String name() {
        String n = "";
        try {
            if (named instanceof t3q content && content.gCt() != null) n = content.gCt().ERO(); // its content's name
            if ((n == null || n.isBlank()) && named instanceof la1 clip) n = clip.H2K(); // the clip's shown title
        } catch (Throwable t) {
            AudioAnalysis.MOD.log("could not read the clip's name", t);
        }
        if (n == null || n.isBlank()) {
            String file = source.getName();
            int dot = file.lastIndexOf('.');
            n = dot > 0 ? file.substring(0, dot) : file;
        }
        return n;
    }

    /** Cheap test for a menu's visibleWhen: exactly one audio clip selected. No file access. */
    public static boolean isSingleAudio(ActionContext context) {
        try {
            Object item = single(context);
            return item != null && audioEvent(item) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The selected audio clip, or null (with the reason in the mod log) when the selection isn't
     * one audio clip playing one sample file. Call on Bitwig's UI thread.
     */
    public static AudioSelection of(ActionContext context) {
        try {
            Object item = single(context);
            if (item == null) return reject("not exactly one selected object");
            TWN event = audioEvent(item);
            if (event == null) return reject("not an audio clip: " + item.getClass().getName());
            if (!(item instanceof vFg interval)) return reject("no timeline position: " + item.getClass().getName());
            double start = interval.x3t(); // its start
            double end = interval.QWG(); // its end
            if (!Double.isFinite(start) || !Double.isFinite(end) || end <= start)
                return reject("bad position " + start + " .. " + end);
            File source = sourceFile(event);
            if (source == null) return null;
            zwV track = event.wIw(); // the track it is on
            if (track == null) return reject("the clip has no track");
            return new AudioSelection(source, start, end, event, item, track);
        } catch (Throwable t) {
            AudioAnalysis.MOD.log("could not read the selected audio clip", t);
            return null;
        }
    }

    private static Object single(ActionContext context) {
        if (context == null || !KINDS.contains(context.selectionKind())) return null;
        if (!(context.selection() instanceof big view)) return null;
        Object target = view.UNj().SQN(); // the selection's target: what is selected
        if (!(target instanceof XIF list)) return null;
        List<?> items = list.bJ_(); // the selected objects
        return items != null && items.size() == 1 ? items.get(0) : null;
    }

    /** The audio event of a selected audio event, or of an arranger clip holding one. */
    private static TWN audioEvent(Object item) {
        if (item instanceof TWN event) return event;
        if (item instanceof la1 clip) {
            pBi inner = clip.bB(); // the clip's event
            if (inner instanceof TWN event) return event;
        }
        return null;
    }

    private static File sourceFile(TWN event) throws IOException {
        File found = null;
        try (Stream<?> regions = event.cCk()) { // the event's regions
            for (var it = regions.iterator(); it.hasNext(); ) {
                Object o = it.next();
                if (!(o instanceof ocL region)) return rejectFile("a region isn't a sample region: " + o);
                qjn resource = region.Scv(); // its sample file
                File file = resource == null ? null : resource.LaE(); // the file on disk
                if (file == null || !file.isFile()) return rejectFile("sample file not found: " + file);
                File canonical = file.getCanonicalFile();
                if (found != null && !found.equals(canonical)) return rejectFile("the clip plays more than one sample file");
                found = canonical;
            }
        }
        if (found == null) return rejectFile("the clip has no sample file");
        return found;
    }

    private static AudioSelection reject(String why) {
        AudioAnalysis.MOD.log("audio clip not usable: " + why);
        return null;
    }

    private static File rejectFile(String why) {
        AudioAnalysis.MOD.log("audio clip not usable: " + why);
        return null;
    }
}
