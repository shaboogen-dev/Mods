package com.bitwig.mods.hibernatetracks;

import bitwig.obf.Jsq; // an arranger clip
import bitwig.obf.MOM; // copies an event
import bitwig.obf.Rke; // a layer of a clip's content
import bitwig.obf.TWN; // an audio clip in the arranger
import bitwig.obf.VDz; // a track's arranger lane
import bitwig.obf.YvI; // an event in a lane
import bitwig.obf.fP; // track types
import bitwig.obf.ocL; // an audio event inside an audio clip
import bitwig.obf.pBi; // a clip in the arranger
import bitwig.obf.xJo; // a track
import bitwig.obf.zZi; // a clip in a lane
import com.bitwig.flt.document.core.master.g2L; // a project
import com.bitwig.flt.document.core.master.ruS; // a group track
import com.bitwig.flt.document.core.master.zwV; // a channel
import com.bitwig.ramona.core.oDc; // a document object
import com.bitwig.ramona.core.zjj; // gives a copied object its own identity

import java.io.File;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The render, cut into one clip per original clip, and the originals rebuilt from what's on the
 * new track on Wake. A piece names its place in the render through its content offset: Bitwig
 * keeps that right when a clip is moved, split or trimmed. So original time = origin + offset.
 */
final class Pieces {
    private static final double EPSILON = 1e-6; // beats
    private static final double TAIL_MARGIN_SECONDS = 0.1;
    private static final Pattern MARK = Pattern.compile("\\s*\\[hibernate-tracks render=(\\S+) origin=(\\S+) start=(\\S+) length=(\\S+) slip=(\\S+)\\]");

    private Pieces() {
    }

    /** What a track's comment says about its render: the file, and how the render's audio event was when it was made. */
    record Mark(String file, double origin, double start, double length, double slip) {
        String text() {
            return "[hibernate-tracks render=" + URLEncoder.encode(file, StandardCharsets.UTF_8) + " origin=" + origin
                    + " start=" + start + " length=" + length + " slip=" + slip + "]";
        }
    }

    static Mark mark(xJo track) {
        String comment = track.AgD();
        if (comment == null) return null;
        Matcher found = MARK.matcher(comment);
        if (!found.find()) return null;
        return new Mark(URLDecoder.decode(found.group(1), StandardCharsets.UTF_8), Double.parseDouble(found.group(2)),
                Double.parseDouble(found.group(3)), Double.parseDouble(found.group(4)), Double.parseDouble(found.group(5)));
    }

    static String withoutMark(String comment) {
        return MARK.matcher(comment).replaceAll("").trim();
    }

    static List<zZi> clips(xJo track) {
        List<zZi> clips = new ArrayList<>();
        for (Object event : track.HVx().iAj().cfM()) clips.add((zZi) event); // the track's arranger events
        return clips;
    }

    private static List<ocL> events(TWN clip) {
        List<ocL> events = new ArrayList<>();
        for (Object layer : clip.z().FFu().wGc()) { // the clip's content
            for (Object event : ((Rke) layer).cfM()) {
                if (event instanceof ocL audio) events.add(audio);
            }
        }
        return events;
    }

    private static boolean isFrom(TWN clip, Mark mark) {
        for (ocL event : events(clip)) {
            if (mark.file().equals(event.Scv().Hnt())) return true; // Hnt: the sample's name, its file's name when it was made
        }
        return false;
    }

    /** True if the piece's place in the render can be read: one audio event, as made, and no loop wrapping round. */
    private static boolean mappable(TWN piece, Mark mark) {
        List<ocL> events = events(piece);
        if (events.size() != 1) return false;
        ocL event = events.get(0);
        if (Math.abs(event.x3t() - mark.start()) > EPSILON) return false; // moved inside the clip
        if (Math.abs(event.nfq() - mark.length()) > EPSILON) return false; // stretched
        if (Math.abs(event.Grk() - mark.slip()) > EPSILON) return false; // slipped
        if (event.nVd()) return false; // reversed
        return !(piece.sut() && piece.nfq() > piece.LIK().nfq() + EPSILON); // sut: loop on
    }

    /**
     * Cuts the render, already on the track, into a piece per original clip, each running to the
     * next clip's start or the end of its tail, or to its clip's end with tails off. Gaps are removed.
     */
    static String cut(g2L project, xJo track, TWN audio, List<double[]> clips, File wav) {
        List<ocL> events = events(audio);
        Mark mark = new Mark(events.get(0).Scv().Hnt(), audio.x3t() - audio.dC_(), events.get(0).x3t(), events.get(0).nfq(), events.get(0).Grk());
        double[] ends = pieceEnds(project, audio, clips, wav, mark.origin());
        VDz lane = track.HVx().iAj();
        Jsq rest = audio;
        for (int i = 0; i < clips.size(); i++) {
            if (i > 0 && rest != null) {
                Jsq next = split(project, lane, rest, clips.get(i)[0]);
                if (next != rest) lane.MRY((YvI) rest); // the gap before this clip
                rest = next;
            }
            if (rest != null && ends[i] < rest.QWG() - EPSILON) {
                rest = split(project, lane, rest, ends[i]);
                if (i == clips.size() - 1) lane.MRY((YvI) rest); // past the last tail
            } else if (i < clips.size() - 1) {
                rest = null; // the audio ends here
            }
        }
        return mark.text();
    }

