package com.bitwig.mods.mixmap;

import java.util.ArrayList;
import java.util.List;

import bitwig.obf.aBW; // a window's project view
import bitwig.obf.dns; // a widget, as Bitwig types it
import bitwig.obf.dve; // a widget
import bitwig.obf.dJH; // a mouse press, as a gesture receives it
import bitwig.obf.dJI; // a mouse release
import bitwig.obf.dJM; // a mouse move while a gesture is held
import bitwig.obf.dmO; // a callback before each display frame
import bitwig.obf.dpM; // a display frame
import bitwig.obf.dpN; // Bitwig's UI event loop
import bitwig.obf.drX; // a mouse gesture (press, drag, release)
import bitwig.obf.drd; // a subscription to the display frames
import bitwig.obf.drv; // told when the UI scale around a widget changes
import bitwig.obf.duG; // a window
import bitwig.obf.eeY; // a theme colour
import bitwig.obf.eoV; // a set of held modifier keys
import bitwig.obf.eoW; // a mouse button
import bitwig.obf.iok; // a colour
import bml.panels.ViewContext;
import com.bitwig.base.geom.tfK; // a widget size (width, height)
import com.bitwig.graphics.Hpk; // a font
import com.bitwig.graphics.SYo; // Bitwig's drawing surface

/**
 * Mix Map's widget: the stage (each track a dot, left/right = pan, up/down = volume, a ring for
 * its level; gridlines and labels for both scales, the stage's edges at +6 / -inf and L100 /
 * R100) and, on its right, the list of tracks. Clicking a name or a dot selects the track in
 * Bitwig; dragging a dot adjusts pan and volume as one gesture (with Shift held, along one axis
 * only); double-click resets both, Cmd/Ctrl-click the volume, Alt-click the pan. Every selected
 * dot has its name beside it (the current track's brighter), drawn under all the dots so that a
 * name never hides a dot; names of dots close together can overlap each other. Clicking a track's colour square in the list hides
 * or shows its dot (an outline and a dimmed name while hidden); a hidden dot is neither drawn nor
 * clickable.
 *
 * Several dots at once: the selection is Bitwig's track selection (selecting several tracks in
 * the mixer selects their dots, and the other way round); selected dots are ringed and their rows
 * highlighted. Dragging on empty stage draws a rectangle that selects the tracks of the dots in it
 * (with Shift, adds them), Shift-click on a dot or a name adds it or takes it out, and a click on
 * empty stage clears the selection. Dragging a selected dot moves all the shown selected dots by
 * the same distance on the stage, stopping when the first reaches an edge, so their spacing stays;
 * a reset on a selected dot resets them all. Hidden dots stay selected in Bitwig but Mix Map
 * neither moves nor resets them.
 *
 * Dots are drawn in Bitwig's track order, so where they overlap the later track is on top, the
 * selection above the rest and Bitwig's current track above all. A click picks the dot drawn on
 * top under the pointer.
 *
 * While the view is shown, it reads the project once per display frame and repaints only when
 * something changed; it stops reading while hidden and when it is disposed (a window rebuild).
 */
final class MixMapWidget extends dve implements drv {
    // Sizes in Bitwig's layout units (scaled with the UI, eLY).
    private static final long PAD = 14;
    private static final long LIST_WIDTH = 170;
    private static final long ROW_HEIGHT = 20;
    private static final long DOT_RADIUS = 6;
    private static final long HALO = 10;
    private static final long SCALE = 18;  // room for the pan scale's labels under the stage
    private static final long SWATCH = 8;  // a list row's colour square
    private static final long LOCK_THRESHOLD = 4; // how far the pointer moves with Shift held before the axis is chosen

    private ViewContext context;   // what the framework built it for
    private aBW window;
    private MixMapModel model;
    private drd frames;            // the display-frame subscription while shown
    private boolean subscribing;
    private double listScroll;     // pixels the list is scrolled down
    private double[] band;         // the selection rectangle being dragged (x0, y0, x1, y1), null when none

