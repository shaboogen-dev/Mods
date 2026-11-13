package bml.pre5browser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import bitwig.obf.BY; // the Device Type filter (a list filter with Bitwig's object-model plumbing)
import bitwig.obf.Cb; // a Device Type value
import bitwig.obf.Dw; // a list filter's value
import bitwig.obf.Jp; // a browser session
import bitwig.obf.M22; // a query
import bitwig.obf.Ow; // the query for one file kind
import bitwig.obf.Wl; // a browser source
import bitwig.obf.pAe; // a result, as a query sees it
import bitwig.obf.qD; // a file kind
import bitwig.obf.rXv; // a query matching all of its parts
import bitwig.obf.yQ; // a list filter's Any value
import bitwig.obf.zvZ; // a query matching any of its parts

/**
 * A side pane tab's choice as a filter of the side session: Package, Collection or Kind. The
 * source stays Everything; each tab's choice is a value of its own filter, whose query is the
 * chosen source's own ({@code Wl.cfM(EE)}), so the tabs combine as Bitwig combines filters (each
 * must match; Location and the type column too) and each shows Bitwig's chip, whose X clears it.
 *
 * <p>Bitwig's list filters are built on its object model, so this is Device Type's class
 * ({@code BY}) with its own id, values and order, and no counting: it never takes a result
 * ({@code cfM(pAe)} false), so it counts nothing and shows no counts. Its values are made when
 * first chosen. It is added to the side session's filter list and made relevant as Device Type is
 * ({@link PaneHost#withDeviceType}, {@link PaneHost#deviceTypeRelevant}), and is offered and
 * relevant from the start, so it joins the session's filter sections, which the query is built
 * from. It isn't one of the
 * session's saved fields, so a saved project keeps only its source (Everything) and opens on it
 * with or without the framework.
 */
final class TabFilter extends BY {
    /** A tab with a filter: its id (the filter's), the icon its values show by name, and its title. */
    enum Tab {
        PACKAGE("bml_package", "browser/package.svg", "Package"),
        COLLECTION("bml_collection", "browser/user_collection.svg", "Collection"),
        KIND("bml_kind", "browser/category.svg", "Kind");

        final String id, icon, title;

        Tab(String id, String icon, String title) {
            this.id = id;
            this.icon = icon;
            this.title = title;
        }
    }

    /** Past Bitwig's device type indices. */
    private static final int FIRST_INDEX = 1000;

    private static final Map<Jp, Map<Tab, TabFilter>> FILTERS = new WeakHashMap<>();

    private final Jp session;
    private final Tab tab;
    private final Map<Wl, Value> values = new IdentityHashMap<>();
    private boolean observed;

    private TabFilter(Jp session, Tab tab) {
        this.session = session;
        this.tab = tab;
        IwV(session); // the session, as Device Type's own set-up
        UZr();
        H_(); // finish setting up
        // Offered and relevant from the start. The session queries only its filter sections
        // (Jp.jHi(): the filter list kept where DD.EUG(), offered, is true, at each Jp.MYI()), and
        // refreshes relevance (DC.x3t()) only for filters already in them; Bitwig sets both for its
        // own filters. dev.44 set neither: the chips showed, and the filters were never queried.
        UNj(true); // offered
        Hnt(true); // relevant
    }

    /** The session's filter for a tab, made once. */
    static TabFilter of(Jp session, Tab tab) {
        synchronized (FILTERS) {
            Map<Tab, TabFilter> tabs = FILTERS.computeIfAbsent(session, s -> new java.util.EnumMap<>(Tab.class));
            return tabs.computeIfAbsent(tab, t -> new TabFilter(session, t));
        }
    }

    /** The session's tab filters made so far. */
    static List<TabFilter> all(Jp session) {
        synchronized (FILTERS) {
            Map<Tab, TabFilter> tabs = FILTERS.get(session);
            return tabs == null ? List.of() : new ArrayList<>(tabs.values());
        }
    }

    /** Chooses only this source (the tab's choice); the results narrow to it. */
    void choose(Wl source, String name) {
        if (!observed) {
            observed = true;
            ejd(); // the session queries with a filter while it is observed
        }
        Value v = values.get(source);
        if (v == null) {
            v = new Value(source, name, FIRST_INDEX + values.size(), this);
            v.H_();
            values.put(source, v);
            Hnt((Dw) v); // one more value
        }
        Hnt((bitwig.obf.tB) v); // only this one chosen
        // Choosing a value this way doesn't run the value-changed step (Dt.Hnt(tB) hands the change
        // to a delegate, Dt.Ucl, which Bitwig sets only for its own filters); run it (hU_, below).
        hU_();
        try {
            M22 part = wYK(); // the filter's part of the query
            Store.LOG.log("pane: " + tab.id + " = " + name + " (offered " + EUG() + ", relevant " + ERO().getBoolValue()
                    + ", in the sections " + session.jHi().contains(this) + ", query part " + (part == null ? "none" : part.getClass().getSimpleName())
                    + ", value's " + (v.lYc() == null ? "none" : v.lYc().getClass().getSimpleName()) + ")");
        } catch (Throwable t) {
            Store.LOG.log("pane: reading " + tab.id + " failed", t);
        }
    }