    /** Bitwig's own split. Returns the part after the cut, or the clip itself if the cut isn't inside it. */
    private static Jsq split(g2L project, VDz lane, Jsq clip, double at) {
        if (at <= clip.x3t() + EPSILON || at >= clip.QWG() - EPSILON) return clip;
        Jsq[] right = new Jsq[1];
        lane.cfM(clip, at, project.EV().iAj(), (left, part) -> right[0] = (Jsq) part);
        if (right[0] == null) throw new IllegalStateException("Bitwig didn't split the render at " + at);
        return right[0];
    }

    /** Where each clip's piece ends. If the file can't be scanned, it runs to the next clip's start, or the render's end. */
    private static double[] pieceEnds(g2L project, TWN audio, List<double[]> clips, File wav, double origin) {
        double[] ends = new double[clips.size()];
        if (!HibernateSettings.preserveTails()) {
            for (int i = 0; i < ends.length; i++) ends[i] = clips.get(i)[1];
            return ends;
        }
        double renderEnd = audio.QWG();
        for (int i = 0; i < ends.length; i++) ends[i] = i + 1 < ends.length ? clips.get(i + 1)[0] : renderEnd;
        if (wav == null) return ends;
        double[] limits = ends.clone();
        try (TailScan scan = TailScan.open(wav, HibernateSettings.tailThresholdDb())) {
            if (scan == null) return ends;
            double beatsPerSecond = project.tHK().qm() / 60.0; // the tempo
            double framesPerBeat = scan.sampleRate / beatsPerSecond;
            double expected = (renderEnd - origin) * framesPerBeat;
            if (Math.abs(scan.frames - expected) > expected * 0.01) { // the tempo changes along the way, so beats and frames don't line up
                Tracks.MOD.log("tail not trimmed: the render has " + scan.frames + " frames, " + Math.round(expected) + " expected");
                return ends;
            }
            double margin = TAIL_MARGIN_SECONDS * beatsPerSecond;
            for (int i = 0; i < ends.length; i++) {
                double clipEnd = clips.get(i)[1];
                long loud = scan.lastLoud((long) ((clipEnd - origin) * framesPerBeat), (long) ((limits[i] - origin) * framesPerBeat));
                double tail = loud < 0 ? clipEnd : origin + loud / framesPerBeat + margin;
                ends[i] = Math.max(clipEnd, Math.min(limits[i], tail));
            }
        } catch (Exception failure) {
            Tracks.MOD.log("tail not trimmed: " + failure);
            return limits;
        }
        return ends;
    }

    /** The ranges of a track's clips, in order. */
    static List<double[]> ranges(List<zZi> clips) {
        List<double[]> ranges = new ArrayList<>();
        for (zZi clip : clips) ranges.add(new double[]{clip.x3t(), clip.QWG()});
        ranges.sort(Comparator.comparingDouble(range -> range[0]));
        return ranges;
    }

    private record Placement(zZi original, double from, double to, double moved) {
        double start() {
            return from + moved;
        }

        double end() {
            return to + moved;
        }
    }

