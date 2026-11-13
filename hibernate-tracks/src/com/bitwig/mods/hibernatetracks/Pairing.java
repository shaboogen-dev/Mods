package com.bitwig.mods.hibernatetracks;

import bitwig.obf.xJo; // a track
import com.bitwig.flt.document.core.master.g2L; // a project
import com.bitwig.ramona.core.zjj; // walks a project's objects of one class

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How a hibernated copy finds its source: the copy's comment names the source track's UUID. Both are
 * stored by Bitwig in the project, so the pair survives saving, reordering, renaming and removing
 * this mod, and the comment tells someone without the mod what to do.
 */
final class Pairing {
    private static final Pattern MARK = Pattern.compile("\\[hibernate-tracks source=([0-9a-fA-F-]{36})\\]");

    private static final Pattern LINK = Pattern.compile("Hibernated copy of .*?\\[hibernate-tracks source=[0-9a-fA-F-]{36}\\]", Pattern.DOTALL);

    private Pairing() {
    }

    static String comment(xJo source) {
        return "Hibernated copy of \"" + source.cb_() + "\". That track stays in this project, deactivated. "
                + "To go back without the Hibernate Tracks mod: activate it and delete this track. "
                + "[hibernate-tracks source=" + source.kFU() + "]"; // cb_: displayed name; kFU: track UUID
    }

    /** A track's comment without what this mod wrote in it. */
    static String withoutLink(String comment) {
        return Pieces.withoutMark(LINK.matcher(comment).replaceAll("")).trim();
    }

    /** The source UUID named in a track's comment, or null if it isn't a hibernated copy. */
    static UUID sourceId(xJo track) {
        String comment = track.AgD(); // the track's comment
        if (comment == null) return null;
        Matcher found = MARK.matcher(comment);
        return found.find() ? UUID.fromString(found.group(1)) : null;
    }

    static List<xJo> allTracks(g2L project) {
        List<xJo> tracks = new ArrayList<>();
        zjj.MRY(project, xJo.class, null, object -> tracks.add((xJo) object));
        return tracks;
    }

    /** One selected track's pair, or why there is none. */
    record Pair(xJo source, xJo copy, String problem) {
        static Pair of(xJo source, xJo copy) {
            return new Pair(source, copy, null);
        }

        static Pair none(String problem) {
            return new Pair(null, null, problem);
        }
    }

    /** Finds the pair of a selected track, which may be the copy or the source. Changes nothing. */
    static Pair find(xJo selected) {
        List<xJo> tracks = allTracks(selected.cc_());
        UUID wanted = sourceId(selected);
        if (wanted != null) {
            List<xJo> sources = new ArrayList<>();
            for (xJo track : tracks) if (wanted.equals(track.kFU())) sources.add(track);
            if (sources.isEmpty()) {
                return Pair.none("The track \"" + selected.cb_() + "\" was hibernated from isn't in this project any more.");
            }
            if (sources.size() > 1) {
                return Pair.none("Several tracks could be the source of \"" + selected.cb_() + "\".");
            }
            return Pair.of(sources.get(0), selected);
        }
        UUID id = selected.kFU();
        List<xJo> copies = new ArrayList<>();
        if (id != null) for (xJo track : tracks) if (id.equals(sourceId(track))) copies.add(track);
        if (copies.isEmpty()) return Pair.none("\"" + selected.cb_() + "\" has no hibernated copy in this project.");
        if (copies.size() > 1) return Pair.none("\"" + selected.cb_() + "\" has more than one hibernated copy.");
        return Pair.of(selected, copies.get(0));
    }
}
