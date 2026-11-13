package bml.pre5browser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import bitwig.obf.BY; // the Device Type filter
import bitwig.obf.Cb; // a device type, as a value of the Device Type filter
import bitwig.obf.DC; // a browser filter
import bitwig.obf.DJ; // a query's per-result step: which filters take a result, and what each counts
import bitwig.obf.Dt; // a list filter (one or more values chosen, or Any)
import bitwig.obf.M22; // a query
import bitwig.obf.Os; // a result with a device type
import bitwig.obf.Ow; // the query for one file kind
import bitwig.obf.Ph; // a result that is a file of some kind
import bitwig.obf.pAe; // a result, as a query sees it
import bitwig.obf.qD; // a file kind
import bitwig.obf.qF; // a device type
import bitwig.obf.zvZ; // a query matching any of its parts

/**
 * Samples and Other as two more values of Bitwig's Device Type filter ({@code BY}), so the pane's
 * type column is Bitwig's own filter column ({@code cDp}) with Bitwig's rows, counts, Any and
 * chip: Any, Instrument, Audio FX, Note FX, Samples, Other. Only for the side browser while the
 * pane is in effect ({@link PaneHost#deviceTypeApplies}); everywhere else Device Type is Bitwig's.
 * <ul>
 *   <li><b>Samples:</b> audio files, Bitwig's Samples and Music kinds (not impulses, wavetables,
 *       multi-samples or sliced audio, which have kinds of their own).</li>
 *   <li><b>Other:</b> every file kind that is neither a sample kind nor a device kind (devices,
 *       plug-ins and their presets, which the device type rows cover).</li>
 * </ul>
 * <p>A list filter counts as Bitwig's query steps through the results ({@code DJ.cfM(pAe)}, for each
 * file): each filter is asked whether it takes the result ({@code DC.cfM(pAe)}; Device Type takes
 * results with a device type), and once the result is known to pass the other filters, each
 * filter that took it counts it ({@code DJ.MRY()}: {@code DC.RDJ()}, then {@code BY.hS_()}, which
 * counts it under its device type's index, {@code Dt.UNj(int)}). Device Type also takes a result
 * without a device type that is a sample or another kind: it is added to the filters that took
 * the result ({@code DJ.Hnt[i]} and the step's list, before Bitwig checks the list is empty), and
 * {@code hS_} counts it under {@link #SAMPLES} or {@link #OTHER} (its three calls that read the
 * device type are answered for it). After the query Bitwig makes a value for each counted index it has none for
 * ({@code BY.MRY(int, int)}, which knows only device types): {@link Row} for these two. A row's
 * query is its file kinds ({@code Ow.cfM(qD)}, as the file-kind filter's values), so choosing it
 * narrows the results as a device type does. The counts are Bitwig's: each value counts the
 * results narrowed by the other filters, and Any counts every result the filter took.
 */
final class TypeRows {
    /** The rows' value indices, past Bitwig's device types. */
    static final int SAMPLES = 100, OTHER = 101;

    /** Audio files: Bitwig's file kinds for samples and music (wav, aiff, flac, mp3, ...). */
    private static final Set<String> SAMPLE_KINDS = Set.of("SAMPLES", "MUSIC");
    /** The file kinds whose items have a device type: the device type rows cover them. */
    private static final Set<String> DEVICE_KINDS = Set.of("DEVICES", "PLUGINS", "PRESETS", "PLUGIN_PRESETS");

    /**
     * For each Device Type filter, the row it took its current result for, or -1 (Bitwig's own, or
     * not taken). Per filter, not per thread: one pass over the results steps each session's query
     * in turn ({@code DJ.cfM(pAe)} for one session, then another's, then the counting), so a
     * thread-wide mark was reset by the next session's step before {@code hS_} counted (dev.40:
     * samples were counted under the last device type read, nearly all Instrument).
     */
    private static final Map<BY, int[]> TAKEN = new WeakHashMap<>();
    /** The mark of the filter counting on this thread ({@code BY.hS_()}, from its entry). */
    private static final ThreadLocal<int[]> COUNTING = ThreadLocal.withInitial(() -> new int[] {-1});
    /** This thread's current query step and result ({@code DJ.cfM(pAe)}). */
    private static final ThreadLocal<Object[]> STEP = ThreadLocal.withInitial(() -> new Object[2]);
    /** The index and count Bitwig makes a value for on this thread ({@code BY.MRY(int, int)}). */
    private static final ThreadLocal<int[]> MAKING = ThreadLocal.withInitial(() -> new int[2]);
    /** Whether each Device Type filter is the side browser's while the pane is in effect (asked once per filter: it is asked for every result). */
    private static final Map<BY, Boolean> APPLIES = new WeakHashMap<>();

