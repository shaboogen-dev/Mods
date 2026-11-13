import bitwig.obf.cCk; // a browser filter's header
import bitwig.obf.cCV; // a browser context
import bitwig.obf.cDj; // the Everything result list
import bitwig.obf.cFm; // builds a browser result row
import bitwig.obf.cFV; // the browser panel
import bitwig.obf.dve; // a widget
import bitwig.obf.dWb; // a row of widgets
import bitwig.obf.eoh; // a drag payload
import bitwig.obf.Jp; // a browser session
import bitwig.obf.pPb; // a list model
import bitwig.obf.tE; // a browser filter
import bitwig.obf.wt; // a browser result
import bitwig.obf.wz; // a result model provider
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.This;
import com.bitwig.flt.document.core.iface.pHd; // a document
import com.bitwig.flt.document_view_settings.hgq; // view settings
import java.util.List;

/**
 * Shows project files in the Everything browser: a Project filter, project drill-in with the
 * project's track hierarchy, and drag and drop of project contents. The Projects file kind itself
 * (Everything showing project files, the Projects kind and its icons) is added through Framework:
 * Browser ({@link ProjectsKind}). The hooks below hand Bitwig's objects to {@link ProjectDrill} and
 * {@link BrowserProjectFilter}.
 */
public final class ProjectsInBrowser {
    private static final ThreadLocal<Jp> FILTER_SESSION = new ThreadLocal<>();
    private static final ThreadLocal<cDj> RESULT_OWNER = new ThreadLocal<>();
    private static final ThreadLocal<RowContext> PROJECT_ROW = new ThreadLocal<>();
    private static final ThreadLocal<Jp> BROWSER_SESSION = new ThreadLocal<>();
    private static final ThreadLocal<wt> RESULT_ROW = new ThreadLocal<>();
    private static final ThreadLocal<wt> COUNT_ROW = new ThreadLocal<>();

    private record RowContext(cDj owner, wt result) {}

    private ProjectsInBrowser() {}

    // ---------------------------------------------------------------------
    // The Project filter
    // ---------------------------------------------------------------------

    /** The browser builds its filter list: note the session for the list below. */
    @Hook(target = Jp.class, method = "zpW", at = At.ENTRY, id = "capture-filter-session")
    public static void captureFilterSession(@This Jp session) {
        FILTER_SESSION.set(session);
    }

    /** The filter list: Bitwig's filters, then Project. */
    @Hook(target = Jp.class, method = "zpW", at = At.REPLACE_CALL, callOwner = List.class,
          call = "of([Ljava/lang/Object;)Ljava/util/List;", id = "append-project-filter")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List buildFilterList(Object[] stockFilters) {
        List base = List.of(stockFilters);
        Jp session = FILTER_SESSION.get();
        FILTER_SESSION.remove();
        return BrowserProjectFilter.appendToFilterList(base, session);
    }

    /** A filter's header opens its chooser: show the latest cached project list (refreshed in the background when old). */
    @Hook(target = cCk.class, method = "IwV(LtE;)V", at = At.ENTRY, id = "refresh-project-chooser")
    public static void refreshProjectChooser(@Arg(0) tE filter) {
        BrowserProjectFilter.consumeHeaderClick(filter);
    }

    // ---------------------------------------------------------------------
    // The Everything result list and its rows
    // ---------------------------------------------------------------------

    /** The result list installs its model: note the list for the model below. */
    @Hook(target = cDj.class, method = "FzB(Lwz;)V", at = At.ENTRY, id = "capture-result-owner")
    public static void captureResultOwner(@This cDj owner) {
        RESULT_OWNER.set(owner);
    }

    /** The result list's model: remembered, so a project drill can swap it and put it back. */
    @Hook(target = cDj.class, method = "FzB(Lwz;)V", at = At.REPLACE_CALL, callOwner = wz.class, call = "E3()LpPb;",
          id = "wrap-result-model")
    public static pPb captureBaseModel(wz provider) {
        pPb base = provider.E3();
        cDj owner = RESULT_OWNER.get();
        RESULT_OWNER.remove();
        return ProjectDrill.wrapResultModel(base, owner);
    }

