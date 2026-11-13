package com.bitwig.mods.alllanesview;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import bitwig.obf.GJD; // a size policy: never smaller than the natural size
import bitwig.obf.GzC; // a container device's list of chains (FX Layer, FX Selector, ...)
import bitwig.obf.LJt; // a text value
import bitwig.obf.OpO; // a size policy: exactly the natural size
import bitwig.obf.R1Z; // a Bitwig device's contents
import bitwig.obf.Sas; // one chain of a device: a lane or a named slot
import bitwig.obf.aDn; // a dialog that can dock in the main window or float in its own
import bitwig.obf.alw; // a device's nested chain slot (a Multiband band, Mid-Side's Mid, ...)
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
import bitwig.obf.foz; // a value listener
import bitwig.obf.kn; // a size policy: the natural size
import bitwig.obf.pPb; // an observable list
import bitwig.obf.rMe; // a value a widget property can be bound to
import bitwig.obf.w3; // Bitwig's standard widget sizes
import bml.api.Settings;
import com.bitwig.base.geom.IBe; // a layout axis
import com.bitwig.base.geom.tfK; // a size
import com.bitwig.flt.document.core.iface.NN; // a model object with a preset identifier
import com.bitwig.flt.document.core.iface.pHd; // a track
import com.bitwig.flt.widget.core.device.THR; // the device chain widget's actions
import com.bitwig.flt.widget.core.device.htp; // the full device chain widget the device panel shows
import com.bitwig.ramona.core.CbE; // a model object's reference to another, observable
import com.bitwig.ramona.core.brd; // a model object's list, observable
import com.bitwig.ramona.core.uzc; // a widget property that can be bound

/**
 * The All Lanes view of one device: one row per chain, each a vertical header (the lane's name,
 * drawn as Bitwig draws a device header's name) and Bitwig's full device chain for that chain,
 * rows separated by Bitwig's separator line, in Bitwig's scroll view.
 *
 * It is a Bitwig dialog that implements Bitwig's dockable-dialog interface, as Bitwig's own
 * expanded device view (The Grid's) does: Bitwig docks it in the main window or floats it in its own
 * window and gives it the dock and undock buttons. Nothing is written into the project: the docked
 * or floating choice and the floating window's size and place are the mod's settings.
 */
final class LanesView extends dtX implements aDn {
    private static final Settings STORE = AllLanesView.MOD.settings();
    static final String UNDOCKED = "undocked";
    private static final String X = "window.x", Y = "window.y", W = "window.width", H = "window.height";

    /** A device's height, and the margin above and below it: Bitwig's device panel (aYz) is exactly
     *  DEVICE_HEIGHT + 2 * CHAIN_MARGIN high (its natural height, held fixed). */
    private static final long DEVICE_HEIGHT = 219, CHAIN_MARGIN = 14;
    /** The lane colour bar at the top of a row's header (the mock-up's). */
    private static final long COLOUR_BAR = 4;
    /** The padding around a device header's contents (L1F's dnF.Hnt(header, 3)). */
    private static final int HEADER_PADDING = 3;
    /** The docked title bar's padding: The Grid's expanded view's (its style, "Device Mapping Editor"). */
    private static final int TITLE_PADDING = 3;