    private MixMapWidget() {
    }

    static MixMapWidget create(ViewContext context) {
        MixMapWidget widget = new MixMapWidget();
        widget.context = context;
        widget.window = (aBW) context.window();
        widget.model = new MixMapModel(widget.window, context.savedKey(MixMapPanels.HIDDEN_TRACKS));
        widget.H_();            // Bitwig's initialisation (the framework puts it in Bitwig's panel widget)
        return widget;
    }

    // ---------- Sizes: no minimum, no maximum (fills its place like a stock panel) ----------

    @Override
    public tfK mL_() {
        return tfK.cfM(); // zero
    }

    @Override
    public tfK nj_() {
        return tfK.MRY(); // unbounded
    }

    @Override
    public dve MJ() {
        return this; // Bitwig's typed self
    }

    @Override
    public dns UZr() {
        return this; // Bitwig's typed self
    }

    // ---------- Display frames: read the project while shown ----------

    private final dmO tick = new dmO() {
        @Override
        public void aboutToPaint(dpM frame) {
            onFrame();
        }
    };

    private void onFrame() {
        try {
            if (!shown()) {
                stopFrames();
                return;
            }
            if (model.refresh()) jbi(); // jbi(): repaint
        } catch (Throwable failure) {
            stopFrames();
            MixMapPanels.MOD.log("Mix Map stopped reading the project", failure);
        }
    }

    /** Whether it is shown: attached to a window, and its view shown there (in a stack, its "is shown"). */
    private boolean shown() {
        return ZTl() && context.shown(); // ZTl(): attached to a window
    }

    private void startFrames() {
        if (frames != null || subscribing) return;
        subscribing = true;
        dpN.TVZ().asyncExec(() -> { // after the current paint
            subscribing = false;
            if (frames == null && shown()) frames = drd.cfM(tick, this); // this window's display frames
        });
    }

    private void stopFrames() {
        if (frames != null) {
            frames.cfM(); // unsubscribes
            frames = null;
        }
    }

    @Override
    public void iy_() {
        stopFrames(); // disposed: the window closes or is rebuilt
        super.iy_();
    }

    /**
     * The UI scale around it changed (a panel scale: Bitwig's scaled container tells every widget
     * in it that implements drv, as its own widgets learn of it): repaint at the new scale.
     */
    @Override
    public void cfM(duG window) {
        jbi();
    }

    // ---------- Geometry ----------

    private double listWidth() {
        return Math.min(eLY(LIST_WIDTH), Sxo() * 0.4); // Sxo(): width
    }

    private double stageLeft() {
        return eLY(PAD) + eLY(HALO);
    }

    private double stageRight() {
        return Sxo() - listWidth() - eLY(PAD) - eLY(HALO);
    }

    private double stageTop() {
        return eLY(PAD) + eLY(HALO);
    }

    private double stageBottom() {
        return yiX() - eLY(PAD) - eLY(SCALE) - eLY(HALO); // yiX(): height
    }

    private double dotX(MixMapModel.Track track) {
        return stageLeft() + track.panNorm * Math.max(1, stageRight() - stageLeft());
    }

    private double dotY(MixMapModel.Track track) {
        return volumeY(track.volumePos);
    }

    /** The y of a position on the volume scale (0: -inf, at the bottom; 1: the maximum, at the top). */
    private double volumeY(double position) {
        return stageTop() + (1 - position) * Math.max(1, stageBottom() - stageTop());
    }

    // ---------- Painting ----------

    @Override
    public void cfM(SYo g) {
        startFrames(); // (Bitwig's own paint for a widget draws nothing; the panel's background is its parent's)
        try {
            if (model.tracks().isEmpty()) model.refresh();
            paintAll(g);
        } catch (Throwable failure) {
            MixMapPanels.MOD.log("Mix Map could not draw", failure);
        }
    }

