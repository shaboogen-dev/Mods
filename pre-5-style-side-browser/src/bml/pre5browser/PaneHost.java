package bml.pre5browser;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import com.bitwig.base.geom.IBe; // an orientation
import com.bitwig.flt.app.CHR; // the application
import com.bitwig.flt.packaging.manager.hgq; // an installed package

import bitwig.obf.AQw; // a boolean value
import bitwig.obf.BAM; // a size: at least this many pixels
import bitwig.obf.BY; // the Device Type filter
import bitwig.obf.DC; // a browser filter
import bitwig.obf.FE; // the Location filter
import bitwig.obf.Fq; // Bitwig's UI-thread executor
import bitwig.obf.GT; // a collection
import bitwig.obf.IW; // a browser result standing in for one not loaded yet
import bitwig.obf.Jp; // a browser session
import bitwig.obf.KIt; // a tree model
import bitwig.obf.RBf; // a result's colour
import bitwig.obf.VF; // the Everything source
import bitwig.obf.VGL; // a value-change event
import bitwig.obf.Wc; // the package sources
import bitwig.obf.Wl; // a browser source
import bitwig.obf.aCU; // a panel splitter: Bitwig's divider between a panel and its neighbour
import bitwig.obf.beB; // a list's listener
import bitwig.obf.cBe; // a filter chip
import bitwig.obf.cBi; // the browser configurations
import bitwig.obf.cDO; // Bitwig's tree filter column
import bitwig.obf.cDp; // Bitwig's list filter column
import bitwig.obf.cFV; // the side browser
import bitwig.obf.dAd; // a toggle button
import bitwig.obf.dKW; // a context menu
import bitwig.obf.dSK; // a padded widget
import bitwig.obf.dVx; // a splitter's sizing
import bitwig.obf.dVz; // a splitter
import bitwig.obf.dWF; // a wrapping row of widgets
import bitwig.obf.dXp; // a separator line
import bitwig.obf.dWb; // a row or column of widgets
import bitwig.obf.dnF; // a padded widget
import bitwig.obf.dve; // a widget
import bitwig.obf.egd; // padding
import bitwig.obf.eiy; // a tree's style
import bitwig.obf.emO; // a tree's expansion
import bitwig.obf.emR; // a tree path
import bitwig.obf.emb; // a tree's selection
import bitwig.obf.eoh; // a drag
import bitwig.obf.foz; // a listener
import bitwig.obf.iW2; // a fixed list model
import bitwig.obf.omW; // a tree node's children
import bitwig.obf.pPb; // a list
import bitwig.obf.ruA; // a widget's content sizes
import bitwig.obf.svm; // a widget's size policy
import bitwig.obf.tJ; // a filter tree's node
import bitwig.obf.tN; // a tree filter
import bitwig.obf.tz; // a list filter
import bitwig.obf.wt; // a browser result
import bml.browser.ResultsSlot;

/**
 * The side pane: while the view is active, Framework: Browser hands the side browser's results
 * column to {@link #build} (the view's results slot), which puts the pane beside it (so a change
 * applies when the browser is built). At its top the
 * paradigm tabs, built as Bitwig's source picker builds its own ({@code cFx}: toggle buttons in the
 * "Underline" style, labelled with Bitwig's own words, upper case), on one line, one per enabled
 * paradigm (none when only one is). Below, the chosen paradigm's list. The source is always
 * Everything; each tab's choice is a filter, so the tabs combine (each must match) and each shows
 * Bitwig's chip:
 * <ul>
 *   <li><b>Packages, Collections, By Kind:</b> the sources the picker lists (installed packages from
 *       the package manager; Everything, Favourites and the collections; the built-in sources whose
 *       group is Sounds, without rekordbox Library and File Browser) as a flat list in Bitwig's tree
 *       column, the host of the Location tree: each row is Bitwig's own result row (the source's
 *       icon and name), selected while it is the tab's choice. Choosing one sets the tab's filter
 *       ({@link TabFilter}: the source's own query); Everything clears Collections'. Filled when
 *       first shown, never during the browser's layout; Collections again when the collections change.</li>
 *   <li><b>Locations:</b> Bitwig's Location tree column and filter, with its counts.</li>
 * </ul>
 *
 * <p>The pane and the results share a horizontal splitter, placed where the side browser adds its
 * results column under the filter area, set up as the pop-up browser's beside its results: the
 * divider sets the pane's width (in layout units, stored between launches) and the results take
 * the rest, so moving the browser panel's own edge changes only the results. Each column has a
 * minimum. A browser panel still at the width Bitwig gives a project gets the width the user last
 * dragged it to (at first one with room for both columns), so a new project opens as the last one
 * was left ({@link #widenBrowser}).
 *
 * <p>Safety is the framework's: a launch after one whose slot never came on screen sets Browser view
 * back to Standard, and a failure while building (this throws) shows the results column alone.
 */
final class PaneHost {
    /** The paradigms, in the picker's group order, with the picker's label key and the tab filter for each (Locations: Bitwig's Location filter). */
    enum Paradigm {
        PACKAGES("Packages", "Packages", "packages", TabFilter.Tab.PACKAGE),
        COLLECTIONS("Collections", "Collections", "collections", TabFilter.Tab.COLLECTION),
        KIND("Kind", "By Kind", "sounds", TabFilter.Tab.KIND), LOCATIONS("Locations", "Locations", "locations", null);

        /** Its name in the log, its tab's name in Settings (as the tab reads), and the picker's label key. */
        final String label, tabLabel, key;
        final TabFilter.Tab tab;

        Paradigm(String label, String tabLabel, String key, TabFilter.Tab tab) {
            this.label = label;
            this.tabLabel = tabLabel;
            this.key = key;
            this.tab = tab;
        }

        /** On in Settings (all on until the user turns one off). */
        boolean enabled() {
            return !"off".equals(Store.read("pane.on." + name()));
        }

        void setEnabled(boolean on) {
            Store.write("pane.on." + name(), on ? "on" : "off");
        }
    }