    /**
     * Rebuilds the source's clips, in an undo step that's already open, from the pieces on the new
     * track. A clip goes with a piece if their spans overlap in original time, cut to that overlap,
     * so a tail alone brings nothing.
     */
    static String rebuild(g2L project, xJo source, xJo copy, Mark mark, List<String> notes) {
        VDz sourceLane = source.HVx().iAj();
        VDz copyLane = copy.HVx().iAj();
        List<zZi> originals = clips(source);
        List<TWN> pieces = new ArrayList<>();
        List<TWN> unmappable = new ArrayList<>();
        List<zZi> others = new ArrayList<>();
        for (zZi clip : clips(copy)) {
            if (clip instanceof TWN audio && isFrom(audio, mark)) {
                (mappable(audio, mark) ? pieces : unmappable).add(audio);
            } else {
                others.add(clip);
            }
        }
        List<Placement> placements = new ArrayList<>();
        Map<zZi, Integer> used = new IdentityHashMap<>();
        for (TWN piece : pieces) {
            double from = mark.origin() + piece.dC_();
            double to = from + piece.nfq();
            for (zZi original : originals) {
                double overlapFrom = Math.max(original.x3t(), from);
                double overlapTo = Math.min(original.QWG(), to);
                if (overlapTo - overlapFrom <= EPSILON) continue;
                placements.add(new Placement(original, overlapFrom, overlapTo, piece.x3t() - from));
                used.merge(original, 1, Integer::sum);
            }
        }
        int dropped = 0;
        for (zZi original : originals) {
            if (used.containsKey(original)) continue;
            if (unmappable.isEmpty()) {
                dropped++; // its piece was deleted
            } else {
                placements.add(new Placement(original, original.x3t(), original.QWG(), 0)); // never guess: where it started
                used.put(original, 1);
            }
        }
        placements.sort(Comparator.comparingDouble(Placement::start));

        // Copies first, while the original is whole. The first placement of each original is the clip itself.
        Map<zZi, Integer> taken = new IdentityHashMap<>();
        List<zZi> clips = new ArrayList<>();
        for (Placement placement : placements) {
            if (taken.merge(placement.original(), 1, Integer::sum) == 1) {
                clips.add(placement.original());
            } else {
                zZi clone = MOM.MRY().cfM(placement.original());
                zjj.cfM((oDc) clone);
                clone.wNO();
                clips.add(clone);
            }
        }
        for (zZi original : originals) {
            if (used.containsKey(original)) {
                sourceLane.cfM((YvI) original, false); // out of the lane, kept
            } else {
                sourceLane.MRY((YvI) original); // delete: not coming back
            }
        }
        Rooms rooms = new Rooms(source);
        Map<Object, Object> contents = new HashMap<>();
        for (int i = 0; i < placements.size(); i++) {
            Placement placement = placements.get(i);
            zZi clip = clips.get(i);
            pBi trimmed = (pBi) clip;
            if (placement.to() < clip.QWG() - EPSILON) {
                clip.eLY(placement.to());
                trimmed.MRY(sourceLane, project.EV().iAj());
            }
            if (placement.from() > clip.x3t() + EPSILON) {
                clip.cfM(placement.from(), true, project.EV().iAj()); // as a clip's start is trimmed
                trimmed.cfM(sourceLane, project.EV().iAj());
            }
            clip.wGc(placement.start());
            xJo destination = rooms.claim(placement.start(), placement.end());
            if (destination != source) clip.cfM(source.HVx(), destination.HVx(), null, true, contents, false); // its content follows
            destination.HVx().iAj().cfM((YvI) clip, project.EV().iAj());
        }
        for (zZi other : others) { // what the user added on the new track comes along
            double start = other.x3t();
            xJo destination = rooms.claim(start, other.QWG());
            copyLane.cfM((YvI) other, false);
            other.cfM(copy.HVx(), destination.HVx(), null, true, contents, false);
            destination.HVx().iAj().cfM((YvI) other, project.EV().iAj());
        }
        String name = "\"" + source.cb_() + "\"";
        if (!unmappable.isEmpty()) {
            notes.add(name + ": " + unmappable.size() + " rendered clips were stretched, looped, reversed or edited, so the clips they can't be matched to went back where they started.");
        }
        if (dropped > 0) {
            notes.add(name + ": " + dropped + " clips had no rendered clip left, so they didn't come back. Ctrl+Z recovers them.");
        }
        if (rooms.spares == 1) {
            notes.add(name + ": some clips would have overlapped, so they're on the deactivated track \"" + rooms.spare.cb_() + "\", where they'd have played.");
        } else if (rooms.spares > 1) {
            notes.add(name + ": some clips would have overlapped, so they're on " + rooms.spares + " deactivated tracks named \"" + rooms.spare.cb_() + "\", where they'd have played.");
        }
        return placements.size() + " clips back from " + pieces.size() + " pieces, " + unmappable.size() + " unmappable, " + dropped + " not brought back";
    }

    /** Where a clip can go without overlapping: the track, or else a deactivated track made for overlaps. */
    private static final class Rooms {
        private final xJo track;
        private final List<xJo> tracks = new ArrayList<>();
        private final List<List<double[]>> taken = new ArrayList<>();
        xJo spare;
        int spares; // how many overlap tracks were made: one per extra layer of overlaps

        Rooms(xJo track) {
            this.track = track;
            tracks.add(track);
            taken.add(new ArrayList<>());
        }

        xJo claim(double start, double end) {
            for (int layer = 0; ; layer++) {
                if (layer == tracks.size()) {
                    spare = spareTrack();
                    spares++;
                    tracks.add(spare);
                    taken.add(new ArrayList<>());
                }
                List<double[]> room = taken.get(layer);
                boolean free = true;
                for (double[] range : room) if (start < range[1] - EPSILON && end > range[0] + EPSILON) free = false;
                if (free) {
                    room.add(new double[]{start, end});
                    return tracks.get(layer);
                }
            }
        }

        private xJo spareTrack() {
            ruS parent = track.zjb();
            xJo made = parent.MRY(fP.Hnt); // a hybrid track: it takes note and audio clips
            made.cfM(track.PD3()); // colour
            made.d_(track.cb_() + " (overlaps)");
            parent.cfM((zwV) made, parent.ugU()); // last of the group's regular tracks
            made.qoU(false); // Bitwig's Deactivate
            return made;
        }
    }
}