    /** The tab's choice cleared (Any). */
    void clear() {
        MIO(); // runs the value-changed step (hU_, below), as the chip's X does
    }

    /**
     * The value-changed step, which a choice, Any and the chip's X all run. Bitwig's
     * ({@code DC.hU_()}) asks the session for a new query ({@code Jp.IwV(DC)} -> {@code DxJ()} ->
     * {@code Jp.FPW()}) only for a filter with a parent in Bitwig's object model ({@code K_()}: the
     * session, for the filters that are its fields); a tab filter isn't a field and has none, so the
     * step returned without a query (dev.44-47: the filter's part was in the session's query, but no
     * new query ran). It asks the session itself, while observed ({@code iAj()}, as Bitwig's step
     * requires).
     */
    @Override
    public void hU_() {
        super.hU_();
        if (K_() == null && iAj()) session.IwV(this); // the session's response to a filter's change: a new query
    }

    /** The chosen source, or null. */
    Wl chosen() {
        for (Object o : E3()) { // the chosen values
            if (o instanceof Value v) return v.source;
        }
        return null;
    }

    Tab tab() {
        return tab;
    }

    /** A value: one source of the tab, with the source's own query. */
    static final class Value extends Cb {
        final Wl source;
        private final String icon;
        private final Jp session;
        private M22 query;

        Value(Wl source, String name, int index, TabFilter filter) {
            this.source = source;
            this.icon = filter.tab.icon;
            this.session = filter.session;
            cfM(name, index, 0, filter); // a value's name, index, count and filter (Dw), as Cb's own
            E3();
        }

        @Override
        public M22 lYc() {
            if (query == null) query = query(source, session);
            return query;
        }

        @Override
        public String Ucl() { // its icon, by name
            return icon;
        }
    }

    /**
     * A source's query, as the session browses it: the source's own ({@code Wl.cfM(EE)}), and its
     * file kinds where it allows only some ({@code Wl.wGc()}: a one-kind source's file kind,
     * {@code Wz.eLY()}; a kind group's for the others). A source of file kinds (Impulses,
     * Multi-samples, Wavetables, All Presets, Templates, Projects, ...) has no query of its own
     * ({@code j2r}, which matches everything): its file kinds are all it is. dev.47 read them from
     * {@code Wl.cfM(Dt)}, a default that answers nothing for these sources (pane30 log:
     * Multi-samples' value read {@code j2r}).
     */
    private static M22 query(Wl source, Jp session) {
        M22 own = source.cfM(session.Bpn());
        List<M22> kinds = new ArrayList<>();
        Set<?> allowed = source.wGc(); // the source's file kinds
        if (allowed != null && !allowed.isEmpty() && allowed.size() < qD.values().length) {
            for (Object o : allowed) {
                if (o instanceof qD kind) kinds.add(Ow.cfM(kind));
            }
        }
        if (kinds.isEmpty()) return own;
        M22 anyKind = kinds.size() == 1 ? kinds.get(0) : zvZ.cfM((Collection<M22>) kinds);
        if (own == null || own == bitwig.obf.j2r.cfM()) return anyKind; // no query of its own
        List<M22> both = new ArrayList<>();
        both.add(own);
        both.add(anyKind);
        return rXv.cfM((Collection<M22>) both);
    }

    // Device Type's behaviour that doesn't apply to a tab filter.

    @Override
    public String Ucl() { // the filter's id
        return tab.id;
    }

    // Bitwig reads these two texts from its localization files by the filter's id
    // (filter_section.<id>.title, filter_column.<id>.any), which have none for a tab's.

    @Override
    public String iCS() { // the filter's title
        return tab.title;
    }

    @Override
    public yQ emh() { // the Any value
        return yQ.cfM("Any " + tab.title, this, "browser/any.svg");
    }

    @Override
    public boolean cfM(pAe result) { // takes no result: counts nothing
        return false;
    }

    @Override
    public void hS_() { // counts nothing
    }

    @Override
    public Dw MRY(int index, int count) {
        for (Value v : values.values()) {
            if (v.rGR() == index) return v;
        }
        return null;
    }

    @Override
    public int MRY(Object value) {
        return -1;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Set cfM() { // no device categories
        return Collections.emptySet();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Comparator IwV() { // in the order chosen
        Comparator<Object> byIndex = Comparator.comparingInt(o -> o instanceof Dw d ? d.rGR() : 0);
        return byIndex;
    }
}