    private void paintAll(SYo g) {
        Hpk font = kI_().cfM(MrC(), this); // the widget's font (as a label's)
        iok text = e3y().cfM(this);        // the theme's text colour
        double ascent = font.cfM().cfM();  // the font's ascent
        g.qoU();                           // save
        g.cfM(font);
        eeY background = jl_().BAa().cfM("Grey 1"); // the theme's dark grey (as the timeline's); jl_(): the theme; BAa(): its colours
        if (background != null) {
            g.MRY(0, 0, Sxo(), yiX());
            g.Hnt(background.cfM(this));
            g.wGc();
        }
        paintStage(g, font, text, ascent);
        paintList(g, font, text, ascent);
        g.ucQ();                           // restore
    }

    private void paintStage(SYo g, Hpk font, iok text, double ascent) {
        double left = stageLeft(), top = stageTop(), right = stageRight(), bottom = stageBottom();
        if (right <= left || bottom <= top) return;
        paintVolumeScale(g, text, ascent, left, top, right, bottom);
        paintPanScale(g, font, text, ascent, left, top, right, bottom);
        g.MRY(left, top, right - left, bottom - top); // the stage's frame: +6 and -inf, L100 and R100
        g.Hnt(text.cfM(0.12F));
        g.IwV(1);
        g.hbP();                                      // stroke

        List<MixMapModel.Track> tracks = model.tracks();
        for (MixMapModel.Track track : tracks) { // the names first: the dots are drawn over them
            if (!track.adjustable() || track.hidden) continue;
            if (track.selected || track.inSelection) paintName(g, track, text, ascent);
        }
        List<MixMapModel.Track> chosen = new ArrayList<>();
        MixMapModel.Track selected = null;
        for (MixMapModel.Track track : tracks) {
            if (!track.adjustable() || track.hidden) continue; // a hidden track's dot is not drawn
            if (track.selected) selected = track;
            else if (track.inSelection) chosen.add(track);
            else paintDot(g, track, text, ascent);
        }
        for (MixMapModel.Track track : chosen) paintDot(g, track, text, ascent); // above the rest
        if (selected != null) paintDot(g, selected, text, ascent);                // on top
        if (band != null) paintBand(g, text);
    }

    private void paintDot(SYo g, MixMapModel.Track track, iok text, double ascent) {
        double x = dotX(track), y = dotY(track), r = eLY(DOT_RADIUS);
        if (track.level > 0.01) { // the level ring
            g.MRY(x, y, r + eLY(HALO) * track.level); // a circle
            g.Hnt(track.colour.cfM((float) (0.25 + 0.35 * track.level)));
            g.wGc();                                  // fill
        }
        g.MRY(x, y, r);
        g.Hnt(track.colour);
        g.wGc();
        if (track.inSelection) { // a ring: in Bitwig's track selection
            g.MRY(x, y, r + eLY(2));
            g.Hnt(text);
            g.IwV(eLY(2) / 2.0 + 1);
            g.hbP();
        }
    }

    /** A selected dot's name, to the right of its ring: the current track's in the full text colour, the others' dimmer. */
    private void paintName(SYo g, MixMapModel.Track track, iok text, double ascent) {
        double x = dotX(track) + eLY(DOT_RADIUS) + eLY(4), y = dotY(track) + ascent / 2 - 1;
        g.cfM(x, y, track.name, track.selected ? text : text.cfM(0.6F));
    }

    /** The selection rectangle being dragged. */
    private void paintBand(SYo g, iok text) {
        double x = Math.min(band[0], band[2]), y = Math.min(band[1], band[3]);
        double w = Math.abs(band[2] - band[0]), h = Math.abs(band[3] - band[1]);
        g.MRY(x, y, w, h);
        g.Hnt(text.cfM(0.06F));
        g.wGc();
        g.MRY(x + 0.5, y + 0.5, Math.max(0, w - 1), Math.max(0, h - 1));
        g.Hnt(text.cfM(0.4F));
        g.IwV(1);
        g.hbP();
    }