    private static boolean logged;

    /**
     * The pane's type columns, drawn without counts. The rows' counts were wrong (dev.40-41: with
     * {@code kick}, Instrument 4748 of Any 4774, Samples 2): Bitwig itself counts most audio files under
     * a device type (file results with one, {@code Op}: {@code PO}, {@code QB}, {@code QX}, {@code Re}),
     * which these rows don't reach. The user: "If the counts can't be fixed, we should turn them
     * off, they're not important anyway."
     */
    private static final Set<Object> UNCOUNTED = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    /** True while a list column of {@link #UNCOUNTED} adds a row's count (UI thread). */
    private static boolean drawingUncounted;

    static void withoutCounts(Object column) {
        synchronized (UNCOUNTED) {
            UNCOUNTED.add(column);
        }
    }

    /** A list column adds a row's widgets ({@code cFm.MRY(wt, dWb)}: the count label). */
    static void addingCount(Object column) {
        synchronized (UNCOUNTED) {
            drawingUncounted = UNCOUNTED.contains(column);
        }
    }

    /** Whether the count label being made is hidden. */
    static boolean leaveOutCount() {
        return drawingUncounted;
    }

    private TypeRows() {
    }

    /** A value for Samples or Other: Bitwig's device type value, with its own name, icon and query. */
    static final class Row extends Cb {
        private final String icon;
        private M22 query;

        Row(String name, String icon, int index, int count, Dt filter) {
            this.icon = icon;
            cfM(name, index, count, filter); // a value's name, index, count and filter (Dw), as Cb's own
            E3();
        }

        @Override
        public M22 lYc() {
            if (query == null) {
                List<M22> kinds = new ArrayList<>();
                for (qD kind : qD.values()) {
                    if (rowOf(kind) == rGR()) kinds.add(Ow.cfM(kind));
                }
                query = zvZ.cfM((Collection<M22>) kinds); // any of them (the Collection overload: a List would pick M22's own cfM(List))
            }
            return query;
        }

        @Override
        public String Ucl() { // its icon, by name
            return icon;
        }
    }

    /** The row a file kind is counted under: {@link #SAMPLES}, {@link #OTHER}, or -1 for a device kind. */
    static int rowOf(qD kind) {
        String name = kind.name();
        if (SAMPLE_KINDS.contains(name)) return SAMPLES;
        return DEVICE_KINDS.contains(name) ? -1 : OTHER;
    }

    /** A value in effect changed (the side pane, or the active view): whether the pane applies is asked again. */
    static void featuresChanged() {
        synchronized (APPLIES) {
            APPLIES.clear();
        }
    }

    private static boolean applies(BY filter) {
        synchronized (APPLIES) {
            Boolean known = APPLIES.get(filter);
            if (known == null) {
                known = PaneHost.deviceTypeApplies(filter.zpW());
                APPLIES.put(filter, known);
            }
            return known;
        }
    }

    /** A query step starts on a result. */
    static void step(DJ step, pAe result) {
        Object[] s = STEP.get();
        s[0] = step;
        s[1] = result;
    }

    private static int[] mark(BY filter) {
        synchronized (TAKEN) {
            return TAKEN.computeIfAbsent(filter, f -> new int[] {-1});
        }
    }

