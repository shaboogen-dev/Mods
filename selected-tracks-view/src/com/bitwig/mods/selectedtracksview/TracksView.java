package com.bitwig.mods.selectedtracksview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import bitwig.obf.GJD; // a size policy: never smaller than the natural size
import bitwig.obf.LJt; // a text value
import bitwig.obf.OpO; // a size policy: exactly the natural size
import bitwig.obf.aBW; // a main window's project view
import bitwig.obf.aDn; // a dialog that can dock in the main window or float in its own
import bitwig.obf.beB; // a list listener: items inserted, removed, changed
import bitwig.obf.dSK; // Bitwig's scroll view, with its scroll bars
import bitwig.obf.dWb; // a layout box: a row or a column
import bitwig.obf.dWh; // a bar of one colour
import bitwig.obf.dXp; // Bitwig's separator line
import bitwig.obf.dnF; // a box with padding
import bitwig.obf.dns; // binds a widget property to a model value
import bitwig.obf.dpN; // Bitwig's application (event loop)
import bitwig.obf.dpx; // a window size
import bitwig.obf.dpy; // a window position
import bitwig.obf.drT; // a window size as a share of the screen
import bitwig.obf.dtX; // Bitwig's dialog
import bitwig.obf.duw; // where a label's text is cut when it doesn't fit
import bitwig.obf.dvb; // a vertical label (text reading bottom to top)
import bitwig.obf.dve; // a widget
import bitwig.obf.dwf; // where a window opens
import bitwig.obf.dwg; // how large a window opens
import bitwig.obf.dwh; // a window move or resize
import bitwig.obf.efi; // a dialog's style: its frame colours and the padding of its title bar and body
import bitwig.obf.egd; // a padding (left, right, top, bottom)
import bitwig.obf.eiH; // the theme
import bitwig.obf.ejt; // a colour
import bitwig.obf.kn; // a size policy: the natural size
import bitwig.obf.pPb; // an observable list
import bitwig.obf.rMe; // a value a widget property can be bound to
import bitwig.obf.w3; // Bitwig's standard widget sizes
import bml.api.Settings;
import com.bitwig.base.geom.IBe; // a layout axis
import com.bitwig.base.geom.tfK; // a size
import com.bitwig.flt.document.core.iface.RYd; // a channel: a track, group, effect track or the master
import com.bitwig.flt.document.core.iface.pHd; // a project
import com.bitwig.flt.widget.core.device.THR; // the device chain widget's actions
import com.bitwig.flt.widget.core.device.htp; // the full device chain widget the device panel shows
import com.bitwig.ramona.core.brd; // a model object's list, observable
import com.bitwig.ramona.core.uzc; // a widget property that can be bound

/**
 * The Selected Tracks view of one window: one row per track, each a vertical header (the track's
 * name and colour, drawn as Bitwig draws a device header's name) and Bitwig's full device chain for
 * that track, rows separated by Bitwig's separator line, in Bitwig's scroll view. The dialog and row
 * code is All Lanes View's (Mods/all-lanes-view, LanesView), taken over as a copy.
 *
 * It is a Bitwig dialog that implements Bitwig's dockable-dialog interface, as Bitwig's own
 * expanded device view (The Grid's) does: Bitwig docks it in the main window or floats it in its own
 * window and gives it the dock and undock buttons. Nothing is written into the project: the docked
 * or floating choice is kept for the Bitwig session, the floating window's size and place in the
 * mod's settings.
 *
 * The tracks are the ones given when it opened; it doesn't follow the selection. A track deleted
 * from the project drops its row; when none is left, the view closes.
 */
final class TracksView extends dtX implements aDn {
    private static final Settings STORE = SelectedTracksView.MOD.settings();
    private static final String X = "window.x", Y = "window.y", W = "window.width", H = "window.height";

    /** A device's height, and the margin above and below it: Bitwig's device panel (aYz) is exactly
     *  DEVICE_HEIGHT + 2 * CHAIN_MARGIN high (its natural height, held fixed). */
    private static final long DEVICE_HEIGHT = 219, CHAIN_MARGIN = 14;
    /** The track colour bar at the top of a row's header. */
    private static final long COLOUR_BAR = 4;
    /** The padding around a device header's contents (L1F's dnF.Hnt(header, 3)). */
    private static final int HEADER_PADDING = 3;
    /** The docked title bar's padding: The Grid's expanded view's (its style, "Device Mapping Editor"). */
    private static final int TITLE_PADDING = 3;