    /**
     * The volume scale: +6, 0, -6, -12, -24, -48 and -inf at equal heights (MixMapModel's marks),
     * +6 the stage's top edge and -inf its bottom edge; a gridline across the stage at each mark in
     * between (0 dB a little stronger). Labels beside the pan-centre line, the edges' inside the stage.
     */
    private void paintVolumeScale(SYo g, iok text, double ascent, double left, double top, double right, double bottom) {
        double centre = (left + right) / 2;
        int last = MixMapModel.VOLUME_MARKS.length - 1;
        for (int i = 0; i <= last; i++) {
            double y = volumeY(1 - (double) i / last);
            if (i > 0 && i < last) line(g, left, y, right, y, text.cfM(MixMapModel.VOLUME_MARKS[i] == 0 ? 0.16F : 0.07F));
            double baseline = i == 0 ? y + ascent + eLY(2) : i == last ? y - eLY(3) : y + ascent / 2 - 1;
            g.cfM(centre + eLY(4), baseline, MixMapModel.VOLUME_LABELS[i], text.cfM(0.35F));
        }
    }

    /** The pan scale's marks, left to right, every 25% each side. */
    private static final String[] PAN_LABELS = {"L100", "L75", "L50", "L25", "C", "R25", "R50", "R75", "R100"};

    /**
     * The pan scale: a gridline down the stage every 25% each side (C a little stronger), L100 and
     * R100 the stage's left and right edges; labels under the stage, centred on their lines.
     */
    private void paintPanScale(SYo g, Hpk font, iok text, double ascent, double left, double top, double right, double bottom) {
        int last = PAN_LABELS.length - 1;
        double baseline = bottom + eLY(HALO) + eLY(4) + ascent;
        for (int i = 0; i <= last; i++) {
            double x = left + (double) i / last * (right - left);
            if (i > 0 && i < last) line(g, x, top, x, bottom, text.cfM(i * 2 == last ? 0.16F : 0.07F));
            double width = font.cfM(PAN_LABELS[i]).IwV(); // the label's advance
            g.cfM(x - width / 2, baseline, PAN_LABELS[i], text.cfM(0.35F));
        }
    }

    private void line(SYo g, double x0, double y0, double x1, double y1, iok colour) {
        g.MRY(x0, y0);
        g.cfM(x1, y1);
        g.Hnt(colour);
        g.IwV(1);
        g.hbP();
    }

    private void paintList(SYo g, Hpk font, iok text, double ascent) {
        double left = Sxo() - listWidth(), width = listWidth(), height = yiX();
        if (width <= 0) return;
        g.MRY(left, 0, width, height);
        g.Hnt(text.cfM(0.05F));
        g.wGc();
        double row = eLY(ROW_HEIGHT);
        clampScroll();
        List<MixMapModel.Track> tracks = model.tracks();
        for (int i = 0; i < tracks.size(); i++) {
            double top = eLY(PAD) / 2 + i * row - listScroll;
            if (top + row < 0 || top > height) continue;
            MixMapModel.Track track = tracks.get(i);
            if (track.inSelection || track.selected) { // the selection; Bitwig's current track, if not in it, fainter
                g.MRY(left, top, width, row);
                g.Hnt(text.cfM(track.inSelection ? 0.15F : 0.07F));
                g.wGc();
            }
            double swatch = eLY(SWATCH);
            double swatchTop = top + (row - swatch) / 2;
            if (track.hidden) { // an outline: its dot is hidden
                g.MRY(left + eLY(6) + 0.5, swatchTop + 0.5, swatch - 1, swatch - 1);
                g.Hnt(track.colour);
                g.IwV(1);
                g.hbP();
            } else {            // filled: its dot is shown
                g.MRY(left + eLY(6), swatchTop, swatch, swatch);
                g.Hnt(track.colour);
                g.wGc();
            }
            g.cfM(left + eLY(6) + swatch + eLY(6), top + (row + ascent) / 2 - 1, track.name, track.hidden ? text.cfM(0.4F) : text);
        }
    }