    /**
     * The step asks whether any filter took the result ({@code taken.isEmpty()}): Device Type also
     * takes a sample or another kind, where its rows apply, to count it under its row.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static boolean noneTook(List taken) {
        Object[] s = STEP.get();
        DJ step = (DJ) s[0];
        Object result = s[1];
        s[0] = s[1] = null;
        if (step != null && result instanceof Ph file) {
            DC[] filters = step.MRY; // the query's filters
            boolean[] took = step.Hnt; // which of them took the result
            for (int i = 0; i < filters.length; i++) {
                if (!(filters[i] instanceof BY types) || types instanceof TabFilter) continue; // Device Type, not a tab's filter
                if (!applies(types)) break; // not the side pane's
                int[] mark = mark(types);
                mark[0] = -1;
                if (took[i] || types.gfw()) break; // taken as Bitwig's, or not counting
                qD kind = file.SQN();
                int row = kind == null ? -1 : rowOf(kind);
                if (row < 0) break;
                took[i] = true;
                taken.add(types);
                mark[0] = row;
                break;
            }
        }
        return taken.isEmpty();
    }

    /** {@code BY.hS_()} starts counting a result: its calls below read this filter's mark. */
    static void counting(BY filter) {
        int[] mark;
        synchronized (TAKEN) {
            mark = TAKEN.get(filter);
        }
        int[] now = COUNTING.get();
        now[0] = mark == null ? -1 : mark[0];
        if (mark != null) mark[0] = -1; // counted once
    }

    /**
     * {@code hS_} reads the result's device type: none to read for a row's result. A result with a
     * device type's interface but none set ({@code Os.HL()} null: seen with BY KIND Samples + Clips
     * in a loop package, pane31) is counted under Other: Bitwig's {@code hS_} would count it under
     * {@code qF.cfM()} of null and crash (dev.49 crashed in {@link #index}). Bitwig never counts
     * Device Type in Everything; the side pane makes it count there.
     */
    static qF type(Os result) {
        int[] row = COUNTING.get();
        if (row[0] >= 0) return qF.values()[0];
        qF type = result == null ? null : result.HL();
        if (type != null) return type;
        row[0] = OTHER; // no device type: counted under Other, and no other device types read
        return qF.values()[0];
    }

    /** {@code hS_} counts under the device type's index: the row's index for a row's result. */
    static int index(qF type) {
        int row = COUNTING.get()[0];
        return row >= 0 ? row : type == null ? OTHER : type.cfM();
    }

    /** {@code hS_} reads the result's other device types: none for a row's result. */
    static int others(Os result) {
        int[] row = COUNTING.get();
        if (row[0] < 0) return result.MJ();
        row[0] = -1;
        return 0;
    }

    static void making(int index, int count) {
        int[] m = MAKING.get();
        m[0] = index;
        m[1] = count;
    }

    /**
     * {@code BY.MRY(int, int)} looks up the index's device type ({@code qF.cfM(int)}) and returns
     * null without one: a row's index gets a stand-in, so Bitwig goes on to make the value
     * ({@link #value}). dev.39 wrapped the method's return, which misses that early null: Bitwig
     * added a null value and crashed sorting its values.
     */
    static qF lookup(int index) {
        return index == SAMPLES || index == OTHER ? qF.values()[0] : qF.cfM(index);
    }

    /** {@code BY.MRY(int, int)} makes its value ({@code Cb.cfM(qF, int, Dt)}): a row's value for a row's index. */
    static Cb value(qF type, int count, Dt filter) {
        int index = MAKING.get()[0];
        if (index != SAMPLES && index != OTHER) return Cb.cfM(type, count, filter);
        Row row = index == SAMPLES ? new Row("Samples", "browser/audio_file.svg", SAMPLES, count, filter)
                : new Row("Other", "browser/file_type.svg", OTHER, count, filter);
        row.H_();
        if (!logged) Store.LOG.log("pane: type rows Samples and Other added to Device Type");
        logged = true;
        return row;
    }

    /** Bitwig's order of device types ({@code BY.IwV()}), then Samples, then Other. */
    private static Comparator<Object> order;

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Comparator order(Comparator bitwig) {
        if (order == null) {
            Comparator<Object> types = bitwig;
            order = (a, b) -> {
                boolean ra = a instanceof Row, rb = b instanceof Row;
                if (!ra && !rb) return types.compare(a, b);
                if (ra && rb) return Integer.compare(((Row) a).rGR(), ((Row) b).rGR());
                return ra ? 1 : -1;
            };
        }
        return order;
    }
}
