import bitwig.obf.Cp; // a browser chooser tree node
import java.io.File;

/**
 * A node of the Project filter's chooser: the root ("All Projects"), the Projects group, or one
 * project. It is one of Bitwig's own chooser tree nodes, so Bitwig's chooser shows and navigates
 * it without a widget of the mod's own.
 */
public final class ProjectChoiceNode extends Cp {
    public static final int ROOT = 0;
    public static final int GROUP = 1;
    public static final int PROJECT = 2;

    private final int kind;
    private final String displayName;
    private final String projectPath;
    private final int reportedCount;

    public ProjectChoiceNode(int kind, String displayName, String projectPath, int reportedCount) {
        super();
        this.kind = kind;
        this.displayName = displayName == null ? "" : displayName;
        this.projectPath = projectPath;
        this.reportedCount = Math.max(0, reportedCount);
        MRY(this.displayName); // name
        // The chooser's hit count, separate from the node's real number of children.
        UNj(this.reportedCount);
        H_(); // finish setting up
    }

    public boolean isProject() { return kind == PROJECT && projectPath != null; }
    public boolean isRoot() { return kind == ROOT; }
    public String projectPath() { return projectPath; }

    @Override public String LaE() { return displayName; } // name

    /** A leaf, as in Bitwig's own trees, so the chooser can select a project. */
    @Override public boolean hUR() { return kind == PROJECT; }

    @Override public String Ucl() { // icon
        if (kind == PROJECT) return "browser/document.svg";
        if (kind == ROOT) return "browser/any.svg";
        return "browser/folder_closed.svg";
    }

    @Override public File Grk() { // file
        return projectPath == null ? null : new File(projectPath);
    }

    @Override public String toString() { return displayName; }
}