    private void clampScroll() {
        double content = eLY(PAD) + model.tracks().size() * (double) eLY(ROW_HEIGHT);
        double max = Math.max(0, content - yiX());
        listScroll = Math.max(0, Math.min(listScroll, max));
    }

    // ---------- Mouse ----------

    private boolean inList(double x) {
        return x >= Sxo() - listWidth();
    }

    /** Whether a point in the list is on (or just beside) a row's colour square. */
    private boolean onSwatch(double x) {
        return x < Sxo() - listWidth() + eLY(6) + eLY(SWATCH) + eLY(3);
    }

    private MixMapModel.Track rowAt(double y) {
        int index = (int) Math.floor((y + listScroll - eLY(PAD) / 2) / eLY(ROW_HEIGHT));
        List<MixMapModel.Track> tracks = model.tracks();
        return index >= 0 && index < tracks.size() ? tracks.get(index) : null;
    }

    /** The shown dot under the pointer that is drawn on top: Bitwig's selected track, then the selection, then the rest. */
    private MixMapModel.Track dotAt(double x, double y) {
        double reach = eLY(DOT_RADIUS) + eLY(3);
        MixMapModel.Track top = null;
        int topLayer = -1;
        for (MixMapModel.Track track : model.tracks()) {
            if (!track.adjustable() || track.hidden) continue; // a hidden dot can't be clicked
            double dx = x - dotX(track), dy = y - dotY(track);
            if (dx * dx + dy * dy > reach * reach) continue;
            int layer = track.selected ? 2 : track.inSelection ? 1 : 0;
            if (layer >= topLayer) { // later tracks are drawn above earlier ones in their layer
                top = track;
                topLayer = layer;
            }
        }
        return top;
    }

    /** The shown dots whose centre is in the rectangle (x0, y0, x1, y1). */
    private List<MixMapModel.Track> dotsIn(double[] rect) {
        double left = Math.min(rect[0], rect[2]), right = Math.max(rect[0], rect[2]);
        double top = Math.min(rect[1], rect[3]), bottom = Math.max(rect[1], rect[3]);
        List<MixMapModel.Track> inside = new ArrayList<>();
        for (MixMapModel.Track track : model.tracks()) {
            if (!track.adjustable() || track.hidden) continue;
            double x = dotX(track), y = dotY(track);
            if (x >= left && x <= right && y >= top && y <= bottom) inside.add(track);
        }
        return inside;
    }

    /** The selected tracks whose dots are shown (a hidden dot is neither moved nor reset). */
    private List<MixMapModel.Track> shownSelection() {
        List<MixMapModel.Track> shown = new ArrayList<>();
        for (MixMapModel.Track track : model.selectedTracks()) if (!track.hidden && track.adjustable()) shown.add(track);
        return shown;
    }

    private static boolean containsSame(List<Object> items, Object item) {
        for (Object each : items) if (each == item) return true;
        return false;
    }

    /** An offset (in 0..1 units) limited so that no start position plus it leaves 0..1: a group stops when its first dot reaches an edge. */
    private static double groupOffset(double offset, double[] starts) {
        double low = -1, high = 1;
        for (double start : starts) {
            low = Math.max(low, -start);
            high = Math.min(high, 1 - start);
        }
        return Math.max(low, Math.min(high, offset));
    }

