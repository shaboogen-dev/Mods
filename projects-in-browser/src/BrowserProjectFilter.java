import bitwig.obf.cDj; // the Everything result list
import bitwig.obf.DU; // a chooser tree's expand/collapse state
import bitwig.obf.DZ; // a chooser value
import bitwig.obf.EE; // a browser library
import bitwig.obf.GC; // a file source (here: Projects)
import bitwig.obf.Ij; // a browser filter with a tree chooser (as Device)
import bitwig.obf.Jp; // a browser session
import bitwig.obf.tE; // a browser filter
import bitwig.obf.tH; // a chooser tree state
import bitwig.obf.tJ; // a chooser's selected value
import bitwig.obf.xl; // a chooser tree root
import bitwig.obf.zC; // a project file result
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Project filter in the Everything browser, after Bitwig's own filters. It is one of Bitwig's
 * tree-chooser filters (the kind Device uses), so Bitwig shows its chooser and chip; the chooser
 * lists the projects in Bitwig's Projects location. Choosing a project enters it
 * ({@link ProjectDrill}); "All Projects" or the chip's X leaves it. The filter never takes part in
 * Bitwig's result query: the drill's own result list does the restricting.
 *
 * <p>The project list comes from {@link ProjectIndex}, which scans in the background: building or
 * swapping the chooser's tree never touches the disk.
 */
public final class BrowserProjectFilter extends Ij {
    /** The filters of the most recent sessions, most recently used first; older ones are let go. */
    private static final int MAX_FILTERS = 32;
    private static final ArrayList<BrowserProjectFilter> FILTERS = new ArrayList<>();

    private final Jp session;
    private boolean projectActive;
    private String projectName = "";
    private String projectPath = "";

    private ProjectChoiceNode chooserRoot;
    private ProjectChoiceNode chooserGroup;
    private ProjectChoiceNode selectedChoice;
    private DU chooserState;
    private ProjectIndex.Snapshot treeSnapshot; // the list the chooser's tree was built from
    private boolean treeBuilt;
    private boolean suppressSelectionHook;

    private BrowserProjectFilter(Jp session) {
        super();
        this.session = session;
        Hnt(session); // the session
        H_(); // finish setting up
        UNj(true); // shown
        ProjectIndex.request(projectsFolder(), true);
        rebuildChooserTree();
        refreshPresentation();
    }

    public static synchronized BrowserProjectFilter forSession(Jp session) {
        if (session == null) return null;
        for (int i = 0; i < FILTERS.size(); i++) {
            BrowserProjectFilter f = FILTERS.get(i);
            if (f.session == session) {
                if (i != 0) { FILTERS.remove(i); FILTERS.add(0, f); }
                return f;
            }
        }
        BrowserProjectFilter filter = new BrowserProjectFilter(session);
        FILTERS.add(0, filter);
        while (FILTERS.size() > MAX_FILTERS) FILTERS.remove(FILTERS.size() - 1);
        return filter;
    }

    private static synchronized BrowserProjectFilter existing(Jp session) {
        for (BrowserProjectFilter f : FILTERS) if (f.session == session) return f;
        return null;
    }