    private static final String WIDTH = "pane.units"; // the pane's width, in layout units
    /** The browser panel's width the user last dragged it to, in layout units (Bitwig saves it per project only). */
    private static final String BROWSER_WIDTH = "browser.units";
    /**
     * The pane's width in layout units (1.5 screen px each at 150 %): until the user drags its
     * divider (room for "Test Collection" or "Instrument" beside their icons); its minimum (an icon
     * and a short name: neither divider squeezes it further); and the widest a drag stores.
     */
    private static final long PANE_DEFAULT = 110, PANE_MIN = 80, PANE_MAX = 2000;
    /**
     * The browser panel's width in layout units that {@link #widenBrowser} gives a panel still at
     * Bitwig's starting width (222 in Single Large Monitor) until the user drags the panel's edge:
     * the pane's default, the divider and about 160 for the results' names, wide enough for the
     * tabs on one line. Then the width they dragged it to, between the panel's minimum and
     * {@link #BROWSER_MAX}.
     */
    private static final long BROWSER_START = 270, BROWSER_MAX = 4000;
    /** Each panel splitter's position as {@link #widenBrowser} set it, so that setting isn't stored as the user's. */
    private static final Map<aCU, Double> SET_BY_PANE = Collections.synchronizedMap(new WeakHashMap<>());
    /** Sources this pane leaves out: browsers of their own, not sources it can switch to. */
    private static final List<String> LEFT_OUT = List.of("builtin:rekordbox_library", "builtin:file_browser");
    private static final Timer TIMER = new Timer("bml-browser-pane", true);
    private static final Set<dVz> WATCHED = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));
    /** Every pane built, for the collections' changes. */
    private static final List<WeakReference<PaneHost>> PANES = new ArrayList<>();
    /**
     * A tab's width: its label's width when there is room, down to {@link #TAB_MIN} pixels when
     * there isn't, so four tabs never make the pane (and the window) wider than the screen.
     */
    private static final long TAB_MIN = 24;
    private static final svm TAB_WIDTH = new svm() {
        @Override
        public long MRY(ruA content) { // the minimum
            return Math.min(content.cfM(TAB_MIN), content.MRY());
        }

        @Override
        public long cfM(ruA content) { // the preferred size: the label's
            return content.MRY();
        }

        @Override
        public long Hnt(ruA content) { // the maximum
            return Math.max(content.Hnt(), content.MRY());
        }
    };

    private final cFV browser;
    private final Jp session;
    private final Map<Paradigm, Flag> chosenFlags = new EnumMap<>(Paradigm.class);
    private final Map<Paradigm, dve> lists = new EnumMap<>(Paradigm.class);
    private final Map<Paradigm, SourceList> sourceLists = new EnumMap<>(Paradigm.class);
    private final Set<Paradigm> filled = Collections.newSetFromMap(new EnumMap<>(Paradigm.class));
    /** The filters' chips, shown while their filter applies, and the row holding them. */
    private final Map<DC, cBe> chips = new LinkedHashMap<>();
    private dve filterRow;
    /** The tabs and the filter chips, across the browser's full width above the pane and the results. */
    private dve header;
    /** The Device Type filter and its column under the list, shown while the source offers it. */
    private BY deviceType;
    private dve deviceTypes;
    /**
     * The type column's height: a fixed share at the bottom of the pane (room for "Any", the three
     * device types, Samples and Other, about 26 px a row; more scroll), whatever its scroller
     * reports, so the list above can't squeeze it out. The list above takes the rest and scrolls.
     */
    private static final long DEVICE_TYPES_HEIGHT = 180; // six rows
    /**
     * The results' width beside the pane: their own preferred and maximum, but a minimum of at most
     * {@link #RESULTS_MIN}. Once results load, the results column's minimum is about 304 px (its
     * rows), and the splitter keeps the pane within the rest ({@code dVz.hUR(long)}: at most the
     * width less the results' minimum): in a 327 px browser the pane got 22 px whatever the share,
     * and a drag couldn't widen it, so no share was ever stored (pane23 log, dev.40).
     */
    private static final long RESULTS_MIN = 120;
    /**
     * The pane's width: at least {@link #PANE_MIN}. With the results' minimum this is the browser
     * panel's minimum; dragged below half of it, Bitwig collapses the panel, as it does every
     * collapsible panel ({@code dVz.cfM(double)}: the first side is hidden below half its minimum).
     */
    private static final svm PANE_WIDTH = new svm() {
        @Override
        public long MRY(ruA content) { // the minimum
            return content.cfM(PANE_MIN);
        }

        @Override
        public long cfM(ruA content) { // the preferred size: the content's, at least the minimum
            return Math.max(content.MRY(), content.cfM(PANE_MIN));
        }

        @Override
        public long Hnt(ruA content) { // the maximum: the content's, at least the minimum
            return Math.max(content.Hnt(), content.cfM(PANE_MIN));
        }
    };
    private static final svm RESULTS_WIDTH = new svm() {
        @Override
        public long MRY(ruA content) { // the minimum
            return Math.min(content.cfM(RESULTS_MIN), content.cfM());
        }

        @Override
        public long cfM(ruA content) { // the preferred size: the content's
            return content.MRY();
        }

        @Override
        public long Hnt(ruA content) { // the maximum: the content's
            return content.Hnt();
        }
    };
    private static final svm FIXED_HEIGHT = new svm() {
        @Override
        public long MRY(ruA content) { // the minimum
            return content.cfM(DEVICE_TYPES_HEIGHT);
        }

        @Override
        public long cfM(ruA content) { // the preferred size
            return content.cfM(DEVICE_TYPES_HEIGHT);
        }

        @Override
        public long Hnt(ruA content) { // the maximum
            return content.cfM(DEVICE_TYPES_HEIGHT);
        }
    };
    private Paradigm chosen;

    private PaneHost(cFV browser, Jp session) {
        this.browser = browser;
        this.session = session;
    }

    // ---------------------------------------------------------------------
    // Settings
    // ---------------------------------------------------------------------

    /** The enabled paradigms (Locations when none is). */
    static List<Paradigm> enabled() {
        List<Paradigm> on = new ArrayList<>();
        for (Paradigm p : Paradigm.values()) {
            if (p.enabled()) on.add(p);
        }
        if (on.isEmpty()) on.add(Paradigm.LOCATIONS);
        return on;
    }

    // ---------------------------------------------------------------------
    // Building
    // ---------------------------------------------------------------------

    /**
     * The view's results slot: the header across the browser, then the pane and the results column
     * side by side. Throws when the side browser can't take a pane (the framework then shows the
     * results alone and the view's claims lapse).
     */
    static Object build(ResultsSlot slot) {
        cFV browser = (cFV) slot.browser();
        Jp session = (Jp) slot.session();
        dve results = (dve) slot.results();
        FE location = session == null ? null : locationOf(session);
        if (browser == null || location == null) throw new IllegalStateException("the side browser has no Location filter");
        PaneHost pane = new PaneHost(browser, session);
        dve left = pane.build(location);
        left.a_(PANE_WIDTH); // horizontal: at least PANE_MIN
        results.a_(RESULTS_WIDTH); // horizontal: a small minimum, so the divider can move
        dVz split = dVz.cfM(IBe.cfM, left, results); // HORIZONTAL: the pane, then the results
        split.cfM(1); // as the pop-up browser's splitter beside its results
        split.cfM(dVx.cfM); // SPLITTER_SETS_FIRST_SIZE: the divider sets the pane's width; the results take the rest
        split.Hnt((double) storedWidth()); // the splitter's own position (dVC.Hnt(double)), in layout units
        dWb whole = dWb.MRY(0, new dve[] {pane.header, split}); // the header across the full width, then the pane and the results
        slot.onFiltersChanged(pane::filtersChanged);
        later(200, () -> settle(split, left, results, whole)); // once laid out
        Store.LOG.log("pane: built beside the results (" + pane.chosen.label + ", paradigms " + enabled() + ")");
        return whole;
    }

    /**
     * The header (the tabs, then the filters that apply), which goes across the full width of the
     * browser above the pane and the results, and the pane's column: each enabled paradigm's list
     * (only the chosen one shown), then the Device Type column. Returns the pane's column.
     */
    private dve build(FE location) {
        List<Paradigm> on = enabled();
        Paradigm first = on.get(0); // the leftmost tab: Packages, then Collections, Kind, Locations

        List<dve> tabList = new ArrayList<>();
        for (Paradigm p : on) {
            Flag flag = new Flag(() -> chosen == p, v -> {
                if (v) choose(p);
            });
            chosenFlags.put(p, flag);
            tabList.add(tab(p, flag));
        }
        dWb tabs = dWb.cfM(IBe.cfM, 8, tabList.toArray(new dve[0])); // one line, across the browser
        if (on.size() < 2) tabs.t_(false); // one paradigm: no tabs
        header = dWb.MRY(0, new dve[] {tabs, filterRow()});

        List<dve> column = new ArrayList<>();
        for (Paradigm p : on) {
            dve list;
            if (p == Paradigm.LOCATIONS) {
                list = cDO.cfM(location); // Bitwig's Location tree column
            } else {
                TabFilter filter = TabFilter.of(session, p.tab);
                SourceList sources = new SourceList(location, p.label, filter::chosen, (source, name) -> chooseRow(p, source, name)); // filled when first shown
                sourceLists.put(p, sources);
                list = sources;
            }
            dSK padded = browser.cfM(list, egd.cfM(3, 0, 3, 3)); // padded as the results are
            padded.MRY(new BAM(80));
            lists.put(p, padded);
            column.add(padded);
        }
        dve types = deviceTypes();
        if (types != null) {
            column.add(separator()); // Bitwig's line between browser sections
            column.add(types);
        }
        dWb left = dWb.MRY(0, column.toArray(new dve[0])); // a vertical column, as the results column

        everything();
        session.qoU().addListener(new foz() { // the browser's source changed: the lists' selection follows
            @Override
            public void eventOccurred(Object event) {
                Fq.cfM(() -> {
                    if (adoptSource()) return; // chosen as that tab's row: the lists and filters followed
                    for (SourceList list : sourceLists.values()) list.follow();
                    showFilters();
                });
            }
        });
        synchronized (PANES) {
            PANES.add(new WeakReference<>(this));
        }
        watchCollections();
        show(first, true);
        return left;
    }

    /**
     * The filters that apply, as Bitwig's own filter chips (built as the filter area builds its
     * chips, {@code cBe.cfM(DC, Runnable)}, at least 80 px wide, in a wrapping row): each chip's
     * clear button clears its filter. Shown only while a filter applies, since the view hides the
     * filter area; the source stays as it is.
     */
    private dve filterRow() {
        dWF flow = dWF.xcy();
        List<Object> filters = new ArrayList<>(session.zpW());
        BY types = session.Zy(); // Device Type isn't in the filter list (the filter area shows it as an icon strip)
        if (types != null && !filters.contains(types)) filters.add(types);
        for (Object o : filters) {
            if (!(o instanceof DC filter)) continue;
            try {
                cBe chip = cBe.cfM(filter, () -> {
                }); // clicking it would open the filter area's chooser, which the view hides
                chip.a_(new BAM(80));
                chip.t_(false);
                chips.put(filter, chip);
                flow.HL(chip);
            } catch (Throwable t) {
                Store.LOG.log("pane: a filter chip failed", t);
            }
        }
        filterRow = dnF.cfM(flow, 3, 3, 3, 3);
        filterRow.t_(false);
        return filterRow;
    }

    /**
     * The type column: the session's Device Type filter ({@code JA.Zy()}, made part of its filters)
     * as Bitwig's list filter column ({@code cDp.cfM(tz)}, the chooser the filter area opens for a
     * list filter, as the 4.x-5.x filter column), with Samples and Other as two more of its values
     * ({@link TypeRows}): Any, Instrument, Audio FX, Note FX, Samples, Other, each with its icon,
     * without counts. Choosing a row sets the filter; "Any" clears it. Padded as the lists, at a
     * fixed height. Null when the session has no Device Type filter.
     */
    private dve deviceTypes() {
        try {
            deviceType = session.Zy();
            if (deviceType == null) {
                Store.LOG.log("pane: the session has no Device Type filter");
                return null;
            }
            cDp column = cDp.cfM((tz) deviceType);
            TypeRows.withoutCounts(column); // no counts (see TypeRows.UNCOUNTED)
            dSK padded = browser.cfM(column, egd.cfM(3, 0, 3, 3));
            padded.MRY(FIXED_HEIGHT); // vertical: a fixed share at the bottom
            deviceTypes = padded;
            Store.LOG.log("pane: type column built (Device Type relevant now: " + ((tz) deviceType).ERO().getBoolValue() + ")");
            return deviceTypes;
        } catch (Throwable t) {
            Store.LOG.log("pane: the type column failed", t);
            deviceType = null;
            return null;
        }
    }

    /** A separator as Bitwig draws between browser sections ({@code dXp}, in the theme's colour the source picker uses). */
    private dve separator() {
        return dXp.cfM(browser.PEn().QOl());
    }

    /**
     * The session's filters ({@code Jp.zpW()}, the list its filter sections, relevance and queries
     * come from), with Device Type added for the side browser while the pane is in effect. Bitwig
     * 6.1.4 keeps Device Type out of that list (it is the controller API's device type column,
     * {@code JA.Zy()}), so the side browser never queries with it, its counts stay 0 and choosing a
     * type changes nothing. Added at the end, as {@code projects-in-browser} adds its filter. For the
     * sessions {@link #deviceTypeApplies}.
     */
    static List<?> withDeviceType(Jp session, List<?> filters) {
        if (filters == null || !deviceTypeApplies(session)) return filters;
        BY types = session.Zy();
        if (types == null || filters.contains(types)) return filters;
        List<Object> withTypes = new ArrayList<>(filters);
        withTypes.add(types);
        for (TabFilter.Tab tab : TabFilter.Tab.values()) withTypes.add(TabFilter.of(session, tab)); // the tabs' choices
        if (!deviceTypeAdded) Store.LOG.log("pane: Device Type and the Package, Collection and Kind filters added to the side browser's filters");
        deviceTypeAdded = true;
        return Collections.unmodifiableList(withTypes);
    }

    private static boolean deviceTypeAdded;
    private static boolean relevantLogged;

    /** True for the side browser's Device Type filter and tab filters while the pane is in effect: Bitwig makes them relevant there. */
    static boolean deviceTypeRelevant(Object filter) {
        if (!(filter instanceof BY types)) return false;
        Jp session = types.zpW();
        if (session == null || !deviceTypeApplies(session)) return false;
        if (types instanceof TabFilter) return true;
        if (session.Zy() != types) return false;
        if (!relevantLogged) Store.LOG.log("pane: Device Type made relevant in the side browser");
        relevantLogged = true;
        return true;
    }

    /**
     * True for a session of the side browser's configuration (Everything, {@code cBi.cfM}) while the
     * pane is in effect. Decided by the configuration, not by the side browser being registered:
     * the session updates its filter sections (and with them Device Type's relevance) while it is
     * restored, before the side browser is built, and again only when the source changes (dev.32:
     * the column worked only after a tab was switched). Other sessions of that configuration
     * (smart collections, snapshots) get Device Type too; with no type chosen it narrows nothing.
     */
    static boolean deviceTypeApplies(Jp session) {
        // The framework has its providers declared, and the chosen view active, before any filter list is built.
        return session != null && session.emh() == cBi.cfM && Pre5SideBrowser.active();
    }

    /** Shows the chips of the filters that apply (offered for the source, and with a value), and the row while any does. */
    private void showFilters() {
        boolean any = false;
        for (Map.Entry<DC, cBe> e : chips.entrySet()) {
            DC filter = e.getKey();
            // Device Type is never "offered" as a chip (the filter area has its strip instead): it applies with a value
            boolean applies = filter instanceof BY ? filter.ODA() : filter.EUG() && filter.ODA();
            if (e.getValue().uev() != applies) e.getValue().t_(applies);
            any |= applies;
        }
        if (filterRow != null && filterRow.uev() != any) filterRow.t_(any);
    }

    /** A filter of the pane's session got or lost a value, or was offered or not: the pane shows the filters that apply. */
    private void filtersChanged() {
        try {
            showFilters();
            for (SourceList list : sourceLists.values()) list.follow(); // a tab's chip cleared: its row too
        } catch (Throwable t) {
            Store.LOG.log("pane: showing the filters failed", t);
        }
    }

    /** A paradigm tab, as the source picker builds its tabs. */
    private dve tab(Paradigm p, Flag flag) {
        dAd tab = dAd.cfM(flag); // on while its paradigm is chosen
        UnaryOperator<String> upper = String::toUpperCase;
        tab.cfM(dve.cfM("source_browser", p.key, new Object[0]), upper); // Bitwig's own label (e.g. BY KIND), upper case
        tab.iHb("Underline");
        tab.a_(TAB_WIDTH); // narrower than its label when the pane is narrow
        return tab;
    }

    private static boolean collectionsWatched;

    /** Every pane's Collections list follows the library's collections, which All Sources can add to. Once: the library outlives the panes. */
    private void watchCollections() {
        if (collectionsWatched) return;
        try {
            session.Bpn().BBT().cfM(new beB() {
                @Override
                public void addedAsListenerTo(pPb list) {
                }

                @Override
                public void aboutToRemoveAsListenerFrom(pPb list) {
                }

                @Override
                public void insertedItems(pPb list, int from, int count) {
                    collectionsChanged();
                }

                @Override
                public void aboutToRemoveItems(pPb list, int from, int count) {
                    collectionsChanged();
                }

                @Override
                public void aboutToChangeItems(pPb list, int from, int count) {
                }

                @Override
                public void changedItems(pPb list, int from, int count) {
                    collectionsChanged();
                }
            });
            collectionsWatched = true;
        } catch (Throwable t) {
            Store.LOG.log("pane: watching the collections failed; a new one is listed after a restart", t);
        }
    }

    /** After the change is done, on the UI thread: a pane showing Collections lists them again; the others when next shown. */
    private static void collectionsChanged() {
        later(100, () -> {
            List<WeakReference<PaneHost>> panes;
            synchronized (PANES) {
                PANES.removeIf(ref -> ref.get() == null);
                panes = List.copyOf(PANES);
            }
            for (WeakReference<PaneHost> ref : panes) {
                PaneHost pane = ref.get();
                if (pane == null) continue;
                try {
                    pane.filled.remove(Paradigm.COLLECTIONS);
                    if (pane.chosen == Paradigm.COLLECTIONS) pane.fill(Paradigm.COLLECTIONS);
                } catch (Throwable t) { // a pane of a browser since rebuilt, not collected yet
                    Store.LOG.log("pane: listing the collections again failed", t);
                }
            }
        });
    }

    /** Fills a source paradigm's list (once, on the UI thread, after the browser is built, and again if {@link #filled} forgets it). */
    private void fill(Paradigm p) {
        SourceList list = sourceLists.get(p);
        if (list == null || !filled.add(p)) return;
        long started = System.currentTimeMillis();
        Map<String, Wl> sources = sources(p);
        List<SourceItem> items = new ArrayList<>();
        for (Map.Entry<String, Wl> e : sources.entrySet()) {
            try {
                items.add(new SourceItem(e.getValue(), e.getKey()));
            } catch (Throwable t) {
                Store.LOG.log("pane: a row for " + e.getKey() + " failed", t);
            }
        }
        list.set(items);
        Store.LOG.log("pane: " + p.label + ": " + items.size() + " sources in " + (System.currentTimeMillis() - started) + " ms");
    }

    /** A paradigm's sources by name, as the source picker lists them. */
    private Map<String, Wl> sources(Paradigm p) {
        Map<String, Wl> sources = new LinkedHashMap<>();
        try {
            switch (p) {
                case PACKAGES -> {
                    var installed = CHR.Grk().BDm().NC(); // the package manager's packages
                    for (int i = 0; i < installed.MRY(); i++) {
                        if (installed.MRY(i) instanceof hgq pack && pack.E3()) { // installed or updatable
                            Wl source = Wc.cfM().cfM(pack.lYc()); // its browser source, if it has one
                            if (source != null) sources.putIfAbsent(name(source), source);
                        }
                    }
                }
                case COLLECTIONS -> {
                    add(sources, VF.lYc()); // Everything: the way back from a collection
                    add(sources, session.Bpn().MJs()); // Favourites
                    var collections = session.Bpn().BBT();
                    for (int i = 0; i < collections.MRY(); i++) add(sources, collections.MRY(i));
                }
                case KIND -> session.emh().FzB().forEach(o -> {
                    if (o instanceof Wl w && "SOUNDS".equals(w.TtY().name()) && !LEFT_OUT.contains(String.valueOf(w.cfM()))) {
                        sources.putIfAbsent(name(w), w);
                    }
                });
                default -> {
                }
            }
        } catch (Throwable t) {
            Store.LOG.log("pane: listing " + p.label + " failed", t);
        }
        return sources;
    }

    private static void add(Map<String, Wl> sources, Object o) {
        Wl source = o instanceof Wl w ? w : o instanceof GT c ? (Wl) c.tp2() : null;
        if (source != null) sources.putIfAbsent(name(source), source);
    }

    private static String name(Wl w) {
        try {
            String n = w.in_();
            return n == null || n.isEmpty() ? String.valueOf(w.cfM()) : n;
        } catch (Throwable t) {
            return String.valueOf(w.cfM());
        }
    }

    // ---------------------------------------------------------------------
    // Choosing
    // ---------------------------------------------------------------------

    /** The user picks a paradigm. */
    private void choose(Paradigm p) {
        try {
            if (p == chosen) return;
            show(p, false);
            Store.LOG.log("pane: " + p.label + " chosen");
        } catch (Throwable t) {
            Store.LOG.log("pane: choosing " + p.label + " failed", t);
        }
    }

    /** Shows the paradigm's list (filled when first shown); Locations browses Everything. Deferred during the build. */
    private void show(Paradigm p, boolean deferred) {
        Paradigm before = chosen;
        chosen = p;
        for (Map.Entry<Paradigm, dve> e : lists.entrySet()) {
            boolean on = e.getKey() == p;
            if (e.getValue().uev() != on) e.getValue().t_(on);
        }
        if (before != null && chosenFlags.containsKey(before)) chosenFlags.get(before).changed();
        if (chosenFlags.containsKey(p)) chosenFlags.get(p).changed();
        Runnable then = () -> {
            if (sourceLists.containsKey(p)) fill(p);
            showFilters();
        };
        if (deferred) later(100, then); // never during the browser's layout
        else then.run();
    }

    /** A row is chosen: its tab's filter is set to it (Everything clears Collections'). The other tabs' choices stay. */
    private void chooseRow(Paradigm p, Wl source, String name) {
        try {
            everything();
            TabFilter filter = TabFilter.of(session, p.tab);
            if (source.equals(VF.lYc())) filter.clear();
            else if (source != filter.chosen()) filter.choose(source, name);
            Store.LOG.log("pane: " + p.label + " " + name);
            for (SourceList list : sourceLists.values()) list.follow();
            showFilters();
        } catch (Throwable t) {
            Store.LOG.log("pane: choosing " + name + " failed", t);
        }
    }

    /**
     * The source was set outside the pane (All Sources, F1-F9): a source one of the pane's tabs
     * lists becomes that tab's choice, as if its row were chosen (the tab is shown, the source goes
     * back to Everything, the tab's filter and chip are set). A source no tab lists (File Browser,
     * rekordbox, a sub-source) stays as Bitwig set it. True when a tab took it.
     */
    private boolean adoptSource() {
        try {
            Wl now = session.BDm();
            if (now == null || now.equals(VF.lYc())) return false;
            for (Paradigm p : sourceLists.keySet()) {
                for (Map.Entry<String, Wl> e : sources(p).entrySet()) {
                    if (!e.getValue().equals(now)) continue;
                    if (chosen != p) show(p, false);
                    chooseRow(p, now, e.getKey());
                    Store.LOG.log("pane: the source " + e.getKey() + " was chosen outside the pane; taken as the " + p.label + " tab's row");
                    return true;
                }
            }
        } catch (Throwable t) {
            Store.LOG.log("pane: following a source chosen outside the pane failed", t);
        }
        return false;
    }

    /** The source is always Everything: the tabs are filters on it. */
    private void everything() {
        try {
            Wl all = VF.lYc();
            if (!all.equals(session.BDm())) {
                session.cfM(all);
                Store.LOG.log("pane: source Everything");
            }
        } catch (Throwable t) {
            Store.LOG.log("pane: setting the source Everything failed", t);
        }
    }

    // ---------------------------------------------------------------------
    // Width and safety
    // ---------------------------------------------------------------------

    /** The pane's stored width in layout units, or {@link #PANE_DEFAULT}. */
    private static long storedWidth() {
        try {
            long stored = Math.round(Double.parseDouble(Store.read(WIDTH)));
            if (stored >= PANE_MIN && stored <= PANE_MAX) return stored;
        } catch (NumberFormatException none) {
            // nothing stored yet
        }
        return PANE_DEFAULT;
    }

    /**
     * Once laid out: the browser panel widened if it is at Bitwig's starting width
     * ({@link #widenBrowser}), the pane's width set again, and both dividers watched. A drag of the
     * pane's divider stores the pane's width; once either divider stops, one line in the log gives
     * both columns' widths ({@link #logDrag}).
     *
     * <p>The splitter's position is the pane's width in layout units ({@code dVx.cfM},
     * SPLITTER_SETS_FIRST_SIZE: {@code dVz.IwV(double)} = {@code eLY((long) position)}, and a drag
     * stores {@code dVz.UNj(long)} = {@code TtY(width)}), so the pane keeps its width when the
     * browser panel's edge moves, and only the results change, as Bitwig's own panels keep theirs.
     * It is set with {@code dVC.Hnt(double)}, which the layout clamps ({@code dVz.hUR(long)}) but
     * doesn't change; the drag's setter {@code dVz.cfM(double)} would store the clamped width of a
     * panel that is still narrow. The position's listeners are {@code dVC.iHb(foz)}, fired as
     * {@code dVC.FzB(double)} sets it. dev.36-dev.60 set the pane's share instead
     * ({@code dVx.Hnt}, SPLITTER_SETS_FIRST_PROPORTION), so moving the panel's edge moved the
     * pane's divider with it.
     */
    private static void settle(dVz split, dve pane, dve results, dve whole) {
        try {
            widenBrowser(whole);
            long width = storedWidth();
            double now = split.SI();
            if (Math.abs(now - width) > 0.5) split.Hnt((double) width);
            Store.LOG.log("pane: width " + width + " layout units (the splitter was at " + now + ", now " + split.SI() + ")");
            logWidths("at 0.2 s", split, pane, results, whole);
            if (!WATCHED.add(split)) return;
            int[] moves = {0};
            Runnable moved = () -> {
                int move = ++moves[0];
                later(600, () -> {
                    if (move == moves[0]) logDrag(pane, results, whole);
                });
            };
            split.iHb(new foz() {
                @Override
                public void eventOccurred(Object event) {
                    long w = Math.max(PANE_MIN, Math.min(PANE_MAX, Math.round(split.SI())));
                    if (w != storedWidth()) Store.write(WIDTH, String.valueOf(w));
                    moved.run();
                }
            });
            Panel panel = Panel.of(whole);
            if (panel != null && WATCHED.add(panel.outer)) {
                panel.outer.iHb(new foz() {
                    @Override
                    public void eventOccurred(Object event) {
                        panel.storeDragged();
                        moved.run();
                    }
                });
            }
        } catch (Throwable t) {
            Store.LOG.log("pane: setting the width failed", t);
        }
    }

    /**
     * The browser panel's starting width. The panel's width is Bitwig's own, the position of the
     * panel splitter beside it ({@code akZ main_and_browser_splitter} in Single Large Monitor, built
     * by {@code aCU.MRY(akZ, aBW)}): a size in layout units ({@code dVx.MRY} with the browser
     * second; Panel Sides swaps the sides and flips it to {@code dVx.cfM} with the browser first,
     * and with separate panels the Browser is still that splitter's edge side), kept whatever the
     * window's width, saved per project in the document's view settings
     * ({@code <path>.splitter_pos}, bound to the splitter by {@code abo()}), with the node's default
     * ({@code ala.uWE()}: 222) in a project that has none. Bitwig keeps no width across projects,
     * so a panel exactly at that default, with both sides shown, gets the width the user last
     * dragged it to ({@link #BROWSER_WIDTH}; {@link #BROWSER_START} before any drag), kept within
     * the room the window has. It is set through the splitter's own setter
     * ({@code dVz.cfM(double)}, as a drag of the panel's edge), which writes the project's value. A
     * width set in the project is kept.
     */
    private static void widenBrowser(dve whole) {
        try {
            Panel panel = Panel.of(whole);
            if (panel == null) return;
            aCU outer = panel.outer;
            double now = outer.SI(), start = panel.start();
            long target = storedBrowserWidth();
            if (Math.abs(now - start) > 0.5 || Math.abs(target - start) <= 0.5) {
                Store.LOG.log("pane: the browser panel is " + now + " layout units (Bitwig's start " + start + "); kept");
                return;
            }
            if (outer.dEI() || outer.Z1C()) return; // a side collapsed: the setter would show it
            long room = outer.TtY(outer.TMF(IBe.cfM) - outer.eLY(outer.FFu()) - panel.other().TtY(IBe.cfM)); // the most the other side's minimum leaves
            if (room <= 0) return; // not laid out
            long width = Math.min(target, room);
            if (width <= start + 0.5 && target > start) {
                Store.LOG.log("pane: no room to widen the browser panel from " + now + " layout units (room " + room + "); kept");
                return;
            }
            SET_BY_PANE.put(outer, (double) width);
            outer.cfM((double) width);
            SET_BY_PANE.put(outer, outer.SI());
            Store.LOG.log("pane: the browser panel set from Bitwig's start " + start + " to " + outer.SI() + " layout units (the last width "
                    + target + ", room " + room + ")");
        } catch (Throwable t) {
            Store.LOG.log("pane: setting the browser panel's width failed", t);
        }
    }

    /** The browser panel's width the user last dragged it to, or {@link #BROWSER_START}. */
    private static long storedBrowserWidth() {
        try {
            long stored = Math.round(Double.parseDouble(Store.read(BROWSER_WIDTH)));
            if (stored >= 1 && stored <= BROWSER_MAX) return stored;
        } catch (NumberFormatException none) {
            // nothing stored yet
        }
        return BROWSER_START;
    }

    /**
     * The panel splitter whose position is the browser panel's width: the nearest left/right
     * {@code aCU} above the browser (a top/bottom one between them, such as Panel Sides' device
     * panel across the full width, is passed over), whose side holding the browser is the side
     * that keeps its size.
     */
    private record Panel(aCU outer, boolean browserFirst) {
        static Panel of(dve whole) {
            dve side = whole;
            for (dve p = whole.RbC(); p != null; side = p, p = p.RbC()) {
                if (!(p instanceof aCU a) || a.LzO() != IBe.cfM) continue;
                boolean first = side == a.Ktv();
                if (!first && side != a.d3()) return null;
                if (a.qFI() != (first ? dVx.cfM : dVx.MRY)) {
                    Store.LOG.log("pane: the browser panel's width isn't a size (" + a.qFI() + "); left as it is");
                    return null;
                }
                return new Panel(a, first);
            }
            Store.LOG.log("pane: no panel splitter beside the browser; its width is left as it is");
            return null;
        }

        /** Bitwig's starting width for the panel: the node's default ({@code aCU.MIO}, {@code ala.uWE()}). */
        double start() {
            return outer.MIO.uWE();
        }

        /** The other side: the main area. */
        dve other() {
            return browserFirst ? outer.d3() : outer.Ktv();
        }

        /**
         * The position changed: stored as the user's width unless it is the one
         * {@link #widenBrowser} set, Bitwig's default (a project without its own), or a side is
         * collapsed.
         */
        void storeDragged() {
            try {
                double now = outer.SI();
                Double set = SET_BY_PANE.get(outer);
                if (set != null && Math.abs(now - set) <= 0.5) return;
                if (Math.abs(now - start()) <= 0.5 || outer.dEI() || outer.Z1C()) return;
                long w = Math.round(now);
                if (w < 1 || w > BROWSER_MAX || w == storedBrowserWidth()) return;
                Store.write(BROWSER_WIDTH, String.valueOf(w));
                SET_BY_PANE.remove(outer);
            } catch (Throwable t) {
                Store.LOG.log("pane: storing the browser panel's width failed", t);
            }
        }
    }

    /**
     * One line once a divider stops: the pane's, the results' and the whole browser's widths in
     * layout units (the unit the pane's divider and Bitwig's panel width are stored in) and on
     * screen, for reading out widths to set as defaults.
     */
    private static void logDrag(dve pane, dve results, dve whole) {
        try {
            Store.LOG.log("pane: widths after a drag: sources " + units(pane) + ", results " + units(results) + ", browser " + units(whole));
        } catch (Throwable t) {
            Store.LOG.log("pane: logging the widths failed", t);
        }
    }

    /** A widget's laid-out width in layout units, with its screen pixels. */
    private static String units(dve w) {
        long px = w.TMF(IBe.cfM);
        return w.TtY(px) + " units (" + px + " px)";
    }

    /**
     * What the splitter gives each side. Its layout ({@code dVz.iK_()}) turns
     * the position into the pane's width only while it is attached ({@code dve.ZTl()}; 0
     * otherwise), then keeps that width between the pane's and the results' minimum and maximum
     * ({@code dVz.hUR(long)}: at least the pane's minimum and the rest less the results' maximum, at
     * most the pane's maximum and the rest less the results' minimum). Logged once, 0.2 s
     * after the build.
     */
    private static void logWidths(String when, dVz split, dve pane, dve results, dve whole) {
        try {
            Store.LOG.log("pane: widths " + when + ": splitter position " + split.SI() + ", attached " + split.ZTl()
                    + ", width " + width(split) + " (header and splitter " + width(whole) + "); pane " + sizes(pane)
                    + "; results " + sizes(results));
        } catch (Throwable t) {
            Store.LOG.log("pane: logging the widths failed", t);
        }
    }

    /** A widget's horizontal minimum, preferred and maximum width, and its laid-out width. */
    private static String sizes(dve w) {
        try {
            return "min " + w.TtY(IBe.cfM) + ", preferred " + w.fJR(IBe.cfM) + ", max " + w.iHb(IBe.cfM) + ", width " + width(w)
                    + (w.ZTl() ? "" : " (not attached)");
        } catch (Throwable t) {
            return "? (" + t + ")";
        }
    }

    private static String width(dve w) {
        try {
            return String.valueOf(w.TMF(IBe.cfM));
        } catch (Throwable t) {
            return "?";
        }
    }

    private static void later(long ms, Runnable task) {
        TIMER.schedule(new TimerTask() {
            @Override
            public void run() {
                Fq.cfM(task);
            }
        }, ms);
    }

    private static FE locationOf(Jp session) {
        for (Object filter : session.jHi()) {
            if (filter instanceof FE fe) return fe;
        }
        return null;
    }

    /**
     * A source as a browser result, so Bitwig's result row draws it: its name, and its icon as the
     * Quick Sources toolbar draws it. Without a source it is the list's root: the tree column casts
     * every node it draws to a result, the root included (its style shows the root).
     */
    private static final class SourceItem extends IW {
        final Wl source; // null for the root
        private final String name;

        SourceItem(Wl source, String name) {
            this.source = source;
            this.name = name;
            H_(); // finished, as Bitwig finishes its own
        }

        @Override
        public String LaE() {
            return name;
        }

        @Override
        public java.net.URI MIO() {
            try {
                return source == null ? null : source.hUR();
            } catch (Throwable t) {
                return null;
            }
        }

        @Override
        public java.io.File Grk() {
            return null;
        }

        @Override
        public com.bitwig.base.io.z2y tp2() {
            return null;
        }

        @Override
        public String Ucl() {
            return null;
        }

        @Override
        public RBf ERO() {
            return null;
        }
    }

    /**
     * A flat list of sources in Bitwig's tree column: a Location column ({@code cDO}, set up on the
     * session's Location filter as Bitwig sets up its own) whose rows, expansion and selection are
     * replaced, so Bitwig draws its compact rows, scrolls and highlights. Clicking a row (or moving
     * to it with the keys) chooses its source; the selection follows the browser's source. The
     * Location filter's value doesn't reach it, and the results' context menu, drag and file
     * commands are left out: the rows are sources, not folders or files.
     */
    private static final class SourceList extends cDO {
        private final java.util.function.Supplier<Wl> current;
        private final SourceItem root;
        private final Pick pick;
        private iW2 rows = new iW2(new Object[0]);

        SourceList(FE location, String label, java.util.function.Supplier<Wl> current, java.util.function.BiConsumer<Wl, String> choose) {
            this.current = current;
            this.root = new SourceItem(null, label);
            this.pick = new Pick(item -> choose.accept(item.source, item.name));
            MRY((tN) location, false); // as cDO.cfM(FE) sets up the Location column
            hUR.cfM(emO.MRY); // always open: one level
            hUR.cfM(pick);
            hUR.qoU(model());
            hideRoot();
            H_();
        }

        /** The rows, then the selection on the browser's source. */
        void set(List<SourceItem> items) {
            rows = new iW2(items);
            pick.quiet = true;
            try {
                hUR.qoU(model()); // a new model rebuilds the rows
            } finally {
                pick.quiet = false;
            }
            hideRoot();
            follow();
        }

        /** The Location column's style shows the root ("All Locations"); a flat list has none to show. */
        private void hideRoot() {
            try {
                hUR.TtY(false); // show_root
            } catch (Throwable t) {
                Store.LOG.log("pane: hiding the list's root failed", t);
            }
        }

        /**
         * A flat list: rows start at the left edge. The tree's style ({@code eiy}, the theme's tree
         * style) leaves room for an expand button and indents children; this list's tree gets a copy
         * without either ({@code should_have_expand_button}, {@code child_indentation}).
         */
        private void flatten() {
            try {
                eiy style = hUR.x3t();
                if (style == null) style = eiy.cfM(hUR); // the theme's tree style
                if (style == null || (!style.E3() && style.Z1C() == 0)) return; // already flat
                int indent = style.Z1C();
                boolean button = style.E3();
                eiy flat = style.IwV(); // a copy
                flat.MRY(false); // should_have_expand_button
                flat.MRY(0); // child_indentation
                hUR.Hnt(flat);
                if (!flattened) Store.LOG.log("pane: list rows flat (the tree style had indentation " + indent + ", expand button " + button + ")");
                flattened = true;
            } catch (Throwable t) {
                Store.LOG.log("pane: making the list flat failed", t);
            }
        }

        private boolean flattened;

        /** Selects the row of the tab's choice (Everything in Collections while it has none; else no row). */
        void follow() {
            hideRoot();
            flatten();
            pick.quiet = true;
            try {
                Wl now = current.get();
                if (now == null) now = VF.lYc();
                emR found = null;
                for (int i = 0; i < rows.MRY(); i++) {
                    if (rows.MRY(i) instanceof SourceItem item && item.source.equals(now)) {
                        found = new emR(new emR(root), item);
                        break;
                    }
                }
                emR current = (emR) pick.QNk().getValue();
                if (found == null ? current == null : found.equals(current)) return;
                pick.ugU();
                if (found != null) {
                    pick.IwV(found);
                    pick.h_(found);
                }
            } catch (Throwable t) {
                Store.LOG.log("pane: following the source failed", t);
            } finally {
                pick.quiet = false;
            }
        }

        private KIt model() {
            iW2 children = rows;
            return new KIt() {
                @Override
                public Object MRY() {
                    return root;
                }

                @Override
                public omW FzB(Object node) {
                    return node == root ? children : iW2.cfM;
                }

                @Override
                public boolean Hnt(Object node) {
                    return node != root; // sources are leaves
                }

                @Override
                public boolean MRY(Object node) {
                    return false;
                }
            };
        }

        @Override
        public wt JJY() {
            return null; // no result is selected here
        }

        @Override
        public List pYg() {
            return List.of(); // nothing for the results' file commands
        }

        @Override
        public void cfM(dKW menu, List items) {
            // no results' context menu on sources
        }

        @Override
        public void cfM(List items, eoh drag) {
            // sources aren't dragged
        }

        @Override
        public void cfM(tJ value) {
            // the Location filter's value isn't this list's selection
        }

        @Override
        public void IwV(tJ value) {
            // nor is a node chosen here the filter's value
        }

        @Override
        public void nl_() {
            // no file command on sources
        }
    }

    /** The list's selection: a row the user selects chooses its source. */
    private static final class Pick extends emb {
        private final Consumer<SourceItem> chosen;
        boolean quiet;

        Pick(Consumer<SourceItem> chosen) {
            this.chosen = chosen;
        }

        @Override
        public void Hnt(emR path) {
            super.Hnt(path);
            if (quiet || path == null || !(path.MRY() instanceof SourceItem item) || item.source == null) return;
            try {
                chosen.accept(item);
            } catch (Throwable t) {
                Store.LOG.log("pane: choosing a source failed", t);
            }
        }
    }

    /** A boolean value the framework holds, for a widget to show and set; listeners get Bitwig's change event. */
    private static final class Flag implements AQw {
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;
        private final List<foz> listeners = new ArrayList<>();
        private boolean last;

        Flag(BooleanSupplier get, Consumer<Boolean> set) {
            this.get = get;
            this.set = set;
            this.last = get.getAsBoolean();
        }

        @Override
        public boolean getBoolValue() {
            return get.getAsBoolean();
        }

        @Override
        public void setBoolValue(boolean on) {
            set.accept(on);
            changed(); // the widget shows the value in effect
        }

        @Override
        public Object getValue() {
            return getBoolValue();
        }

        @Override
        public void setValue(Object value) {
            setBoolValue(Boolean.TRUE.equals(value));
        }

        @Override
        public boolean isSettable() {
            return true;
        }

        @Override
        public boolean isObservable() {
            return true;
        }

        @Override
        public void addListener(foz listener) {
            if (listener != null && !listeners.contains(listener)) listeners.add(listener);
        }

        @Override
        public void removeListener(foz listener) {
            listeners.remove(listener);
        }

        /** Tells the listeners the value in effect (Bitwig's change event, old and new). */
        void changed() {
            boolean now = getBoolValue();
            boolean before = last;
            last = now;
            for (foz listener : List.copyOf(listeners)) {
                try {
                    listener.eventOccurred(new VGL(this, before, now));
                } catch (Throwable t) {
                    Store.LOG.log("pane: a value listener failed", t);
                }
            }
        }
    }
}