    /**
     * Which gesture a press here would start. Bitwig asks this on a real press, and also on every
     * mouse move (for the cursor and hover, and for the status bar's hints it asks once per
     * modifier combination), so it must not change anything: it only returns the gesture, and the
     * gesture acts when a real press reaches it (its press callback). Acting here selected tracks
     * on hover, and reset volume and pan on the moves after a drag (the Ctrl and Alt probes).
     *
     * The click count is the number of clicks before this one (Bitwig's "prior click count"): 0 for
     * a single click, 1 for the second click of a double-click (as Bitwig's own double-click
     * handlers test it).
     */
    @Override
    public drX MRY(eoW button, double x, double y, eoV mods, int clicks) {
        if (button != eoW.cfM) return null; // eoW.cfM: the main button
        try {
            if (model.tracks().isEmpty()) model.refresh(); // reads only
            boolean shift = mods.cfM(); // cfM(): Shift among the held keys
            if (inList(x)) return new ListPress(rowAt(y), y, onSwatch(x), shift);
            MixMapModel.Track track = dotAt(x, y);
            if (track == null) return new BandPress(x, y, shift); // empty stage: a selection rectangle, or a click that clears
            if (clicks >= 1) return new DotPress(track, x, y, DotPress.RESET_BOTH, false); // a double-click
            if (mods.equals(eoV.lYc)) return new DotPress(track, x, y, DotPress.RESET_VOLUME, false); // eoV.lYc: Cmd on macOS, Ctrl elsewhere, alone
            if (mods.equals(eoV.hUR)) return new DotPress(track, x, y, DotPress.RESET_PAN, false);    // eoV.hUR: Alt alone
            return new DotPress(track, x, y, DotPress.DRAG, shift);
        } catch (Throwable failure) {
            MixMapPanels.MOD.log("Mix Map could not handle a click", failure);
            return null;
        }
    }

    /**
     * A press on a dot. DRAG: selects the track and adjusts pan and volume while dragging, as one
     * adjusting gesture (a controller's touch, set, release). The resets act on the press. On a
     * selected dot, both act on every shown selected dot (all touched at the press, set on each
     * move and released together, as one dot's pan and volume are). A drag moves them all by the
     * same distance on the stage, across and up or down, limited so that none passes an edge: the
     * group stops when its first dot reaches L100 / R100 or +6 / -inf (a dot at -inf is at the
     * bottom edge like any other). Their dB differences change as they cross into bands of other
     * sizes: the spacing kept is the one on screen.
     *
     * A plain press on a dot outside the selection selects its track alone; on a selected dot it
     * makes its track Bitwig's current track and keeps the selection. With Shift, a dot outside the
     * selection is added, and a selected one is taken out if the pointer is released without a drag.
     *
     * Holding Shift while dragging locks the dot to one axis, as in drawing programs: once the
     * pointer has moved LOCK_THRESHOLD from the press, the axis it moved along most is kept (pan
     * only or volume only, the other back at its value from the press) until Shift is released,
     * which frees both again. Bitwig sends no move for a key alone, so a change of Shift shows on
     * the next move. (Shift isn't Bitwig's fine adjustment here: that is each stock gesture's own
     * handling of its moves, and this gesture's moves are the pointer's own positions.)
     */
    private final class DotPress extends drX {
        static final int DRAG = 0;
        static final int RESET_BOTH = 1;
        static final int RESET_VOLUME = 2;
        static final int RESET_PAN = 3;

        private final MixMapModel.Track track;
        private final double pressX, pressY;
        private final int kind;
        private final boolean shift;   // Shift-click: adds the dot to the selection, or takes it out
        private List<MixMapModel.Track> group = List.of(); // the dots the drag moves
        private double[] startPans, startVolumes; // pan 0..1; volume as positions on the stage
        private boolean pressed;
        private boolean adjusting;
        private boolean moved;          // the pointer went LOCK_THRESHOLD from the press
        private boolean removeOnRelease; // a Shift-press on a selected dot: out on a release without a drag
        private int axis = FREE; // with Shift held: the axis the dot is locked to
        static final int FREE = 0;       // no lock (Shift not held, or not decided yet)
        static final int HORIZONTAL = 1; // pan only
        static final int VERTICAL = 2;   // volume only

        DotPress(MixMapModel.Track track, double x, double y, int kind, boolean shift) {
            this.track = track;
            this.pressX = x;
            this.pressY = y;
            this.kind = kind;
            this.shift = shift;
        }

