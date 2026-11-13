package com.bitwig.mods.audioanalysis;

/**
 * The status lines of the setup tasks, short and plain so they fit the notification centre's row
 * (under about 40 characters). Pure: tested offline.
 */
public final class Status {
    private Status() { }

    public static final String UNPACKING_PYTHON = "Unpacking Python";
    public static final String CREATING_ENVIRONMENT = "Creating the environment";
    public static final String UNPACKING_PACKAGES = "Unpacking the packages";
    public static final String CHECKING = "Checking the install";
    public static final String INSTALLING_PYTORCH = "Installing PyTorch";
    public static final String REMOVING = "Removing the setup";
    public static final String CANCELLING = "Cancelling";

    /** "Downloading Python (47 MB)". */
    public static String downloadingPython(String size) {
        return "Downloading Python (" + size + ")";
    }

    /** "Installing packages (12 of 46)", or "Unpacking the packages" once pip unpacks them. */
    public static String packages(PipProgress pip) {
        if (pip.unpacking()) return UNPACKING_PACKAGES;
        return "Installing packages (" + pip.handled() + " of " + pip.total() + ")";
    }

    /** "Downloading the model (84 MB)". */
    public static String downloadingModel(int megabytes) {
        return "Downloading the model (" + megabytes + " MB)";
    }

    /** A step's text without a trailing ellipsis ("…" or "..."): the row is short, and a status line is a state, not a pause. */
    public static String plain(String text) {
        if (text == null) return "";
        String t = text.strip();
        while (t.endsWith("…") || t.endsWith("...")) t = t.endsWith("…") ? t.substring(0, t.length() - 1).strip() : t.substring(0, t.length() - 3).strip();
        return t;
    }
}
