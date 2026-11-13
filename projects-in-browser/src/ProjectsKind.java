import bml.browser.Browser;
import bml.browser.BrowserProvider;

/**
 * Adds Bitwig's Projects file kind to the side browser through Framework: Browser: Everything
 * shows project files, and Projects is a kind under BY KIND and in the source picker, with the
 * Templates kind's tile (Bitwig has no Projects tile) and Bitwig's document icon where the file
 * kind is drawn by name (Bitwig names {@code browser/project.svg}, which it doesn't ship).
 */
public final class ProjectsKind implements BrowserProvider {
    @Override
    public void declare(Browser browser) {
        browser.kind("projects", "Projects", "projects_tile", "browser/document.svg");
    }
}