        /** The real press: only here does anything change. */
        @Override
        public void cfM(dJH press) {
            if (pressed) return;
            pressed = true;
            try {
                List<MixMapModel.Track> targets = track.inSelection ? shownSelection() : List.of(track);
                switch (kind) {
                    case RESET_BOTH -> targets.forEach(MixMapModel::resetToDefault);
                    case RESET_VOLUME -> targets.forEach(MixMapModel::resetVolume);
                    case RESET_PAN -> targets.forEach(MixMapModel::resetPan);
                    default -> {
                        if (track.inSelection) {      // the shown selected dots, this one among them
                            group = shownSelection();
                            if (shift) removeOnRelease = true;
                            else if (!track.selected) model.makeCurrent(track);
                        } else if (shift) {           // added to the selection
                            group = new ArrayList<>(shownSelection());
                            group.add(track);
                            model.addToSelection(track);
                        } else {                      // selected alone
                            group = List.of(track);
                            model.select(track);
                        }
                        if (!group.contains(track)) group = List.of(track);
                        startPans = new double[group.size()];
                        startVolumes = new double[group.size()];
                        for (int i = 0; i < group.size(); i++) {
                            startPans[i] = group.get(i).panNorm;
                            startVolumes[i] = group.get(i).volumePos;
                        }
                        for (MixMapModel.Track member : group) MixMapModel.startAdjusting(member);
                        adjusting = true;
                        jbi(); // repaint: the selection may have changed
                    }
                }
            } catch (Throwable failure) {
                MixMapPanels.MOD.log("Mix Map could not handle a click", failure);
            }
        }

        @Override
        public void cfM(dJM move) {
            if (!adjusting) return;
            double width = Math.max(1, stageRight() - stageLeft());
            double height = Math.max(1, stageBottom() - stageTop());
            double dx = move.hbP() - pressX; // hbP(): the pointer's x in the widget
            double dy = move.Grk() - pressY; // Grk(): its y
            if (Math.max(Math.abs(dx), Math.abs(dy)) >= eLY(LOCK_THRESHOLD)) moved = true;
            if (move.E3().cfM()) {           // E3(): the held modifiers; cfM(): Shift among them
                if (axis == FREE && Math.max(Math.abs(dx), Math.abs(dy)) >= eLY(LOCK_THRESHOLD)) {
                    axis = Math.abs(dx) >= Math.abs(dy) ? HORIZONTAL : VERTICAL;
                }
                if (axis == FREE) { // not decided yet: stays where it was pressed
                    dx = 0;
                    dy = 0;
                }
                if (axis == HORIZONTAL) dy = 0;
                if (axis == VERTICAL) dx = 0;
            } else {
                axis = FREE;
            }
            double panOffset = groupOffset(dx / width, startPans);
            double volumeOffset = groupOffset(-dy / height, startVolumes);
            for (int i = 0; i < group.size(); i++) {
                MixMapModel.Track member = group.get(i);
                double volume = MixMapModel.positionToVolume(startVolumes[i] + volumeOffset, member.maxVolume());
                MixMapModel.adjust(member, startPans[i] + panOffset, volume);
            }
            jbi();
        }


        /**
         * The release: ends the adjusting gesture, then Bitwig's own release ends the mouse
         * gesture (without it the gesture stayed active until Escape).
         */
        @Override
        public void cfM(dJI release) {
            finish();
            if (removeOnRelease && !moved) { // a Shift-click on a selected dot: takes it out
                model.removeFromSelection(track);
                jbi();
            }
            super.cfM(release);
        }

        /** A cancel (Escape, or Bitwig ending the gesture): the adjusting gesture ends too. */
        @Override
        public void FzB() {
            finish();
            super.FzB();
        }

        private void finish() {
            if (!adjusting) return;
            adjusting = false;
            for (MixMapModel.Track member : group) MixMapModel.finishAdjusting(member);
        }

