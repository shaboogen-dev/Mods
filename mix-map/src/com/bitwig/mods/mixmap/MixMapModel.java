package com.bitwig.mods.mixmap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import bitwig.obf.Jmw; // a channel's mixer module (volume, pan, meters)
import bitwig.obf.aBW; // a window's project view
import bitwig.obf.dUW; // one saved value of the view settings
import bitwig.obf.dVe; // a saved text value
import bitwig.obf.iCC; // a selection of channels (with its cursor)
import bitwig.obf.iok; // a colour
import bitwig.obf.tft; // one meter value of a channel
import com.bitwig.flt.control_surface.values.RangedValueTarget; // a parameter's value, as controllers see it
import com.bitwig.flt.control_surface.values.ValueAtomParameterTarget; // a parameter, as controllers adjust it
import com.bitwig.flt.document.core.iface.RYd; // a channel (track, effect track, master)
import com.bitwig.flt.document.core.iface.kWY; // a group of tracks (the project's root is one)
import com.bitwig.flt.document.core.iface.pHd; // a project
import com.bitwig.ramona.core.ESe; // a text value, as Bitwig's code reads and sets one

/**
 * Mix Map's view of the project: the main tracks in Bitwig's order (each group track followed
 * by its own tracks; effect tracks and the master are left out),
 * with name, colour, volume, pan, level, whether it is the selected track and whether its dot is
 * hidden. Read again on
 * every display frame while the view is shown (refresh), so adds, removes, reorders, renames and
 * colour changes follow without per-track listeners; refresh says when something changed.
 *
 * The selection is Bitwig's own track selection, which can hold several tracks (Ctrl- and
 * Shift-click in the mixer or the arranger): read every frame, and changed through the same calls
 * Bitwig's own selection handling makes (select alone, add, remove, set all, clear, make current).
 * Its current track (the one the device panel follows) is the "selected" track.
 *
 * Mix Map's volume scale (see volumeToPosition) gives +6, 0, -6, -12, -24, -48 dB and -inf equal
 * heights, so there is more room per dB near the top, as on a fader.
 *
 * Hidden tracks are Mix Map's own: their ids (the channel's UUID, which the project keeps) are
 * the view's saved value (MixMapPanels.HIDDEN_TRACKS: one set per display profile, shared by every
 * place Mix Map is shown, each mode's tab and the stack's view), so hiding a dot never changes the project.
 * Without that value (not declared) the hidden set is kept in memory only.
 *
 * Volume and pan go through Bitwig's controller parameter targets, so a drag is what a
 * controller's touch / set / release is: one adjusting gesture (one undo step) per drag.
 */
final class MixMapModel {
    /** One channel as Mix Map shows it. */
    static final class Track {
        final RYd channel;
        final ValueAtomParameterTarget volume; // null when the channel has no mixer
        final ValueAtomParameterTarget pan;
        final RangedValueTarget volumeValue;
        final RangedValueTarget panValue;
        String name = "";
        iok colour = iok.Hnt; // grey until read
        double volumePos;     // 0 (bottom, -inf) .. 1 (top, the maximum), on Mix Map's scale
        double panNorm;       // 0 (left) .. 1 (right)
        double level;         // 0 .. 1, shaped like Bitwig's meters
        boolean selected;     // Bitwig's current track (the device panel's)
        boolean inSelection;  // in Bitwig's track selection (it can hold several)
        boolean hidden;       // its dot is hidden (Mix Map's own choice)
        final String id;      // its UUID as text, null if it has none

        Track(RYd channel) {
            this.channel = channel;
            UUID uuid = channel.kFU(); // the channel's id, kept with the project
            id = uuid != null ? uuid.toString() : null;
            Jmw mixer = channel.MIO(); // its mixer module
            volume = mixer != null ? ValueAtomParameterTarget.createForAtom(mixer.UNj()) : null; // UNj: volume
            pan = mixer != null ? ValueAtomParameterTarget.createForAtom(mixer.hUR()) : null;    // hUR: pan
            volumeValue = volume != null ? volume.getValueTarget() : null;
            panValue = pan != null ? pan.getValueTarget() : null;
        }

