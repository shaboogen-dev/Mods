package com.bitwig.hacks;

import java.lang.reflect.Method;

/**
 * A browser view's own search field, through Framework: Browser Configuration when it is there.
 * While a view with its own content (Browser: Folders, ...) shows in the side browser, Bitwig's
 * field is hidden under it; the framework knows the field the view declared. The framework is
 * optional: it is looked up by its public class name, and without it (not installed, or not
 * turned on) the answer is always false, so the action does what it did before.
 */
final class ViewSearchField {
    private static final String API = "bml.browser.ViewSearch"; // Browser Configuration's public API
    private static Method focus;
    private static boolean looked;

    private ViewSearchField() { }

    /** Focuses the field of the view showing in {@code sideBrowser}; false without a view, a field or the framework. */
    static boolean focus(Object sideBrowser) {
        Method method = method();
        if (method == null) return false;
        try {
            return Boolean.TRUE.equals(method.invoke(null, sideBrowser));
        } catch (Throwable failure) {
            return false;
        }
    }

    private static Method method() {
        if (looked) return focus;
        looked = true;
        try {
            Class<?> api = Class.forName(API, false, ViewSearchField.class.getClassLoader());
            focus = api.getMethod("focus", Object.class);
        } catch (ClassNotFoundException | NoSuchMethodException absent) {
            focus = null; // Browser Configuration isn't there, or is older than 0.2.0-dev.2
        } catch (Throwable failure) {
            focus = null;
        }
        return focus;
    }
}
