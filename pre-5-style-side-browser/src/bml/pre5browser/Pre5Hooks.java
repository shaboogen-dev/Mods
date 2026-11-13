package bml.pre5browser;

import java.util.Comparator;
import java.util.List;

import bitwig.obf.BY; // the Device Type filter
import bitwig.obf.DC; // a browser filter
import bitwig.obf.Jp; // a browser session

import bml.api.At;
import bml.api.Hook;
import bml.api.Return;
import bml.api.This;

/**
 * Where the pane hooks into Bitwig: the side session's filters (Device Type and the tabs' filters
 * join them while the view is active) and the type column's rows (Samples and Other as values of
 * Device Type, without counts). The pane itself is placed through Framework: Browser's results slot.
 * Every hook contains its own failures and falls back to Bitwig's behaviour.
 */
public final class Pre5Hooks {
    /** The session building its filter list, for the list below. */
    private static final ThreadLocal<Jp> FILTER_LIST = new ThreadLocal<>();

    private Pre5Hooks() {
    }

    /** A session builds its filter list (its filter sections, relevance and queries come from it). */
    @Hook(target = Jp.class, method = "zpW", at = At.ENTRY, id = "note-filter-list-session")
    public static void filterListStarts(@This Jp session) {
        FILTER_LIST.set(session);
    }

    /** The filter list: while the view is active, the side browser's (its configuration's) also has Device Type and the tabs' filters. */
    @Hook(target = Jp.class, method = "zpW", at = At.WRAP_RETURN, id = "device-type-filter")
    @SuppressWarnings("rawtypes")
    public static List filterList(@Return List filters) {
        Jp session = FILTER_LIST.get();
        FILTER_LIST.remove();
        try {
            return PaneHost.withDeviceType(session, filters);
        } catch (Throwable t) {
            Store.LOG.log("adding Device Type to the filters failed", t);
            return filters;
        }
    }

    /**
     * A filter section's relevance is updated ({@code DC.x3t()} sets it from {@code DC.hW_()}): the
     * side browser's Device Type is relevant while the view is active. Bitwig's answer is no for
     * every source (no source says yes, and the kinds rule fails for Everything), and a filter that
     * isn't relevant adds nothing to the query ({@code DC.wYK()}, {@code Jp.ifN()}) and isn't counted.
     */
    @Hook(target = DC.class, method = "x3t()V", at = At.REPLACE_CALL, callOwner = DC.class, call = "hW_()Z",
          id = "device-type-relevant")
    public static boolean filterRelevant(DC filter) {
        try {
            if (PaneHost.deviceTypeRelevant(filter)) return true;
        } catch (Throwable t) {
            Store.LOG.log("making Device Type relevant failed", t);
        }
        return filter.hW_();
    }

    // The type rows: Samples and Other as values of Device Type (TypeRows).

    /** A query step starts on a result. */
    @Hook(target = bitwig.obf.DJ.class, method = "cfM(LpAe;)LsTA;", at = At.ENTRY, id = "type-rows-step")
    public static void typeRowsStep(@This bitwig.obf.DJ step, @bml.api.Arg(0) bitwig.obf.pAe result) {
        TypeRows.step(step, result);
    }

    /** The step asks whether any filter took the result: Device Type also takes samples and other kinds. */
    @Hook(target = bitwig.obf.DJ.class, method = "cfM(LpAe;)LsTA;", at = At.REPLACE_CALL, callOwner = List.class,
          call = "isEmpty", occurrence = 1, id = "type-rows-take")
    @SuppressWarnings("rawtypes")
    public static boolean typeRowsTake(List taken) {
        try {
            return TypeRows.noneTook(taken);
        } catch (Throwable t) {
            Store.LOG.log("taking a result for the type rows failed", t);
            return taken.isEmpty();
        }
    }

    /** A list column adds a row's count label: noted, so the pane's type column can leave it out. */
    @Hook(target = bitwig.obf.cFm.class, method = "MRY(Lwt;LdWb;)V", at = At.ENTRY, id = "type-column-count-row")
    public static void typeColumnCountRow(@This bitwig.obf.cFm column) {
        TypeRows.addingCount(column);
    }

