import bitwig.obf.cCB; // a browser context's project hierarchy model
import bitwig.obf.cCV; // a browser context
import bitwig.obf.cDj; // the Everything result list
import bitwig.obf.dAa; // a compact icon button
import bitwig.obf.dWb; // a row of widgets
import bitwig.obf.Jp; // a browser session
import bitwig.obf.pPb; // a list model
import bitwig.obf.w3; // widget styles
import bitwig.obf.wt; // a browser result
import bitwig.obf.zC; // a project file result
import com.bitwig.base.io.z2y; // a file path
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Project drill-in: the Everything result list shows one project's contents instead of the search
 * results, entered from a project row's arrow or the Project filter, and left the same way or with
 * the Project chip's X. Switching straight from one project to another is supported.
 */
public final class ProjectDrill {
    private static final Map<Jp, WeakReference<cCV>> CONTEXTS = new WeakHashMap<>();
    private static final Map<cDj, pPb> BASE_MODELS = new WeakHashMap<>();
    private static final Map<cDj, cCB> FILE_MODELS = new WeakHashMap<>();
    private static final Map<cDj, DrillState> DRILLS = new WeakHashMap<>();

    private static final class DrillState {
        final zC project;
        final String key;
        final String previousSearch;
        final BrowserProjectDrillModel model;
        DrillState(zC project, String key, String previousSearch, BrowserProjectDrillModel model) {
            this.project = project;
            this.key = key;
            this.previousSearch = previousSearch;
            this.model = model;
        }
    }

    private ProjectDrill() {}

    public static synchronized void registerContext(Jp il, cCV browser) {
        if (il == null || browser == null) return;
        CONTEXTS.put(il, new WeakReference<>(browser));

        cCB fileModel = modelFor(browser);
        if (fileModel == null || BASE_MODELS.isEmpty()) return;
        for (cDj owner : new ArrayList<>(BASE_MODELS.keySet())) {
            if (owner == null) continue;
            try {
                if (owner.bB() == il) FILE_MODELS.put(owner, fileModel); // the list's session
            } catch (Throwable ignored) {}
        }
    }

    private static synchronized cCV contextFor(Jp il) {
        WeakReference<cCV> ref = CONTEXTS.get(il);
        return ref == null ? null : ref.get();
    }

    private static cCB modelFor(cCV browser) {
        return browser == null ? null : browser.E3; // its project hierarchy model
    }

    public static pPb wrapResultModel(pPb base, cDj owner) {
        if (base == null || owner == null) return base;
        synchronized (ProjectDrill.class) {
            BASE_MODELS.put(owner, base);
        }
        try {
            cCB fileModel = modelFor(contextFor(owner.bB()));
            if (fileModel != null) {
                synchronized (ProjectDrill.class) {
                    FILE_MODELS.put(owner, fileModel);
                }
            }
        } catch (Throwable ignored) {}
        return base;
    }

    private static synchronized cCB fileModelFor(cDj owner) {
        cCB model = FILE_MODELS.get(owner);
        if (model != null) return model;
        try {
            model = modelFor(contextFor(owner.bB()));
            if (model != null) FILE_MODELS.put(owner, model);
        } catch (Throwable ignored) {}
        return model;
    }

    private static String projectKey(wt result) {
        if (result == null || result instanceof BrowserProjectTrackResult) return null;
        try {
            z2y p = result.tp2(); // its path
            if (p == null || p.TMF()) return null; // none, or inside a file
            java.io.File f = p.cfM(); // the file
            if (f == null || !f.getName().toLowerCase(Locale.ROOT).endsWith(".bwproject")) return null;
            return f.getAbsolutePath();
        } catch (Throwable t) {
            return null;
        }
    }

    private static synchronized DrillState drillFor(cDj owner) {
        return DRILLS.get(owner);
    }

    /** A project row in Everything: an arrow that enters the project, or leaves it when it is open. */
    public static void installProjectDisclosure(cDj owner, wt result, dWb row) {
        if (owner == null || result == null || row == null) return;
        String key = projectKey(result);
        if (key == null) return;
        if (fileModelFor(owner) == null) return;
        try {
            DrillState active = drillFor(owner);
            boolean isActiveProject = active != null && key.equals(active.key);

            dAa button = dAa.mtS(); // a new button
            button.TMF(isActiveProject ? "stepper_arrow_left.svg" : "fold_arrow_right.svg"); // icon
            button.Hnt(w3.MRY); // style
            button.tp2("Hover Icon button"); // style name
            button.wGc(isActiveProject ? "Back to search results" : "Show project contents"); // help text
            button.Hnt((Runnable)() -> { // on click
                try {
                    DrillState now = drillFor(owner);
                    boolean activeNow = now != null && key.equals(now.key);
                    if (activeNow) {
                        exitDrill(owner);
                        button.TMF("fold_arrow_right.svg");
                        button.wGc("Show project contents");
                    } else {
                        enterDrill(owner, result, key);
                        DrillState after = drillFor(owner);
                        if (after != null && key.equals(after.key)) {
                            button.TMF("stepper_arrow_left.svg");
                            button.wGc("Back to search results");
                        }
                    }
                } catch (Throwable ignored) {}
            });
            row.HL(button); // add it
        } catch (Throwable ignored) {}
    }