    final aBW window;
    final pHd project;
    private final List<RYd> tracks = new ArrayList<>(); // what the view shows, in Bitwig's track order
    private final Column column;
    private final Map<RYd, dve> rows = new IdentityHashMap<>();
    private final List<dve> separators = new ArrayList<>();
    private final dve filler; // the stretchable space after the last row, always there
    private final List<RYd> shown = new ArrayList<>();
    private boolean built; // the rows have been built once
    /** The track lists a shown track's deletion shows up in (its group's and every group's above). */
    private final Set<brd> followed = Collections.newSetFromMap(new IdentityHashMap<>());
    private final beB tracksChanged = new beB() {
        @Override
        public void addedAsListenerTo(pPb list) {
        }

        @Override
        public void aboutToRemoveAsListenerFrom(pPb list) {
        }

        @Override
        public void insertedItems(pPb list, int from, int count) {
        }

        @Override
        public void aboutToRemoveItems(pPb list, int from, int count) {
            queueRefresh(); // runs after the removal
        }

        @Override
        public void aboutToChangeItems(pPb list, int from, int count) {
        }

        @Override
        public void changedItems(pPb list, int from, int count) {
            queueRefresh();
        }
    };
    private boolean refreshQueued;
    private boolean disposed;
    /** The device panel's node this view hid (the preference), to show again on close; null if none. */
    private Object hiddenPanel;
    private efi style; // built on first use (Bitwig may ask for it while the dialog is being set up)

    TracksView(aBW window, pHd project, List<RYd> tracks) {
        super();
        this.window = window;
        this.project = project;
        this.tracks.addAll(tracks);
        zqN(); // set the dialog up
        column = new Column(); // a column
        // Never lower than its rows (the scroll view scrolls the rest), and as high as it is given:
        // the space after the last row keeps the column's maximum height unbounded, so the docked
        // view fills its place (All Lanes View dev.3 and dev.8).
        column.MRY(GJD.cfM());
        filler = column.JJY();
        follow();
        refresh();
        // Bitwig's scroll view (scroller dSK around a viewport dTA), both axes scrollable: the view
        // keeps the size its host gives it and scrolls, with Bitwig's scroll bars.
        dSK scroll = dSK.MRY(column);
        scroll.FFu().cfM(PEn().dvk()); // the viewport's background, as the device panel's (pzf) "Grey 0"
        IwV(scroll); // the content
        // The device panel's background ("Grey 0", PEn().ycx()), on the whole dialog and the scroll view.
        ejt background = PEn().ycx();
        UNj(background);
        scroll.UNj(background);
        vpw(false);
        Grk(true); // disposed when closed
        cfM(THR.Hnt, this::Hnt); // "Toggle Expanded Device View" closes it while it has focus
        H_();
    }

    /** The dialog's title. */
    void title(String text) {
        ((uzc) Txc()).setValue(text);
    }

    /** Hides the window's device panel if it is shown, remembering that this view hid it. */
    void hideDevicePanel() {
        hiddenPanel = DevicePanel.hide(window);
    }

