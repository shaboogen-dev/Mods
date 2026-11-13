import bitwig.obf.dpN; // Bitwig's application: its UI event loop
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The projects in Bitwig's Projects location, found by one background thread and cached. Nothing
 * here touches the disk on the caller's thread: {@link #latest()} returns the cached list at once,
 * and {@link #request} only queues a scan when one is needed.
 *
 * <p>A scan looks for {@code .bwproject} files. A folder with a {@code .bwproject} file at its top
 * level is a project folder: its project files are listed and its subfolders (samples, recordings,
 * bounces...) are never visited. Other folders are searched further, so projects in the user's own
 * subfolders are found; the Projects folder itself is always searched further, and its own
 * {@code .bwproject} files are listed. Hidden and system folders are skipped.
 */
final class ProjectIndex {
    /** A project file: its name without {@code .bwproject} and its canonical path. */
    record Project(String name, String path) {}

    /** One finished scan. {@code root} is the scanned folder's absolute path. */
    record Snapshot(String root, List<Project> projects, long finishedNanos) {}

    /** A cached list older than this is refreshed when the browser asks for it again. */
    static final long STALE_NANOS = 30_000_000_000L;

    private static final String EXT = ".bwproject";

    private static final ExecutorService SCANNER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "projects-in-browser-scan");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    private static volatile Snapshot latest;
    private static String queuedRoot; // the folder of the scan queued or running; guarded by ProjectIndex.class

    private ProjectIndex() {}

    static Snapshot latest() { return latest; }

    static String key(File root) {
        return root == null ? null : root.getAbsolutePath();
    }

    /**
     * Any thread, never blocks: queue a scan of {@code root} when there is no cached list for it,
     * or, with {@code refreshIfOld}, when the cached list is older than {@link #STALE_NANOS}.
     */
    static void request(File root, boolean refreshIfOld) {
        String key = key(root);
        if (key == null) return;
        Snapshot s = latest;
        boolean needed = s == null || !s.root().equals(key)
            || (refreshIfOld && System.nanoTime() - s.finishedNanos() > STALE_NANOS);
        if (!needed) return;
        synchronized (ProjectIndex.class) {
            if (key.equals(queuedRoot)) return; // already queued or running
            queuedRoot = key;
        }
        try {
            SCANNER.execute(() -> scan(root, key));
        } catch (Throwable t) {
            synchronized (ProjectIndex.class) { if (key.equals(queuedRoot)) queuedRoot = null; }
        }
    }

    private static void scan(File root, String key) {
        List<Project> projects = Collections.emptyList();
        try {
            projects = discover(root);
        } catch (Throwable ignored) {
        } finally {
            synchronized (ProjectIndex.class) { if (key.equals(queuedRoot)) queuedRoot = null; }
        }
        Snapshot s = new Snapshot(key, projects, System.nanoTime());
        latest = s;
        // Hand the new list to the browser's filters on the UI thread, as Bitwig's own async results do.
        try {
            dpN app = dpN.TVZ(); // the application
            if (app != null) app.asyncExec(() -> BrowserProjectFilter.snapshotLanded(s));
        } catch (Throwable ignored) {
            // The next chooser opening picks the list up.
        }
    }

    private static boolean isProjectName(Path p) {
        Path name = p.getFileName();
        return name != null && name.toString().toLowerCase(Locale.ROOT).endsWith(EXT);
    }

    private static String displayName(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(EXT)
            ? fileName.substring(0, fileName.length() - EXT.length()) : fileName;
    }

    private static boolean hidden(Path dir, BasicFileAttributes attrs) {
        Path name = dir.getFileName();
        if (name != null && name.toString().startsWith(".")) return true;
        return attrs instanceof DosFileAttributes dos && (dos.isHidden() || dos.isSystem());
    }

    private static List<Project> discover(File root) throws IOException {
        if (root == null || !root.isDirectory()) return Collections.emptyList();
        Path rootPath = root.toPath();
        // One canonical path for the folder; projects get it plus their path inside it.
        Path canonicalRoot;
        try { canonicalRoot = new File(root.getCanonicalPath()).toPath(); }
        catch (Throwable e) { canonicalRoot = root.getAbsoluteFile().toPath(); }
        Path base = canonicalRoot;

        ArrayList<Project> out = new ArrayList<>();
        Files.walkFileTree(rootPath, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
            new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.equals(rootPath)) return FileVisitResult.CONTINUE;
                    if (hidden(dir, attrs)) return FileVisitResult.SKIP_SUBTREE;
                    // List the folder once: a project folder ends here.
                    ArrayList<Path> found = new ArrayList<>(2);
                    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                        for (Path p : ds) {
                            if (isProjectName(p) && Files.isRegularFile(p)) found.add(p);
                        }
                    } catch (Throwable e) {
                        return FileVisitResult.SKIP_SUBTREE; // unreadable: skipped, the walk goes on
                    }
                    if (found.isEmpty()) return FileVisitResult.CONTINUE; // the walker lists it for its subfolders
                    for (Path p : found) out.add(project(rootPath, base, p));
                    return FileVisitResult.SKIP_SUBTREE;
                }

                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    // Only the Projects folder's own files can be projects here: any other folder
                    // the walker lists has no project file at its top level.
                    if (rootPath.equals(file.getParent()) && isProjectName(file) && Files.isRegularFile(file)) {
                        out.add(project(rootPath, base, file));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override public FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        out.sort(Comparator
            .comparing(Project::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Project::path, String.CASE_INSENSITIVE_ORDER));
        return Collections.unmodifiableList(out);
    }

    private static Project project(Path rootPath, Path canonicalRoot, Path file) {
        String path;
        try { path = canonicalRoot.resolve(rootPath.relativize(file)).toString(); }
        catch (Throwable e) { path = file.toAbsolutePath().toString(); }
        return new Project(displayName(file.getFileName().toString()), path);
    }
}
