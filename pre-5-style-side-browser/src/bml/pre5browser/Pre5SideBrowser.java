package bml.pre5browser;

import bml.browser.Browser;
import bml.browser.BrowserProvider;
import bml.browser.Feature;
import bml.browser.View;

/**
 * The side browser in the style of Bitwig 4.x: one view for Framework: Browser. While it is the
 * chosen view, the side browser's results column goes into the view's results slot, where
 * {@link PaneHost} puts the pane beside it (the tabs, the chosen tab's list and the type column).
 * The view hides the side browser's own source rows the pane replaces: the Quick Sources toolbar,
 * the source header and the filter area (and its showing itself on a filter). The toolbar's All
 * Sources button stays, beside the search field: it is the way to what the pane doesn't do, such as
 * creating a collection.
 */
public final class Pre5SideBrowser implements BrowserProvider {
    /** The view and the framework's handle for this mod, once declared. */
    private static volatile View view;
    private static volatile Browser browser;

    @Override
    public void declare(Browser browser) {
        Pre5SideBrowser.browser = browser;
        view = browser.view("side-pane", "Pre-5.0 Style Side Browser")
                      .claims(Feature.ALL_SOURCES_BUTTON, true)
                      .claims(Feature.QUICK_SOURCES_VISIBLE, false)
                      .claims(Feature.SOURCE_HEADER_VISIBLE, false)
                      .claims(Feature.FILTERS_VISIBLE, false)
                      .claims(Feature.FILTER_AUTO_SHOW, false)
                      .results(PaneHost::build);
        browser.features().onChange(TypeRows::featuresChanged); // the view chosen, left or failed: the type rows ask again
    }

    /** True while the view is chosen and working. */
    static boolean active() {
        View v = view;
        return v != null && v.active();
    }

    /** A pane option changed: rebuild the browser while the pane shows, since the pane is built with it. */
    static void optionChanged(String why) {
        Browser b = browser;
        if (b != null && active()) b.rebuild(why);
    }
}
