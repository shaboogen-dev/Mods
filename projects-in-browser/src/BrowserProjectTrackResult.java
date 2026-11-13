import bitwig.obf.cCB; // a browser context's project hierarchy model
import bitwig.obf.hL; // a project node
import bitwig.obf.LJt; // a label
import bitwig.obf.RBf; // a result's preview
import bitwig.obf.zC; // a project file result
import com.bitwig.base.io.z2y; // a file path
import java.io.File;

/**
 * A row of a project drill: one of the project's tracks, around Bitwig's own project node, which
 * stays the source of the track's path and of what is dragged. It also carries the row's place in
 * the hierarchy (depth, expandable, expanded) for the flat result list.
 */
public final class BrowserProjectTrackResult extends zC {
    private final zC parentProject;
    private final hL projectNode;
    private final cCB fileModel;
    private final BrowserProjectDrillModel drillModel;
    private final String displayName;
    private final String iconName;
    private final int depth;
    private final boolean expandable;
    private final boolean expanded;

    public BrowserProjectTrackResult(zC parentProject, hL projectNode, cCB fileModel,
                                     BrowserProjectDrillModel drillModel, int depth,
                                     boolean expandable, boolean expanded) {
        super();
        if (parentProject == null || projectNode == null || fileModel == null || drillModel == null) {
            throw new IllegalArgumentException("null project track adapter input");
        }
        this.parentProject = parentProject;
        this.projectNode = projectNode;
        this.fileModel = fileModel;
        this.drillModel = drillModel;
        this.depth = Math.max(0, depth);
        this.expandable = expandable;
        this.expanded = expanded;

        String name = null;
        try {
            LJt label = fileModel.IwV(projectNode); // the node's label
            if (label != null) name = label.f_(); // its text
        } catch (Throwable ignored) {}
        if (name == null || name.isEmpty()) name = projectNode.toString();
        this.displayName = name;

        String icon = null;
        try { icon = fileModel.UNj(projectNode); } catch (Throwable ignored) {} // the node's icon
        this.iconName = icon;

        MRY(parentProject.QNk(), parentProject.E3(), this.displayName); // source, folder and name as the project's
        H_(); // finish setting up
    }

    public hL rawProjectNode() { return projectNode; }
    public cCB fileModel() { return fileModel; }
    public zC parentProject() { return parentProject; }
    public int hierarchyDepth() { return depth; }
    public boolean hierarchyExpandable() { return expandable; }
    public boolean hierarchyExpanded() { return expanded; }
    public void toggleHierarchy() { if (expandable) drillModel.toggleGroup(projectNode); }

    @Override public String LaE() { return displayName; } // name
    @Override public File Grk() { return parentProject.Grk(); } // file
    @Override public z2y tp2() { return projectNode.TMF(); } // path
    @Override public String Ucl() { return iconName; } // icon
    @Override public RBf ERO() { return null; } // preview
}