    /** UI thread: a background scan finished. Every filter showing an older list of that folder takes the new one. */
    static void snapshotLanded(ProjectIndex.Snapshot snapshot) {
        List<BrowserProjectFilter> filters;
        synchronized (BrowserProjectFilter.class) { filters = new ArrayList<>(FILTERS); }
        for (BrowserProjectFilter f : filters) {
            try {
                if (f.treeBuilt && f.treeSnapshot != snapshot
                        && snapshot.root().equals(ProjectIndex.key(f.projectsFolder()))) {
                    f.rebuildChooserTree();
                }
            } catch (Throwable ignored) {}
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List appendToFilterList(List base, Jp session) {
        try {
            BrowserProjectFilter project = forSession(session);
            if (project == null) return base;
            ArrayList out = new ArrayList(base == null ? 1 : base.size() + 1);
            if (base != null) out.addAll(base);
            if (!out.contains(project)) out.add(project);
            return out;
        } catch (Throwable ignored) {
            return base;
        }
    }

    /**
     * Just before Bitwig opens the chooser: show the latest cached list, and refresh it in the
     * background when it is old. Bitwig then opens the chooser as usual.
     */
    public static boolean consumeHeaderClick(tE filter) {
        if (filter instanceof BrowserProjectFilter project) {
            try {
                File folder = project.projectsFolder();
                ProjectIndex.request(folder, true);
                ProjectIndex.Snapshot latest = ProjectIndex.latest();
                if (!project.treeBuilt || (latest != null && latest != project.treeSnapshot
                        && latest.root().equals(ProjectIndex.key(folder)))) {
                    project.rebuildChooserTree();
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static void activateFromProjectPath(Jp session, String path) {
        try {
            BrowserProjectFilter filter = forSession(session);
            if (filter == null) return;
            String canonical = canonicalPath(path);
            String name = canonical == null ? "" : new File(canonical).getName();
            if (name.toLowerCase(Locale.ROOT).endsWith(".bwproject")) {
                name = name.substring(0, name.length() - ".bwproject".length());
            }
            filter.projectActive = true;
            filter.projectPath = canonical == null ? "" : canonical;
            filter.projectName = name;
            filter.selectedChoice = filter.findChoiceByPath(filter.projectPath);
            if (filter.selectedChoice == null) {
                filter.selectedChoice = new ProjectChoiceNode(
                    ProjectChoiceNode.PROJECT, filter.projectName, filter.projectPath, 0);
            }
            filter.syncNativeSelection(filter.selectedChoice);
            filter.refreshPresentation();
        } catch (Throwable ignored) {}
    }

    public static void deactivate(Jp session) {
        try {
            BrowserProjectFilter filter = existing(session);
            if (filter == null) return;
            filter.projectActive = false;
            filter.projectName = "";
            filter.projectPath = "";
            filter.selectedChoice = filter.chooserRoot;
            if (filter.chooserRoot != null) filter.syncNativeSelection(filter.chooserRoot);
            filter.refreshPresentation();
        } catch (Throwable ignored) {}
    }

    public static void deactivateOwner(cDj owner) {
        try {
            if (owner != null) deactivate(owner.bB()); // the list's session
        } catch (Throwable ignored) {}
    }

    private static String canonicalPath(String path) {
        if (path == null || path.isBlank()) return null;
        try { return new File(path).getCanonicalPath(); }
        catch (Throwable ignored) { return new File(path).getAbsolutePath(); }
    }

    private GC projectSource() {
        try {
            EE library = session == null ? null : session.Bpn(); // the session's library
            return library == null ? null : library.eej(); // its Projects source
        } catch (Throwable ignored) {
            return null;
        }
    }

    private File projectsFolder() {
        try {
            GC source = projectSource();
            return source == null ? null : source.MJ(); // its folder
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * The chooser tree, from Bitwig's own tree nodes, listing the projects of the latest background
     * scan of the Projects location (none yet: an empty "Projects (scanning)" group). No disk access.
     */
    private synchronized void rebuildChooserTree() {
        ProjectIndex.Snapshot snapshot = ProjectIndex.latest();
        String folderKey = ProjectIndex.key(projectsFolder());
        if (snapshot != null && !snapshot.root().equals(folderKey)) snapshot = null; // another folder's list
        List<ProjectIndex.Project> projects = snapshot == null ? List.of() : snapshot.projects();
        int count = projects.size();

        ProjectChoiceNode newRoot = new ProjectChoiceNode(ProjectChoiceNode.ROOT, "All Projects", null, count);
        ProjectChoiceNode newGroup = new ProjectChoiceNode(ProjectChoiceNode.GROUP,
            snapshot == null && folderKey != null ? "Projects (scanning)" : "Projects", null, count);
        newRoot.cfM(newGroup, 0); // add a child

        ProjectChoiceNode activeMatch = null;
        int index = 0;
        for (ProjectIndex.Project project : projects) {
            String canonical = project.path();
            ProjectChoiceNode node = new ProjectChoiceNode(
                ProjectChoiceNode.PROJECT, project.name(), canonical, 0);
            newGroup.cfM(node, index++);
            if (projectActive && projectPath != null && !projectPath.isEmpty()
                    && canonical != null && canonical.equalsIgnoreCase(projectPath)) {
                activeMatch = node;
            }
        }

        DU newState = DU.IwV(); // a new state
        try { newState.MRY(newGroup); } catch (Throwable ignored) {} // expanded

        chooserRoot = newRoot;
        chooserGroup = newGroup;
        chooserState = newState;
        treeSnapshot = snapshot;
        treeBuilt = true;
        selectedChoice = projectActive
            ? (activeMatch != null ? activeMatch : new ProjectChoiceNode(
                ProjectChoiceNode.PROJECT, projectName, projectPath, 0))
            : newRoot;
        syncNativeSelection(selectedChoice);
    }

    private ProjectChoiceNode findChoiceByPath(String path) {
        if (path == null || path.isEmpty() || chooserGroup == null) return null;
        try {
            List children = chooserGroup.hbP(); // its children
            if (children != null) {
                for (Object o : children) {
                    if (o instanceof ProjectChoiceNode node && node.isProject()) {
                        String p = node.projectPath();
                        if (p != null && p.equalsIgnoreCase(path)) return node;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void syncNativeSelection(ProjectChoiceNode choice) {
        if (choice == null) return;
        try {
            suppressSelectionHook = true;
            MRY((DZ)choice); // the filter's selected value
        } catch (Throwable ignored) {
        } finally {
            suppressSelectionHook = false;
        }
    }

    private zC nativeProjectResult(String path) {
        try {
            GC source = projectSource();
            if (source == null) return null;
            File root = source.MJ();
            File file = new File(path);
            Path rootPath = root.toPath().toAbsolutePath().normalize();
            Path filePath = file.toPath().toAbsolutePath().normalize();
            if (!filePath.startsWith(rootPath)) return null;
            Path parent = filePath.getParent();
            if (parent == null) return null;
            Path rel = rootPath.relativize(parent);
            String relDir = rel.toString().replace(File.separatorChar, '/');
            return zC.cfM(source, relDir, file.getName()); // a result for a file in the source
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void selectProject(ProjectChoiceNode choice) {
        if (choice == null || !choice.isProject()) return;
        String path = choice.projectPath();
        if (path == null || path.isEmpty()) return;
        zC result = nativeProjectResult(path);
        if (result == null) return;
        if (ProjectDrill.enterProject(session, result, path)) {
            selectedChoice = choice;
        }
    }

    private static void exitCurrentProject(Jp session) {
        try { ProjectDrill.exitProject(session); }
        catch (Throwable ignored) {}
    }

    private void refreshPresentation() {
        try {
            MRY(projectActive && !projectName.isEmpty() ? projectName : "Project"); // chip label
            hUR(projectActive ? "browser/document.svg" : "browser/any.svg"); // chip icon
            wGc(""); // help text
            Hnt(projectActive); // active
        } catch (Throwable ignored) {}
    }

    /** The chooser's tree. */
    @Override public xl UNj() {
        if (chooserRoot == null) rebuildChooserTree();
        return chooserRoot;
    }

    /** The chooser tree's expand/collapse state. */
    @Override public tH lYc() {
        if (chooserState == null) rebuildChooserTree();
        return chooserState;
    }

    /** The selected value, shown by the chooser and the chip. */
    @Override public tJ hUR() {
        if (chooserRoot == null) rebuildChooserTree();
        if (projectActive && selectedChoice != null) return selectedChoice;
        return chooserRoot;
    }

    /**
     * The chooser changed the selected value: enter the chosen project, or leave the open one for
     * "All Projects", instead of the Device filter's handling.
     */
    @Override public void cfM(DZ oldValue) {
        if (suppressSelectionHook) return;
        try {
            DZ current = Nnn(); // the selected value
            if (!(current instanceof ProjectChoiceNode choice)) return;
            if (choice.isProject()) {
                selectProject(choice);
            } else if (choice.isRoot()) {
                if (projectActive) exitCurrentProject(session);
            }
            // The Projects group only structures the tree, which expands it itself.
        } catch (Throwable ignored) {}
    }

    @Override public String Ucl() { return "projects"; } // ID
    @Override public String iCS() { return "Project"; } // name

    @Override public void hU_() { refreshPresentation(); } // refresh

    /** Never part of the result query. */
    @Override public void cfM(boolean oldValue) {}

    @Override public boolean jFL() { return projectActive; } // active

    /** The chip's X. */
    @Override public void MIO() {
        if (!projectActive) return;
        exitCurrentProject(session);
    }
}
