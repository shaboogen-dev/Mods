import bitwig.obf.beB; // a list model listener
import bitwig.obf.cCB; // a browser context's project hierarchy model
import bitwig.obf.hI; // a project node with children (a group track)
import bitwig.obf.hL; // a project node
import bitwig.obf.pPb; // a list model
import bitwig.obf.r3s; // a list model that holds its items
import bitwig.obf.zC; // a project file result
import java.io.File;
import java.util.*;

/**
 * The result list of a project drill: the project's tracks as the File Browser shows them, with
 * group tracks that expand and collapse. Only the visible branches of the open project are
 * flattened into the list, so the Everything model stays small.
 */
public final class BrowserProjectDrillModel extends r3s implements beB {
    private final zC project;
    private final cCB fileModel;
    private pPb tree;
    private final java.util.Set<String> expandedGroups = new java.util.HashSet<>();
    private boolean disposed;
    private boolean rebuilding;

    public BrowserProjectDrillModel(zC project, cCB fileModel) {
        super();
        if (project == null || fileModel == null) throw new IllegalArgumentException("null drill input");
        this.project = project;
        this.fileModel = fileModel;
        try {
            File file = null;
            var path = project.tp2(); // its path
            if (path != null && !path.TMF()) file = path.cfM(); // a file of its own: the file
            if (file != null) {
                tree = fileModel.cfM((Object)file); // the project's top-level nodes
                if (tree != null) tree.cfM((beB)this); // listen
            }
        } catch (Throwable ignored) {}
        rebuild();
    }

    private static String nodeKey(hL node) {
        if (node == null) return null;
        try {
            var p = node.TMF(); // its path
            if (p != null) return p.toString();
        } catch (Throwable ignored) {}
        return node.getClass().getName() + "@" + System.identityHashCode(node);
    }

    public synchronized void toggleGroup(hL node) {
        if (disposed || node == null || !(node instanceof hI)) return;
        String key = nodeKey(node);
        if (key == null) return;
        if (!expandedGroups.add(key)) expandedGroups.remove(key);
        rebuild();
    }

    public synchronized boolean isExpanded(hL node) {
        String key = nodeKey(node);
        return key != null && expandedGroups.contains(key);
    }

    private void appendNode(ArrayList<Object> out, hL node, int depth, java.util.Set<hL> path) {
        if (node == null || depth > 64 || path.contains(node)) return;
        boolean expandable = node instanceof hI;
        boolean expanded = expandable && isExpanded(node);
        try {
            out.add(new BrowserProjectTrackResult(project, node, fileModel, this, depth, expandable, expanded));
        } catch (Throwable ignored) {
            return;
        }

        if (!expanded) return;
        path.add(node);
        try {
            // A node's children as the File Browser gets them; Bitwig's nodes are kept as they are
            // and only the visible branch is flattened.
            pPb children = fileModel.cfM((Object)node);
            if (children == null) return;
            int n = children.MRY(); // its size
            for (int i = 0; i < n; i++) {
                Object child = children.MRY(i); // an item
                if (child instanceof hL glChild) appendNode(out, glChild, depth + 1, path);
            }
        } catch (Throwable ignored) {
        } finally {
            path.remove(node);
        }
    }

    private synchronized void rebuild() {
        if (disposed || rebuilding) return;
        rebuilding = true;
        try {
            ArrayList<Object> out = new ArrayList<>();
            pPb t = tree;
            if (t != null) {
                int count = t.MRY();
                java.util.Set<hL> path = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                for (int i = 0; i < count; i++) {
                    Object child = t.MRY(i);
                    if (child instanceof hL node) appendNode(out, node, 0, path);
                }
            }
            cfM(out); // the list's items
        } finally {
            rebuilding = false;
        }
    }

    public synchronized void dispose() {
        if (disposed) return;
        disposed = true;
        try { if (tree != null) tree.MRY((beB)this); } catch (Throwable ignored) {} // stop listening
        tree = null;
        expandedGroups.clear();
    }

    @Override public void addedAsListenerTo(pPb source) {}
    @Override public void aboutToRemoveAsListenerFrom(pPb source) {}
    @Override public void insertedItems(pPb source, int first, int last) { rebuild(); }
    @Override public void aboutToRemoveItems(pPb source, int first, int last) {}
    @Override public void aboutToChangeItems(pPb source, int first, int last) {}
    @Override public void changedItems(pPb source, int first, int last) { rebuild(); }
}