        boolean adjustable() {
            return volumeValue != null && panValue != null;
        }

        /** Its volume parameter's maximum value (+6 dB). */
        double maxVolume() {
            return volumeValue.normalizedValueToValue(1);
        }
    }

    private final aBW window;
    private final String hiddenKey; // the view settings' key of the hidden tracks
    private final Map<Object, Track> known = new IdentityHashMap<>();
    private final List<RYd> channels = new ArrayList<>();                                 // reused every frame
    private final Set<Object> chosenSet = Collections.newSetFromMap(new IdentityHashMap<>()); // reused every frame
    private List<Track> tracks = new ArrayList<>();
    private long signature;
    private String hiddenText = "";                        // the saved value, as last read
    private Set<String> hiddenIds = new LinkedHashSet<>(); // ... as a set
    private boolean hiddenInMemory;                        // no saved value: the set is kept here only

    MixMapModel(aBW window, String hiddenKey) {
        this.window = window;
        this.hiddenKey = hiddenKey;
    }

    /** The main tracks in Bitwig's order: each group track followed by its own tracks. */
    List<Track> tracks() {
        return tracks;
    }

    /** Reads the project again; returns whether anything Mix Map shows changed. */
    boolean refresh() {
        pHd project = window.mtS(); // the window's project
        List<Track> next = new ArrayList<>();
        Object selected = null;
        List<?> chosen = List.of();
        if (project != null) {
            kWY root = project.VpW(); // the project's tracks
            channels.clear();
            addTracks(root.uva(), channels);          // uva(): the main tracks (no effect tracks, no master)
            iCC selection = trackSelection(project);
            selected = selection != null ? selection.Fsx().getValue() : null; // Fsx(): the current track
            chosen = selection != null ? selection.FzB() : List.of();         // FzB(): the selected tracks
            for (RYd channel : channels) next.add(known.computeIfAbsent(channel, c -> new Track((RYd) c)));
        }
        channels.clear();
        if (known.size() > next.size()) { // a track went away (known holds every track of next, once)
            Set<Object> present = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Track track : next) present.add(track.channel);
            known.keySet().retainAll(present);
        }
        readHidden();

