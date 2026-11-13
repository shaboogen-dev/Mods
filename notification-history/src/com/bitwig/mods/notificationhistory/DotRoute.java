package com.bitwig.mods.notificationhistory;

/**
 * Which handler a press on the dot gets. No Bitwig names, so it can be tested without Bitwig.
 */
public enum DotRoute {
    /** Bitwig's own handler: the click shows or hides the overlay. */
    STOCK,
    /** The mod's left-click handler: opens or closes the notification panel. */
    PANEL,
    /** The mod's right-click handler: the dot's menu with "Show notifications". */
    MENU;

    /** The mouse buttons that matter here. */
    public enum Button { LEFT, RIGHT, OTHER }

    /**
     * The route for a press. Without Bitwig's own handler to fall back on, or for any other button,
     * everything stays Bitwig's.
     */
    public static DotRoute of(boolean hasStock, Button button) {
        if (!hasStock || button == null) return STOCK;
        return switch (button) {
            case LEFT -> PANEL;
            case RIGHT -> MENU;
            case OTHER -> STOCK;
        };
    }
}