    /** A result row is decorated: note it for the disclosure below. */
    @Hook(target = cDj.class, method = "cfM(Lwt;LdWb;)V", at = At.ENTRY, id = "capture-project-row")
    public static void captureProjectRow(@This cDj owner, @Arg(0) wt result, @Arg(1) dWb row) {
        PROJECT_ROW.set(new RowContext(owner, result));
    }

    /** The row's first widget: Bitwig's own, then a project's disclosure arrow. */
    @Hook(target = cDj.class, method = "cfM(Lwt;LdWb;)V", at = At.REPLACE_CALL, callOwner = dWb.class, call = "HL",
          id = "add-project-disclosure")
    public static void addStockWidgetAndProjectDisclosure(dWb row, dve widget) {
        row.HL(widget); // add the widget
        RowContext ctx = PROJECT_ROW.get();
        PROJECT_ROW.remove();
        if (ctx != null) {
            ProjectDrill.installProjectDisclosure(ctx.owner(), ctx.result(), row);
        }
    }

    /** The browser panel is set up: note its session for the context below. */
    @Hook(target = cFV.class, method = "MRY(Lcom/bitwig/flt/document/core/iface/pHd;LJp;Lcom/bitwig/flt/document_view_settings/hgq;)V", at = At.ENTRY, id = "capture-browser-session")
    public static void captureBrowserSession(@Arg(1) Jp session) {
        BROWSER_SESSION.set(session);
    }

    /** The panel's browser context, which owns the project hierarchy model. */
    @Hook(target = cFV.class, method = "MRY(Lcom/bitwig/flt/document/core/iface/pHd;LJp;Lcom/bitwig/flt/document_view_settings/hgq;)V", at = At.REPLACE_CALL, callOwner = cCV.class,
          call = "cfM(Lcom/bitwig/flt/document/core/iface/pHd;Lcom/bitwig/flt/document_view_settings/hgq;)LcCV;",
          id = "register-browser-context")
    public static cCV createBrowserContext(pHd document, hgq settings) {
        cCV browser = cCV.cfM(document, settings);
        Jp session = BROWSER_SESSION.get();
        BROWSER_SESSION.remove();
        ProjectDrill.registerContext(session, browser);
        return browser;
    }

    /** A result row is built: note its result for the row below. */
    @Hook(target = cFm.class, method = "cfM(Lwt;)Ldve;", at = At.ENTRY, id = "capture-result-row")
    public static void captureResultRow(@Arg(0) wt result) {
        RESULT_ROW.set(result);
    }

    /** The row: indented, with a group's disclosure arrow, for a track inside a project. */
    @Hook(target = cFm.class, method = "cfM(Lwt;)Ldve;", at = At.REPLACE_CALL, callOwner = dWb.class, call = "IwV",
          id = "install-project-hierarchy-prefix")
    public static dWb createResultRow(int layout) {
        dWb row = dWb.IwV(layout); // a row
        wt result = RESULT_ROW.get();
        RESULT_ROW.remove();
        ProjectDrill.installTrackHierarchyPrefix(result, row);
        return row;
    }

    /** A chooser row gets its hit count: note its result for the count below. */
    @Hook(target = cFm.class, method = "MRY(Lwt;LdWb;)V", at = At.ENTRY, id = "capture-count-row")
    public static void captureCountRow(@Arg(0) wt result) {
        COUNT_ROW.set(result);
    }

    /** The hit count: none for a project in the Project chooser. */
    @Hook(target = cFm.class, method = "MRY(Lwt;LdWb;)V", at = At.REPLACE_CALL, callOwner = dWb.class, call = "HL",
          id = "suppress-project-leaf-count")
    public static void addHitCountWidget(dWb row, dve widget) {
        wt result = COUNT_ROW.get();
        COUNT_ROW.remove();
        if (result instanceof ProjectChoiceNode project && project.isProject()) return;
        row.HL(widget);
    }

    /** Dragged results: a track inside a project is dragged as Bitwig's own project node. */
    @Hook(target = cFm.class, method = "cfM(Ljava/util/List;Leoh;)V", at = At.REPLACE_CALL, callOwner = eoh.class,
          call = "cfM(Ljava/lang/Object;)V", id = "unwrap-project-drag-object")
    public static void addDragObject(eoh payload, Object item) {
        payload.cfM(ProjectDrill.unwrapDragObject(item)); // add the object
    }
}