    final R1Z device;
    private final Column column;
    private final Map<Sas, dve> rows = new IdentityHashMap<>();
    private final List<dve> separators = new ArrayList<>();
    private final dve filler; // the stretchable space after the last row, always there
    private final List<Sas> shown = new ArrayList<>();
    private boolean built; // the rows have been built once
    private final brd lanes; // the container's lanes, observable; null for a device with fixed slots
    private final beB lanesChanged = new beB() {
        @Override
        public void addedAsListenerTo(pPb list) {
        }

        @Override
        public void aboutToRemoveAsListenerFrom(pPb list) {
        }

        @Override
        public void insertedItems(pPb list, int from, int count) {
            queueRefresh();
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
    /** Each slot's chain reference (a device with fixed slots), followed in case Bitwig replaces it. */
    private final List<CbE> slotChains = new ArrayList<>();
    private final foz slotChanged = event -> queueRefresh();
    private boolean refreshQueued;
    private boolean disposed;
    private efi style; // built on first use (Bitwig may ask for it while the dialog is being set up)

    LanesView(R1Z device) {
        super();
        this.device = device;
        GzC container = device.TMF();
        this.lanes = container != null ? container.iCS() : null;
        zqN(); // set the dialog up
        column = new Column(); // a column
        // Never lower than its rows (the scroll view scrolls the rest), and as high as it is given:
        // the space after the last row (refresh() keeps a stretchable spacer there) leaves its maximum
        // height unbounded. The scroll view reports its content's maximum as its own, and the dock
        // host gives a panel no more than its maximum height, so a column held at its rows' height
        // (dev.3) shrank the docked view to its rows.
        column.MRY(GJD.cfM());
        // Takes the height below the last row, so the rows stay at the top and the column's maximum
        // height is unbounded: the view fills its place whatever the number of rows, none included.
        filler = column.JJY();
        refresh();
        // Bitwig's scroll view (scroller dSK around a viewport dTA), both axes scrollable: unlike the
        // plain box dev.2 used, its minimum size doesn't follow its content, so the view keeps the size
        // its host gives it and scrolls, with Bitwig's scroll bars.
        dSK scroll = dSK.MRY(column);
        scroll.FFu().cfM(PEn().dvk()); // the viewport's background, as the device panel's (pzf) "Grey 0"
        IwV(scroll); // the content
        // The device panel's background: Bitwig's device panel (pzf) paints its body with the
        // theme colour PEn().ycx() ("Grey 0"). Painted on the whole dialog and on the scroll view;
        // follows the theme, as it is a named theme colour.
        ejt background = PEn().ycx();
        UNj(background);
        scroll.UNj(background);
        vpw(false);
        Grk(true); // disposed when closed
        cfM(THR.Hnt, this::Hnt); // "Toggle Expanded Device View" closes it while it has focus
        if (lanes != null) lanes.cfM(lanesChanged); // follow lanes added, removed or moved
        followSlots();
        H_();
    }

    /** A device with fixed slots: follows each slot's chain reference (alw.TMF()), which Bitwig sets
     *  when it gives the slot another chain object, so that row is rebuilt. */
    private void followSlots() {
        if (!ListedDevices.model(device).slots()) return;
        try {
            for (Object o : device.E3()) {
                if (o instanceof alw slot) {
                    CbE chain = slot.TMF();
                    chain.addListener(slotChanged);
                    slotChains.add(chain);
                }
            }
        } catch (Throwable t) {
            AllLanesView.MOD.log("could not follow the slots", t);
        }
    }

    /** One row's lane: its chain and the name its header shows. */
    record Lane(Sas chain, LJt name) {
    }

    /**
     * The device's lanes, each shown as one row. A device's model (see {@link ListedDevices}) is read
     * here in one pass so that every device gets the same rows:
     * <ul>
     * <li>its lane list (layers, selectors): the container's chains ({@code R1Z.TMF()}), named by the
     * chain's own name, as Bitwig's lane list names them;</li>
     * <li>its fixed slots: the device's nested chain slots ({@code R1Z.E3()}, the slot components
     * {@code alw}), each the slot's chain ({@code alw.hUR()}) named by the slot's name
     * ({@code alw.FzB()}, the "name" attribute; {@code alw.cfM()} is the same name as a string), as
     * Bitwig names a slot's chain where it shows one. A slot's ID is its preset identifier
     * ({@code NN.ay_()}). Pre first, the bands, Post last ({@link ListedDevices}). If a device lists
     * bands and none of them is found (a later Bitwig renamed them), every other slot is shown, in
     * the natural order of their names.</li>
     * </ul>
     */
    static List<Lane> chains(R1Z device) {
        List<Lane> out = new ArrayList<>();
        GzC container = device.TMF();
        if (container != null) {
            for (Object o : container.MIO()) { // its lanes
                if (o instanceof Sas lane && !has(out, lane)) out.add(new Lane(lane, lane.z3I()));
            }
        }
        ListedDevices.Model model = ListedDevices.model(device);
        if (!model.slots()) return out;
        List<alw> pre = new ArrayList<>(), post = new ArrayList<>(), others = new ArrayList<>();
        for (Object o : device.E3()) { // the device's components
            if (!(o instanceof alw slot)) continue; // its nested chain slots
            String name = name(slot), id = id(slot);
            if (ListedDevices.pre(name, id)) pre.add(slot);
            else if (ListedDevices.post(name, id)) post.add(slot);
            else others.add(slot);
        }
        List<alw> bands = new ArrayList<>();
        Map<alw, String> fallback = new IdentityHashMap<>();
        for (ListedDevices.Slot wanted : model.bands()) {
            for (alw slot : others) {
                if (wanted.id().equals(id(slot)) && !bands.contains(slot)) {
                    bands.add(slot);
                    fallback.put(slot, wanted.name());
                    break;
                }
            }
        }
        boolean natural = model.natural();
        if (model.bands().isEmpty() || bands.isEmpty()) {
            if (!model.bands().isEmpty() && !others.isEmpty()) {
                AllLanesView.MOD.log("no listed band found; showing every slot of the device");
            }
            bands = others;
            natural = true;
        }
        if (natural) bands.sort((a, b) -> ListedDevices.natural(name(a), name(b))); // stable
        for (alw slot : pre) add(out, slot, fallbackName(slot, fallback));
        for (alw slot : bands) add(out, slot, fallbackName(slot, fallback));
        for (alw slot : post) add(out, slot, fallbackName(slot, fallback));
        return out;
    }

    /** A slot's name now (Bitwig's, from the device's description), or "". */
    private static String name(alw slot) {
        String name = ((LJt) slot.FzB()).f_();
        return name != null ? name : "";
    }

    /** A slot's ID: its preset identifier ("LOW_CHAIN"), or "" if it has none. */
    static String id(alw slot) {
        String id = slot instanceof NN identified ? identified.ay_() : null;
        return id != null ? id : "";
    }

    private static String fallbackName(alw slot, Map<alw, String> listed) {
        String name = listed.get(slot);
        return name != null ? name : id(slot);
    }

    private static boolean has(List<Lane> lanes, Sas chain) {
        for (Lane lane : lanes) {
            if (lane.chain() == chain) return true;
        }
        return false;
    }

    /** Adds a slot's chain, named by the slot's name, or by {@code fallback} while the slot has none. */
    private static boolean add(List<Lane> out, alw slot, String fallback) {
        Sas chain = slot.hUR(); // the slot's chain
        if (chain == null || has(out, chain)) return false;
        Function<Object, Object> orFallback = n -> n == null || n.toString().isEmpty() ? fallback : n;
        out.add(new Lane(chain, ((LJt) slot.FzB()).cfM(orFallback))); // the slot's name, live
        return true;
    }

    private void queueRefresh() {
        if (refreshQueued || disposed) return;
        refreshQueued = true;
        dpN.TVZ().runAtEndOfCurrentEvent(() -> { // once, after the change has finished
            refreshQueued = false;
            if (!disposed) refresh();
        });
    }

    /** Adds a row for each new chain, drops removed ones and keeps the device's order. */
    private void refresh() {
        List<Lane> current;
        try {
            current = chains(device);
        } catch (Throwable t) {
            AllLanesView.MOD.log("could not list the chains", t);
            return;
        }
        List<Sas> now = new ArrayList<>();
        for (Lane lane : current) now.add(lane.chain());
        if (built && now.equals(shown)) return;
        built = true;
        try {
            for (Sas lane : new ArrayList<>(rows.keySet())) {
                if (!now.contains(lane)) {
                    dve row = rows.remove(lane);
                    column.MJ(row); // remove it
                    row.iy_(); // dispose it
                }
            }
            for (Sas lane : shown) {
                dve row = rows.get(lane);
                if (row != null) column.MJ(row);
            }
            for (dve line : separators) {
                column.MJ(line);
                line.iy_();
            }
            separators.clear();
            column.MJ(filler); // re-added after the rows
            boolean first = true;
            for (Lane lane : current) {
                if (!first) { // between two rows
                    dve line = dXp.LzO(); // Bitwig's separator: its own thickness and theme colour
                    separators.add(line);
                    column.HL(line);
                }
                first = false;
                dve row = rows.get(lane.chain());
                if (row == null) {
                    row = row(lane);
                    rows.put(lane.chain(), row);
                }
                column.HL(row); // add
            }
            column.HL(filler); // the height below the last row: rows stay at the top
            shown.clear();
            shown.addAll(now);
        } catch (Throwable t) {
            AllLanesView.MOD.log("could not update the rows", t);
        }
    }

    /** One chain: a vertical header at the left, Bitwig's full device chain beside it. */
    private dve row(Lane lane) {
        LaneRow row = new LaneRow();
        row.cfM(header(lane), 0.0f);
        row.cfM(chain(lane.chain()), 1.0f);
        return row;
    }

    /**
     * The row's header: the lane's name in a vertical label, set up as Bitwig sets up a device
     * header's name (oOM): "Light Text" colour, cut at the right, with the same padding, in the
     * theme's "Bold" font (the next named size up from the header's "Uppercase header": the same
     * Lato Semibold face at 11 instead of 10.25); on the theme's "Device Header" colour (L1F), with
     * the lane's colour on top. As wide as a device header (see {@link Plate}). The name is bound to
     * the lane's (see {@link #chains}), upper case as Bitwig shows a device's (LJt.g_()), so a rename
     * shows at once. The colour bar is bound to the chain's colour: a slot's chain has none (Bitwig's
     * default chain colour is transparent), so its bar shows the header's own colour.
     */
    private dve header(Lane lane) {
        eiH theme = jl_();
        dvb name = dvb.LzO();
        name.cfM(theme.Ai().cfM("Bold")); // font
        name.Hnt((ejt) theme.BAa().MYI()); // colour: "Light Text"
        name.MRY(kn.cfM());
        name.MRY(duw.FzB); // cut at the right
        name.lYc(0.4f);
        name.E3(0.5f);
        dns.cfM((uzc) name.cCk(), (rMe) lane.name().g_()); // the lane's name, live
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
        dns.cfM((uzc) colour.QOl(), (rMe) lane.chain().BDm()); // the lane's colour, live

        dWb strip = dWb.UNj(0);
        strip.HL(colour);
        strip.cfM(dnF.Hnt(centred, HEADER_PADDING), 1.0f); // the device header's 3 px (L1F)
        strip.UNj(PEn().cfM("Device Header"));
        return strip;
    }

    /** The lane's chain, wrapped as Bitwig's device panel (pzf) wraps a track's chain. */
    private dve chain(Sas lane) {
        htp chain = htp.FzB(lane.Ucl()); // the chain, as the device panel shows it
        chain.MRY(kn.cfM());
        chain.E3(0.5f);
        dnF framed = dnF.cfM(chain, 0, 0, 4, 4);
        framed.Nnn(true);
        framed.Lcd(true);
        framed.UNj(PEn().ycx());
        framed.a_(GJD.cfM()); // never narrower than the chain: a wider row scrolls the view
        return framed;
    }

    /** Whether the user last undocked the view (one choice for every device). */
    boolean storedUndocked() {
        return STORE.getBoolean(UNDOCKED, false);
    }

    // --- the dialog's style ---

    /**
     * The dialog's style. Bitwig's docked chrome (aYF) and floating window (doN) are built from it:
     * the frame behind the title bar and around the body is painted with the style's focused colour
     * while the dialog has keyboard focus, its unfocused colour otherwise (dsg), the title bar is
     * padded by the style's header padding and the body by its body padding. A dialog's default
     * (doH.sut(), the theme's "Default") is focused "Grey 4", unfocused "Grey 3", title bar padded
     * 8 above and below, body padded 12 all round: the view opened lighter (it takes focus on open,
     * as The Grid's does) and its title bar was 44 px high at 100%, its contents high in it.
     *
     * This style is Bitwig's "Default" built as Bitwig builds it (efl), with the default's unfocused
     * colour for both states and its body padding at the sides and bottom, and The Grid's title bar:
     * its expanded view's style ("Device Mapping Editor", BrJ) pads the title bar 3 all round and the
     * body 0. The Grid's own style isn't reused: it paints the frame "Grey 0" in both states.
     */
    @Override
    public efi sut() {
        if (style == null) {
            try {
                style = chrome();
            } catch (Throwable t) {
                AllLanesView.MOD.log("could not set up the view's style", t);
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
        // The body's padding: the default's at the sides and bottom; none at the top, so the title
        // bar's contents sit in the middle of the grey above the rows.
        chrome.rGR().MIO().MRY(body.LaE(), body.Ucl(), 0, body.ERO());
        return chrome;
    }

    // --- Bitwig's dockable-dialog interface ---

    /** The track the device is on (the floating window's header and its dock button use it). */
    @Override
    public pHd hUR() {
        return device.apV();
    }

    /** Docked back into the main window. */
    @Override
    public void lB_() {
        STORE.putBoolean(UNDOCKED, false);
    }

    /** Undocked into its own window. */
    @Override
    public void FzB() {
        STORE.putBoolean(UNDOCKED, true);
    }

    // --- shown, hidden, disposed ---

    @Override
    public void iA_() {
        super.iA_();
        AllLanesView.shownChanged(device);
    }

    @Override
    public void iB_() {
        super.iB_();
        AllLanesView.shownChanged(device);
    }

    @Override
    public void iy_() {
        if (!disposed) {
            disposed = true;
            try {
                if (lanes != null) lanes.MRY(lanesChanged);
                for (CbE chain : slotChains) chain.removeListener(slotChanged);
                slotChains.clear();
            } catch (Throwable t) {
                AllLanesView.MOD.log("could not stop following the lanes", t);
            }
            AllLanesView.disposed(this);
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

    /**
     * The column holding a header's name: at least as wide as the inside of a device header. A
     * device header (oOM) is a column whose buttons are w3.MRY wide (21 px at 100%), padded 3 px on
     * each side by L1F: 27 px. This column is that wide, or wider if its name needs more, and the
     * header pads it by the same 3 px.
     */
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
     * The view's column of rows. A column's size is its children's: across, the widest child; along,
     * their sum. Its only child when there are no rows is the stretchable spacer (dWb.JJY(), a dYh),
     * which is unbounded along the column but has a width of 0, natural and maximum (dXA.nj_():
     * gfw().cfM(max, 0)). An empty column so had a maximum size of 0 across, and Bitwig leaves out a
     * child whose maximum size is 0 in either direction (dve.ERO(dve), for widgets that ask for it,
     * ZTl()): the empty view's content counted as absent, and the docked view shrank to its title bar.
     * With a row the row's width made the maximum non-zero, so only an empty view collapsed. This
     * column's maximum is unbounded both ways, rows or not: the view always fills its place.
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

    /**
     * A row: as high as Bitwig's device panel makes a chain (aYz's natural height: a device and a
     * margin above and below, scaled to the interface size), or higher if its content asks for it,
     * and held at that height.
     */
    static final class LaneRow extends dWb {
        LaneRow() {
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