        /** Asks for the mouse moves while the gesture is held (without it Bitwig sends none: no drag). */
        @Override
        public boolean jC_() {
            return kind == DRAG;
        }
    }

    /**
     * A press in the list (on the real press): on a row's colour square, hides or shows its dot;
     * elsewhere on a row, selects its track alone, or with Shift adds it to the selection or takes it
     * out. Dragging scrolls the list.
     */
    private final class ListPress extends drX {
        private final MixMapModel.Track track; // null: below the last row
        private final double pressY;
        private final boolean swatch;          // on the colour square
        private final boolean shift;           // Shift held: adds to or takes out of the selection
        private double startScroll;
        private boolean pressed;

        ListPress(MixMapModel.Track track, double y, boolean swatch, boolean shift) {
            this.track = track;
            this.pressY = y;
            this.swatch = swatch;
            this.shift = shift;
        }

        @Override
        public void cfM(dJH press) {
            if (pressed) return;
            pressed = true;
            startScroll = listScroll;
            try {
                if (track == null) return;
                if (swatch) {
                    model.toggleHidden(track);
                } else if (shift) {
                    if (track.inSelection) model.removeFromSelection(track);
                    else model.addToSelection(track);
                } else {
                    model.select(track);
                }
                jbi(); // repaint
            } catch (Throwable failure) {
                MixMapPanels.MOD.log("Mix Map could not select or hide a track", failure);
            }
        }

        @Override
        public void cfM(dJM move) {
            listScroll = startScroll - (move.Grk() - pressY);
            clampScroll();
            jbi();
        }

        /** Asks for the mouse moves while the gesture is held. */
        @Override
        public boolean jC_() {
            return true;
        }
    }

    /**
     * A press on empty stage (on the real press): dragging draws a rectangle and makes the tracks
     * of the dots in it Bitwig's selection (with Shift, adds them to it); a click without a drag
     * clears the selection (with Shift, leaves it). A cancel (Escape) puts the selection back as it
     * was at the press.
     */
    private final class BandPress extends drX {
        private final double pressX, pressY;
        private final boolean adding;           // Shift held: adds to the selection
        private List<Object> before = List.of(); // Bitwig's selection at the press (all of it)
        private List<Object> last;              // what the rectangle last set
        private boolean pressed;
        private boolean dragged;

        BandPress(double x, double y, boolean adding) {
            this.pressX = x;
            this.pressY = y;
            this.adding = adding;
        }

        @Override
        public void cfM(dJH press) {
            if (pressed) return;
            pressed = true;
            before = model.selectionItems();
        }

        @Override
        public void cfM(dJM move) {
            if (!pressed) return;
            double x = move.hbP(), y = move.Grk();
            if (!dragged && Math.max(Math.abs(x - pressX), Math.abs(y - pressY)) < eLY(2)) return;
            dragged = true;
            band = new double[] {pressX, pressY, x, y};
            List<Object> chosen = new ArrayList<>(adding ? before : List.of());
            for (MixMapModel.Track track : dotsIn(band)) if (!containsSame(chosen, track.channel)) chosen.add(track.channel);
            if (!chosen.equals(last)) { // only when the rectangle took in or let go of a dot
                last = chosen;
                model.setSelectionItems(chosen);
            }
            jbi();
        }

        /** The release, then Bitwig's own release ends the mouse gesture. */
        @Override
        public void cfM(dJI release) {
            if (pressed && !dragged && !adding) model.clearSelection(); // a click on empty stage
            band = null;
            jbi();
            super.cfM(release);
        }

        /** A cancel (Escape, or Bitwig ending the gesture): the selection as it was. */
        @Override
        public void FzB() {
            if (pressed && dragged) model.setSelectionItems(before);
            band = null;
            jbi();
            super.FzB();
        }

        /** Asks for the mouse moves while the gesture is held. */
        @Override
        public boolean jC_() {
            return true;
        }
    }
}