    /**
     * Before a project track's icon and label: its indent and, for a group, its disclosure arrow,
     * so the flat result list reads as a tree. Only {@link BrowserProjectTrackResult} rows change.
     */
    public static void installTrackHierarchyPrefix(wt result, dWb row) {
        if (!(result instanceof BrowserProjectTrackResult track) || row == null) return;
        try {
            int depth = track.hierarchyDepth();
            if (depth > 0) row.Hnt((long)depth * 18L); // a gap

            if (track.hierarchyExpandable()) {
                dAa button = dAa.mtS();
                button.TMF(track.hierarchyExpanded() ? "fold_arrow_down.svg" : "fold_arrow_right.svg");
                button.Hnt(w3.MRY);
                button.tp2("Hover Icon button");
                button.wGc(track.hierarchyExpanded() ? "Collapse group" : "Expand group");
                button.Hnt((Runnable)track::toggleHierarchy);
                row.HL(button);
                row.Hnt(3L);
            } else {
                // Leaves keep the arrow's column (21 px, measured against Bitwig's own tree) so
                // their icons line up with expandable siblings.
                row.Hnt(21L);
                row.Hnt(3L);
            }
        } catch (Throwable ignored) {}
    }

    /** From the Project filter's chooser: enter a project built against Bitwig's own Projects source. */
    public static boolean enterProject(Jp il, zC project, String key) {
        if (il == null || project == null || key == null) return false;
        ArrayList<cDj> owners = new ArrayList<>();
        synchronized (ProjectDrill.class) {
            for (cDj owner : BASE_MODELS.keySet()) {
                if (owner == null) continue;
                try {
                    if (owner.bB() == il) owners.add(owner);
                } catch (Throwable ignored) {}
            }
        }
        for (cDj owner : owners) {
            try {
                if (fileModelFor(owner) == null) continue;
                enterDrill(owner, project, key);
                DrillState state = drillFor(owner);
                if (state != null && key.equals(state.key)) return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static void enterDrill(cDj owner, wt result, String key) {
        if (!(result instanceof zC project)) return;
        pPb base;
        cCB fileModel;
        Jp il;
        DrillState old;
        synchronized (ProjectDrill.class) {
            old = DRILLS.get(owner);
            if (old != null && key.equals(old.key)) return;
            base = BASE_MODELS.get(owner);
            fileModel = FILE_MODELS.get(owner);
            if (fileModel == null) fileModel = fileModelFor(owner);
            if (base == null || fileModel == null) return;
        }

        il = owner.bB();
        String previousSearch = "";
        boolean switching = old != null;
        if (switching) {
            previousSearch = old.previousSearch;
            try { old.model.dispose(); } catch (Throwable ignored) {}
        } else {
            try { if (il != null && il.JJY() != null) previousSearch = il.JJY(); } catch (Throwable ignored) {} // the search text
        }

        BrowserProjectDrillModel model = new BrowserProjectDrillModel(project, fileModel);
        synchronized (ProjectDrill.class) {
            DRILLS.put(owner, new DrillState(project, key, previousSearch, model));
        }

        BrowserProjectFilter.activateFromProjectPath(il, key);
        if (owner.hUR != null) owner.hUR.FzB((pPb)model); // the list's model

        // Only the first entry clears the search text; a switch between projects is already
        // detached from Bitwig's Everything model.
        if (!switching) {
            try {
                if (il != null && previousSearch != null && !previousSearch.isEmpty()) il.cfM(""); // set the search text
            } catch (Throwable ignored) {}
        }
    }

    /** Leave any project drill of this browser session. */
    public static void exitProject(Jp il) {
        if (il == null) return;
        ArrayList<cDj> owners = new ArrayList<>();
        synchronized (ProjectDrill.class) {
            for (cDj owner : DRILLS.keySet()) {
                if (owner == null) continue;
                try {
                    if (owner.bB() == il) owners.add(owner);
                } catch (Throwable ignored) {}
            }
        }
        for (cDj owner : owners) {
            try { exitDrill(owner); } catch (Throwable ignored) {}
        }
    }

    private static void exitDrill(cDj owner) {
        DrillState state;
        pPb base;
        synchronized (ProjectDrill.class) {
            state = DRILLS.remove(owner);
            base = BASE_MODELS.get(owner);
        }
        if (state == null) return;
        try { state.model.dispose(); } catch (Throwable ignored) {}
        if (owner.hUR != null && base != null) owner.hUR.FzB(base);
        try {
            Jp il = owner.bB();
            if (il != null && state.previousSearch != null) il.cfM(state.previousSearch);
        } catch (Throwable ignored) {}
        BrowserProjectFilter.deactivateOwner(owner);
    }

    public static Object unwrapDragObject(Object item) {
        if (item instanceof BrowserProjectTrackResult track) return track.rawProjectNode();
        return item;
    }
}