        long sig = 17;
        for (Track track : next) {
            track.name = track.channel.cb_();         // its name
            track.colour = track.channel.BDm().cfM(); // its colour
            if (track.adjustable()) {
                track.volumePos = volumeToPosition(track.volumeValue.getCurrentValue(), track.maxVolume());
                track.panNorm = clamp(track.panValue.getCurrentNormalizedValue());
            }
            track.level = level(track.channel);
            track.selected = track.channel == selected;
            track.hidden = track.id != null && hiddenIds.contains(track.id);
            sig = sig * 31 + System.identityHashCode(track.channel);
            sig = sig * 31 + track.name.hashCode();
            sig = sig * 31 + track.colour.qoU(); // qoU(): the colour as one int
            sig = sig * 31 + Math.round(track.volumePos * 4000);
            sig = sig * 31 + Math.round(track.panNorm * 4000);
            sig = sig * 31 + Math.round(track.level * 100);
            sig = sig * 31 + (track.selected ? 1 : 0);
            sig = sig * 31 + (track.hidden ? 1 : 0);
        }
        tracks = next;
        markSelection(chosen);
        for (Track track : next) sig = sig * 31 + (track.inSelection ? 1 : 0);
        boolean changed = sig != signature;
        signature = sig;
        return changed;
    }

    /** Adds the channels in Bitwig's order, each group followed by its own tracks. */
    private static void addTracks(List<?> list, List<RYd> into) {
        for (Object item : list) {
            if (item instanceof RYd channel) into.add(channel);
            if (item instanceof kWY group) addTracks(group.uva(), into); // a group's tracks
        }
    }

    /** The project's track selection (the selected track in the arranger and mixer). */
    private static iCC trackSelection(pHd project) {
        return project.wIw().E3(); // wIw(): the project's display settings; E3(): the track selection
    }

    /** The channel's level: the louder side's peak, shaped as Bitwig's meters are (cube root). */
    private static double level(RYd channel) {
        Jmw mixer = channel.MIO();
        if (mixer == null) return 0;
        List<?> meters = mixer.E3().FzB(); // its meter values: left, right, ...
        double peak = 0;
        for (int i = 0; i < Math.min(2, meters.size()); i++) {
            if (meters.get(i) instanceof tft meter) peak = Math.max(peak, meter.IwV()); // IwV(): the peak, linear
        }
        return clamp(Math.cbrt(peak));
    }

    // ---------- Bitwig's track selection ----------
    // The calls Bitwig's own selection handling makes (its UI selection wrapper: on the UI thread).

    private iCC selection() {
        pHd project = window.mtS();
        return project != null ? trackSelection(project) : null;
    }

    /** Selects the track alone and makes it the current track (as a plain click on a track in Bitwig does). */
    void select(Track track) {
        iCC selection = selection();
        if (selection != null) selection.UNj(track.channel);
        readSelection();
    }

    /** Makes the track Bitwig's current track, the selection staying as it is. */
    void makeCurrent(Track track) {
        iCC selection = selection();
        if (selection != null) selection.eLY(track.channel);
        readSelection();
    }

    void addToSelection(Track track) {
        iCC selection = selection();
        if (selection != null) selection.E3(track.channel);
        readSelection();
    }

    void removeFromSelection(Track track) {
        iCC selection = selection();
        if (selection != null) selection.wGc(track.channel);
        readSelection();
    }

    void clearSelection() {
        iCC selection = selection();
        if (selection != null) selection.iSC();
        readSelection();
    }

    /** Everything in Bitwig's track selection now (also what Mix Map doesn't show), to set back later. */
    List<Object> selectionItems() {
        iCC selection = selection();
        return selection != null ? new ArrayList<>(selection.FzB()) : new ArrayList<>();
    }

    /** Sets Bitwig's track selection to exactly these items (Bitwig removes the others and adds the new ones). */
    void setSelectionItems(Collection<Object> items) {
        iCC selection = selection();
        if (selection != null) selection.cfM(items);
        readSelection();
    }

    /** The tracks Mix Map shows that are in Bitwig's selection, in track order. */
    List<Track> selectedTracks() {
        List<Track> list = new ArrayList<>();
        for (Track track : tracks) if (track.inSelection) list.add(track);
        return list;
    }

    private void readSelection() {
        iCC selection = selection();
        markSelection(selection != null ? selection.FzB() : List.of());
    }

    private void markSelection(Collection<?> chosen) {
        chosenSet.clear();
        chosenSet.addAll(chosen);
        for (Track track : tracks) track.inSelection = chosenSet.contains(track.channel);
        chosenSet.clear();
    }

    // ---------- Hidden tracks ----------

    /** The view's saved value of hidden tracks in this window's view settings, null without one. */
    private ESe hiddenValue() {
        dUW value = window.RDJ().OzW().cfM(hiddenKey); // RDJ(): the view settings; OzW(): their values; cfM: by key
        return value instanceof dVe text ? text.lYc() : null;       // lYc(): as a value to read and set
    }

    /** Reads the saved hidden set (parsed again only when its text changed). */
    private void readHidden() {
        ESe value = hiddenValue();
        if (value == null) {
            if (!hiddenInMemory) MixMapPanels.MOD.log("Mix Map has no saved value for hidden tracks; keeping them in memory only");
            hiddenInMemory = true;
            return;
        }
        hiddenInMemory = false;
        Object read = value.getValue();
        String text = read instanceof String s ? s : "";
        if (text.equals(hiddenText)) return;
        hiddenText = text;
        Set<String> ids = new LinkedHashSet<>();
        for (String id : text.split(",")) if (!id.isBlank()) ids.add(id.trim());
        hiddenIds = ids;
    }

    /** Shows the track's dot if it is hidden, hides it otherwise (Mix Map's own choice, saved with the view). */
    void toggleHidden(Track track) {
        if (track.id == null) return;
        Set<String> ids = new LinkedHashSet<>(hiddenIds);
        if (!ids.remove(track.id)) ids.add(track.id);
        hiddenIds = ids;
        track.hidden = ids.contains(track.id);
        ESe value = hiddenValue();
        if (value == null) return; // in memory only
        hiddenText = String.join(",", ids);
        value.setValue(hiddenText);
    }

    // ---------- Adjusting, as a controller does: touch, set, release ----------

    static void startAdjusting(Track track) {
        track.volume.startedAdjusting(false, true);
        track.pan.startedAdjusting(false, true);
    }

    /** Sets pan (0..1) and volume (the parameter's value, 0 .. its maximum), and Mix Map's copy of both. */
    static void adjust(Track track, double panNorm, double volume) {
        double max = track.maxVolume();
        volume = Math.max(0, Math.min(max, volume));
        track.panValue.setCurrentValue(track.panValue.normalizedValueToValue(clamp(panNorm)));
        track.volumeValue.setCurrentValue(volume);
        track.panNorm = clamp(panNorm);
        track.volumePos = volumeToPosition(volume, max);
    }

    static void finishAdjusting(Track track) {
        track.volume.finishedAdjusting(false, true);
        track.pan.finishedAdjusting(false, true);
    }

    static void resetToDefault(Track track) {
        track.volume.setToDefault();
        track.pan.setToDefault();
    }

    static void resetVolume(Track track) {
        track.volume.setToDefault();
    }

    static void resetPan(Track track) {
        track.pan.setToDefault();
    }

    // ---------- Mix Map's volume scale ----------

    /** The volume scale's marks, top to bottom, in dB: each band between two of them is the same height. */
    static final double[] VOLUME_MARKS = {6, 0, -6, -12, -24, -48, Double.NEGATIVE_INFINITY};
    static final String[] VOLUME_LABELS = {"+6", "0", "-6", "-12", "-24", "-48", "-inf"};
    private static final int BANDS = VOLUME_MARKS.length - 1;
    private static final double LOWEST_DB = VOLUME_MARKS[BANDS - 1]; // -48: the last band's top

    /**
     * The volume parameter's value for a level in dB. Its value is the cube root of the
     * amplitude (0 dB = 1, its maximum +6 dB = 1.26), so a level in dB is 10^(dB / 60).
     */
    static double dbToValue(double dB) {
        return Math.pow(10, dB / 60);
    }

    /** The upper edge of a band (counted from the top), in dB: the top band's is the parameter's maximum (+6.02 dB, shown as +6). */
    private static double bandTop(int band, double max) {
        return band == 0 ? 60 * Math.log10(max) : VOLUME_MARKS[band];
    }

    /**
     * Where a volume value sits on Mix Map's scale: 0 (bottom, -inf) .. 1 (top, the maximum).
     * The six bands +6..0, 0..-6, -6..-12, -12..-24, -24..-48 and -48..-inf are each a sixth of the
     * height. Inside the first five, linear in dB. The last can't be linear in dB (it reaches
     * -inf); it is linear in the parameter's value (as the fader's own travel is), which meets
     * the band above at -48 dB without a jump and reaches 0 (-inf) at the bottom.
     */
    static double volumeToPosition(double value, double max) {
        if (!(value > 0)) return 0;
        double dB = 60 * Math.log10(value);
        if (dB >= bandTop(0, max)) return 1;
        if (dB < LOWEST_DB) return value / dbToValue(LOWEST_DB) / BANDS; // the last band
        int band = 0;
        while (dB < VOLUME_MARKS[band + 1]) band++;
        double top = bandTop(band, max), low = VOLUME_MARKS[band + 1];
        return (BANDS - 1 - band + (dB - low) / (top - low)) / BANDS;
    }

    /** The volume value at a position on Mix Map's scale (volumeToPosition's inverse). */
    static double positionToVolume(double position, double max) {
        double scaled = clamp(position) * BANDS;              // 0 .. 6, bands counted from the bottom
        if (scaled <= 1) return dbToValue(LOWEST_DB) * scaled; // the last band
        int fromBottom = Math.min(BANDS - 1, (int) Math.floor(scaled));
        int band = BANDS - 1 - fromBottom;                     // counted from the top
        double top = bandTop(band, max), low = VOLUME_MARKS[band + 1];
        return Math.min(max, dbToValue(low + (scaled - fromBottom) * (top - low)));
    }

    static double clamp(double value) {
        return value < 0 ? 0 : value > 1 ? 1 : value;
    }
}