    private static boolean sameItems(List<RYd> a, List<RYd> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i) != b.get(i)) return false;
        }
        return true;
    }

    // --- following deletions ---

    /** Listens to the track lists a shown track's deletion would change (a hook isn't needed). */
    private void follow() {
        Set<brd> wanted = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            for (RYd track : tracks) Tracks.listsAbove(track, project.VpW(), wanted);
        } catch (Throwable t) {
            SelectedTracksView.MOD.log("could not find the tracks' groups", t);
        }
        try {
            for (brd list : new ArrayList<>(followed)) {
                if (!wanted.contains(list)) {
                    list.MRY(tracksChanged);
                    followed.remove(list);
                }
            }
            for (brd list : wanted) {
                if (followed.add(list)) list.cfM(tracksChanged);
            }
        } catch (Throwable t) {
            SelectedTracksView.MOD.log("could not follow the track lists", t);
        }
    }

    private void unfollow() {
        try {
            for (brd list : followed) list.MRY(tracksChanged);
        } catch (Throwable t) {
            SelectedTracksView.MOD.log("could not stop following the track lists", t);
        }
        followed.clear();
    }

    private void queueRefresh() {
        if (refreshQueued || disposed) return;
        refreshQueued = true;
        dpN.TVZ().runAtEndOfCurrentEvent(() -> { // once, after the change has finished
            refreshQueued = false;
            if (disposed) return;
            refresh();
            if (!disposed) follow(); // a track moved to another group: follow its new lists
        });
    }

    /** Drops the rows of deleted tracks, adds rows for new ones, keeps the order. */
    private void refresh() {
        List<RYd> now = new ArrayList<>();
        try {
            for (RYd track : tracks) {
                if (Tracks.present(track, project)) now.add(track); // a deleted track's row is dropped
            }
        } catch (Throwable t) {
            SelectedTracksView.MOD.log("could not check the tracks", t);
            return;
        }
        if (now.size() != tracks.size()) {
            tracks.retainAll(now);
            follow();
        }
        if (built && now.isEmpty()) { // every shown track was deleted
            Hnt(); // close
            return;
        }
        if (built && sameItems(now, shown)) return;
        built = true;
        try {
            for (RYd track : new ArrayList<>(rows.keySet())) {
                if (!now.contains(track)) {
                    dve row = rows.remove(track);
                    column.MJ(row); // remove it
                    row.iy_(); // dispose it
                }
            }
            for (RYd track : shown) {
                dve row = rows.get(track);
                if (row != null) column.MJ(row);
            }
            for (dve line : separators) {
                column.MJ(line);
                line.iy_();
            }
            separators.clear();
            column.MJ(filler); // re-added after the rows
            boolean first = true;
            for (RYd track : now) {
                if (!first) { // between two rows
                    dve line = dXp.LzO(); // Bitwig's separator: its own thickness and theme colour
                    separators.add(line);
                    column.HL(line);
                }
                first = false;
                dve row = rows.get(track);
                if (row == null) {
                    row = row(track);
                    rows.put(track, row);
                }
                column.HL(row); // add
            }
            column.HL(filler); // the height below the last row: rows stay at the top
            shown.clear();
            shown.addAll(now);
        } catch (Throwable t) {
            SelectedTracksView.MOD.log("could not update the rows", t);
        }
    }

    /** One track: a vertical header at the left, Bitwig's full device chain beside it. */
    private dve row(RYd track) {
        TrackRow row = new TrackRow();
        row.cfM(header(track), 0.0f);
        row.cfM(chain(track), 1.0f);
        return row;
    }

    /**
     * The row's header, as All Lanes View's: the track's name in a vertical label, set up as Bitwig
     * sets up a device header's name (oOM): "Light Text" colour, cut at the right, upper case, in the
     * theme's "Bold" font, on the theme's "Device Header" colour, with the track's colour on top. As
     * wide as a device header (see {@link Plate}). Name and colour are bound to the track's, so a
     * rename or a new colour shows at once.
     */
    private dve header(RYd track) {
        eiH theme = jl_();
        dvb name = dvb.LzO();
        name.cfM(theme.Ai().cfM("Bold")); // font
        name.Hnt((ejt) theme.BAa().MYI()); // colour: "Light Text"
        name.MRY(kn.cfM());
        name.MRY(duw.FzB); // cut at the right
        name.lYc(0.4f);
        name.E3(0.5f);
        LJt trackName = track.z3I(); // the track's name
        dns.cfM((uzc) name.cCk(), (rMe) trackName.g_()); // the track's name, live, upper case
        dnF padded = dnF.cfM(name, 1, 1, 5, 5);
        padded.lYc(0.5f);
        padded.E3(0.5f);
        padded.Nnn(true);
        padded.Lcd(true);

        Plate centred = new Plate(); // the name in the middle of the row's height
        centred.a_(OpO.cfM()); // held at its natural width: a device header's
        centred.JJY();
        centred.HL(padded);
        centred.JJY();

        dWh colour = dWh.IwV(COLOUR_BAR); // a horizontal bar
        dns.cfM((uzc) colour.QOl(), (rMe) track.BDm()); // the track's colour, live

        dWb strip = dWb.UNj(0);
        strip.HL(colour);
        strip.cfM(dnF.Hnt(centred, HEADER_PADDING), 1.0f); // the device header's 3 px (L1F)
        strip.UNj(PEn().cfM("Device Header"));
        return strip;
    }

    /** The track's chain, as the device panel builds it (aYz: htp.FzB(track.Ucl())). */
    private dve chain(RYd track) {
        htp chain = htp.FzB(track.Ucl()); // the chain, as the device panel shows it
        chain.MRY(kn.cfM());
        chain.E3(0.5f);
        dnF framed = dnF.cfM(chain, 0, 0, 4, 4);
        framed.Nnn(true);
        framed.Lcd(true);
        framed.UNj(PEn().ycx());
        framed.a_(GJD.cfM()); // never narrower than the chain: a wider row scrolls the view
        return framed;
    }

    // --- the dialog's style (All Lanes View's: Bitwig's "Default" with The Grid's title bar) ---

    @Override
    public efi sut() {
        if (style == null) {
            try {
                style = chrome();
            } catch (Throwable t) {
                SelectedTracksView.MOD.log("could not set up the view's style", t);
                return super.sut();
            }
        }
        return style;
    }

    private efi chrome() {
        efi base = super.sut(); // Bitwig's default dialog style
        efi chrome = efi.cfM("Default", jl_()); // the same style, built as Bitwig builds it
        ejt frame = base.wGc(); // the default's unfocused frame colour, "Grey 3"
        chrome.cfM(frame); // unfocused
        chrome.Hnt(frame); // focused: the same, so focus doesn't change the view's colour
        chrome.UZr().MIO().MRY(TITLE_PADDING); // the title bar's padding, as The Grid's
        egd body = base.rGR().MIO();
        // The body's padding: the default's at the sides and bottom; none at the top.
        chrome.rGR().MIO().MRY(body.LaE(), body.Ucl(), 0, body.ERO());
        return chrome;
    }

    // --- Bitwig's dockable-dialog interface ---

    /** The project the view belongs to (the floating window's header and its dock button use it). */
    @Override
    public pHd hUR() {
        return project;
    }

    /** Docked back into the main window: the next view opens docked (this Bitwig session). */
    @Override
    public void lB_() {
        SelectedTracksView.undocked = false;
    }

    /** Undocked into its own window: the next view opens in its own window (this Bitwig session). */
    @Override
    public void FzB() {
        SelectedTracksView.undocked = true;
    }

    // --- disposed ---

    @Override
    public void iy_() {
        if (!disposed) {
            disposed = true;
            unfollow();
            if (hiddenPanel != null) DevicePanel.restore(window, hiddenPanel); // only if this view hid it
            hiddenPanel = null;
            SelectedTracksView.disposed(this);
        }
        super.iy_();
    }

    // --- the floating window's place and size, kept in the mod's settings ---

    @Override
    public dwf lx_() { // where it opens
        if (STORE.get(X, null) != null) return new dpy(STORE.getInt(X, 0), STORE.getInt(Y, 0));
        return super.lx_();
    }

    @Override
    public dwg ly_() { // its size
        if (STORE.get(W, null) != null) return new dpx(STORE.getInt(W, 800), STORE.getInt(H, 600));
        return new drT(0.6); // as Bitwig's expanded device view: 60% of the screen
    }

    @Override
    public void cfM(dwh move) { // moved
        super.cfM(move);
        STORE.putInt(X, (int) move.zab());
        STORE.putInt(Y, (int) move.qTC());
    }

    @Override
    public void MRY(dwh resize) { // resized
        super.MRY(resize);
        STORE.putInt(W, (int) resize.pvS());
        STORE.putInt(H, (int) resize.ASH());
    }

    /** The column holding a header's name: at least as wide as the inside of a device header (27 px at 100%). */
    static final class Plate extends dWb {
        Plate() {
            super();
            FzB(IBe.MRY); // vertical
            H_();
            TMF(0); // no gap
        }

        @Override
        public tfK iH_() { // natural size
            tfK size = super.iH_();
            size.cfM(Math.max(size.FzB(), eLY((long) w3.MRY.cfM())));
            return size;
        }
    }

    /**
     * The view's column of rows, with an unbounded maximum size both ways, rows or not, so the docked
     * view always fills its place (All Lanes View dev.8: a column of only the spacer has a maximum
     * width of 0, and Bitwig leaves such a child out).
     */
    static final class Column extends dWb {
        Column() {
            super();
            FzB(IBe.MRY); // vertical
            H_();
            TMF(0); // no gap
        }

        @Override
        public tfK nj_() { // maximum size
            tfK size = super.nj_();
            size.cfM(Long.MAX_VALUE); // width
            size.MRY(Long.MAX_VALUE); // height
            return size;
        }
    }

    /** A row: as high as Bitwig's device panel makes a chain, or higher if its content asks for it. */
    static final class TrackRow extends dWb {
        TrackRow() {
            super();
            FzB(IBe.cfM); // horizontal
            H_();
            TMF(0); // no gap between the header and the chain
            MRY(OpO.cfM());
        }

        @Override
        public tfK iH_() { // natural size
            tfK size = super.iH_();
            size.MRY(Math.max(size.IwV(), eLY(DEVICE_HEIGHT) + eLY(CHAIN_MARGIN) * 2));
            return size;
        }
    }
}