    /**
     * The count label is made ({@code cDU.ifN()}): hidden in the pane's type column. (The call that
     * adds it to the row, {@code dWb.HL}, is Projects in Browser's to replace; two mods can only
     * share a replaced call when it is static.)
     */
    @Hook(target = bitwig.obf.cFm.class, method = "MRY(Lwt;LdWb;)V", at = At.REPLACE_CALL, callOwner = bitwig.obf.cDU.class,
          call = "ifN", occurrence = 1, id = "type-column-no-count")
    public static bitwig.obf.cDU typeColumnNoCount() {
        bitwig.obf.cDU label = bitwig.obf.cDU.ifN();
        if (TypeRows.leaveOutCount()) label.t_(false);
        return label;
    }

    /** Device Type starts counting a result: a row's result, or Bitwig's. */
    @Hook(target = BY.class, method = "hS_", at = At.ENTRY, id = "type-rows-counting")
    public static void typeRowsCounting(@This BY types) {
        TypeRows.counting(types);
    }

    /** Device Type counts a result: its device type (none to read for a row's result). */
    @Hook(target = BY.class, method = "hS_", at = At.REPLACE_CALL, callOwner = bitwig.obf.Os.class, call = "HL",
          occurrence = 1, id = "type-rows-type")
    public static bitwig.obf.qF typeRowsType(bitwig.obf.Os result) {
        return TypeRows.type(result);
    }

    /** Device Type counts a result under its device type's index: a row's result under the row's. */
    @Hook(target = BY.class, method = "hS_", at = At.REPLACE_CALL, callOwner = bitwig.obf.qF.class, call = "cfM()I",
          occurrence = 1, id = "type-rows-index")
    public static int typeRowsIndex(bitwig.obf.qF type) {
        return TypeRows.index(type);
    }

    /** Device Type counts a result's other device types: none for a row's result. */
    @Hook(target = BY.class, method = "hS_", at = At.REPLACE_CALL, callOwner = bitwig.obf.Os.class, call = "MJ",
          occurrence = 1, id = "type-rows-others")
    public static int typeRowsOthers(bitwig.obf.Os result) {
        return TypeRows.others(result);
    }

    /** Device Type makes a value for a counted index. */
    @Hook(target = BY.class, method = "MRY(II)LDw;", at = At.ENTRY, id = "type-rows-making")
    public static void typeRowsMaking(@bml.api.Arg(0) int index, @bml.api.Arg(1) int count) {
        TypeRows.making(index, count);
    }

    /**
     * Device Type looks up the index's device type, and returns null early when there is none
     * (which a return hook doesn't see: it wraps only the final return). A row's index finds a
     * stand-in, so Bitwig goes on to make the value.
     */
    @Hook(target = BY.class, method = "MRY(II)LDw;", at = At.REPLACE_CALL, callOwner = bitwig.obf.qF.class,
          call = "cfM(I)LqF;", occurrence = 1, id = "type-rows-lookup")
    public static bitwig.obf.qF typeRowsLookup(int index) {
        return TypeRows.lookup(index);
    }

    /** Device Type makes its value ({@code Cb.cfM(qF, int, Dt)}): a row's value for a row's index. */
    @Hook(target = BY.class, method = "MRY(II)LDw;", at = At.REPLACE_CALL, callOwner = bitwig.obf.Cb.class,
          call = "cfM(LqF;ILDt;)LCb;", occurrence = 1, id = "type-rows-value")
    public static bitwig.obf.Cb typeRowsValue(bitwig.obf.qF type, int count, bitwig.obf.Dt filter) {
        return TypeRows.value(type, count, filter);
    }

    /** Device Type's order of values: Bitwig's for device types, then Samples, then Other. */
    @Hook(target = BY.class, method = "IwV()Ljava/util/Comparator;", at = At.WRAP_RETURN, id = "type-rows-order")
    @SuppressWarnings("rawtypes")
    public static Comparator typeRowsOrder(@Return Comparator order) {
        return TypeRows.order(order);
    }
}
