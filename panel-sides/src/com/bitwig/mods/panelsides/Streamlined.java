package com.bitwig.mods.panelsides;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import bitwig.obf.AQw; // a saved on/off value (here: a panel's "is shown")
import bitwig.obf.aBW; // a window's project view (its layout widgets and view settings)
import bitwig.obf.ajN; // which panel the detail editor follows
import bitwig.obf.ajP; // the detail editor panel (piano roll and the other clip editors)
import bitwig.obf.ajS; // the device panel
import bitwig.obf.ajv; // the arranger panel (tracks and clips)
import bitwig.obf.akC; // a display profile: its windows, modes and layouts
import bitwig.obf.akQ; // a panel
import bitwig.obf.akZ; // a splitter between two parts of a window's layout
import bitwig.obf.akj; // the mixer panel
import bitwig.obf.akn; // a mode of the main view area (ARRANGE, MIX, EDIT)
import bitwig.obf.akq; // the main view area: one layout per mode
import bitwig.obf.akt; // the on-screen keyboard panel
import bitwig.obf.akw; // a part of a window's layout
import bitwig.obf.akx; // a part of a layout that names the panel docked windows go to
import bitwig.obf.akz; // one layout of the main view area, for its modes
import bitwig.obf.dAd; // a toggle button widget
import bitwig.obf.dVx; // which side of a splitter keeps its size
import bitwig.obf.dWb; // a row or column of widgets
import bitwig.obf.dmQ; // a clickable action that runs a Runnable
import bitwig.obf.dve; // a widget
import bitwig.obf.dws; // what a button runs when clicked
import bitwig.obf.kn; // the bottom bar's button colour
import bitwig.obf.w3; // the bottom bar's button sizes
import bml.panels.Layout;
import bml.panels.LayoutPolicy;
import com.bitwig.base.geom.IBe; // the bottom bar's row style
import com.bitwig.flt.document_view_settings.gJi; // a project's view settings for one display profile
import com.bitwig.flt.document_view_settings.rsB; // the base of the view settings: holds the current mode

/**
 * Streamlined Views' layout policy (through Framework: Panels). In a profile
 * with ARRANGE / MIX / EDIT in its first window, Bitwig's main view area (one layout per mode) is
 * replaced by one layout shared by all three modes:
 *
 * <pre>
 * main_devices_splitter (top/bottom; the device panel keeps its height)
 *   views                 the framework's view stack: Tracks, Detail Editor, Mixer, then the mods' views
 *   devices               the device panel, shown by default
 * </pre>
 *
 * The modes become presets: ARRANGE shows Tracks alone, MIX the Mixer, EDIT the Detail Editor; the
 * device panel is left as it is, and can go across the full width of the window instead.
 */
final class Streamlined implements LayoutPolicy {
    static final String MOD_ID = "panel-sides";

    /** Each profile with the layout. */
    private static final Map<akC, Streamlined.Profile> PROFILES = Collections.synchronizedMap(new IdentityHashMap<>());

    private static final ViewAction TOGGLE_DEVICES = new ViewAction("panel_sides_click_toggle_devices", "Show or hide the device panel");

    /** One profile's layout. */
    private static final class Profile {
        final Layout layout;
        final ajv tracks;
        final ajS devices;
        final akw views;            // the stack's layout node
        final akZ mainDevices;      // the device panel's split inside the main area
        final MainArea main;
        final akz mainCase;         // the main area's one layout
        final akZ keyboardSplit;    // the window's split above the on-screen keyboard; null: no full width
        final akn arrange, mix, edit;
        PassThroughSplitter fullWidth; // the device panel's split across the full width, once made

        Profile(Layout layout, ajv tracks, ajS devices, akw views, akZ mainDevices, MainArea main, akZ keyboardSplit, akn arrange, akn mix, akn edit) {
            this.layout = layout;
            this.tracks = tracks;
            this.devices = devices;
            this.views = views;
            this.mainDevices = mainDevices;
            this.main = main;
            this.mainCase = main.cfM(0); // its one layout
            this.keyboardSplit = keyboardSplit;
            this.arrange = arrange;
            this.mix = mix;
            this.edit = edit;
        }

        String viewOf(akn mode) {
            if (mode == arrange) return "tracks";
            if (mode == mix) return "mixer";
            if (mode == edit) return "editor";
            return null;
        }

        akn modeOf(String view) {
            return switch (view) {
                case "tracks" -> arrange;
                case "mixer" -> mix;
                case "editor" -> edit;
                default -> null;
            };
        }
    }

    // ---------- The policy ----------

    @Override
    public void layout(Layout layout) {
        akC profile = (akC) layout.profile();
        Profile built = build(layout, profile);
        if (built != null) {
            PROFILES.put(profile, built);
            placeDevices(built, PanelSides.deviceFullWidth());
        }
    }

    @Override
    public void nothingShown(Layout layout, Object viewSettings) {
        Profile p = PROFILES.get((akC) layout.profile());
        if (p == null || !(viewSettings instanceof gJi settings)) return;
        String view = p.viewOf(settings.vpw()); // vpw(): the current mode
        layout.showOnly(settings, MOD_ID, view != null ? view : "tracks");
    }

    /** Bitwig's three views alone through their mode: its preset shows the view, and the mode follows. */
    @Override
    public boolean showAlone(Layout layout, Object viewSettings, String modId, String viewId) {
        Profile p = PROFILES.get((akC) layout.profile());
        if (p == null || !MOD_ID.equals(modId) || !(viewSettings instanceof gJi settings)) return false;
        akn mode = p.modeOf(viewId);
        if (mode == null) return false;
        settings.cfM(mode); // the mode change runs the preset (mode-preset hook)
        return true;
    }

    // ---------- The layout ----------

    /** Gives the profile's first window one stack for all three modes; null if it can't. */
    private static Profile build(Layout layout, akC profile) {
        akn arrange = profile.IwV("ARRANGE"); // the profile's modes, by name
        akn mix = profile.IwV("MIX");
        akn edit = profile.IwV("EDIT");
        akq oldMain = profile.uWE() > 0 ? findMain(profile.cfM(0).TMF()) : null; // in the first window's layout
        if (arrange == null || mix == null || edit == null || !(oldMain != null && oldMain.uva() instanceof akZ parent)) {
            PanelSides.MOD.log("Display profile " + profile.MIO() + " has no ARRANGE / MIX / EDIT main area; "
                    + "its views stay as Bitwig builds them.");
            return null;
        }

        ajv tracks = ajv.MRY("arranger");
        tracks.lYc(true);  // can be hidden: has its own "is shown"
        tracks.TtY(true);  // shown by default
        ajP editor = ajP.cfM("detail_editor");
        editor.cfM(ajN.cfM); // follows the arranger or the mixer, whichever is in use (as in EDIT)
        editor.lYc(true);
        editor.TtY(false);
        akj mixer = akj.MRY("mixer"); // with the big mixer's settings (as in MIX)
        mixer.kFU(true);
        mixer.BNq(true);
        mixer.lYc(true);
        mixer.TtY(false);
        ((akx) mixer).MRY((akQ) mixer); // windows docked from the mixer stay in the mixer (as in MIX)
        ajS devices = ajS.cfM("devices");
        devices.lYc(true);
        devices.TtY(true);

        layout.bind("tracks", tracks);
        layout.bind("editor", editor);
        layout.bind("mixer", mixer);
        akw views = (akw) layout.stack("views", "tracks", "editor", "mixer"); // the framework's stack: these, then the mods' views

        akZ mainDevices = akZ.cfM("main_devices_splitter", false, views, devices);
        mainDevices.cfM(dVx.MRY); // the device panel keeps its height
        ((akx) mainDevices).MRY((akQ) tracks); // device windows dock into the arranger (as in ARRANGE)

        MainArea main = MainArea.create(oldMain.tSS()); // the same name as Bitwig's, so the paths start the same
        main.cfM(mainDevices, arrange, mix, edit); // one layout for all three modes
        if (parent.E3() == oldMain) parent.MRY(main); // the first side
        else parent.IwV(main);                       // the second side

        if (edit.iHb()) { // EDIT is a toggle-back mode: choosing it again went back to the previous mode
            profile.FzB(edit);  // out of the toggle-back modes
            edit.cfM(false);
            profile.IwV(edit);  // into the plain modes, after ARRANGE and MIX
        }

        akZ keyboardSplit = findKeyboardSplit(profile.cfM(0).TMF());
        if (keyboardSplit == null) {
            PanelSides.MOD.log("Display profile " + profile.MIO() + " has no on-screen keyboard split; "
                    + "its device panel stays between the side panels.");
        }
        return new Profile(layout, tracks, devices, views, mainDevices, main, keyboardSplit, arrange, mix, edit);
    }

    /** The top/bottom split with the on-screen keyboard below: everything else of the window is above it. */
    private static akZ findKeyboardSplit(akw node) {
        if (!(node instanceof akZ splitter)) return null;
        if (!splitter.lYc() && splitter.wGc() instanceof akt) return splitter; // lYc(): left/right; akt: the on-screen keyboard
        akZ found = findKeyboardSplit(splitter.E3());
        return found != null ? found : findKeyboardSplit(splitter.wGc());
    }

    /** The main view area: the layout part with one layout per mode. */
    private static akq findMain(akw node) {
        if (node instanceof akq main) return main;
        if (!(node instanceof akZ splitter)) return null;
        akq main = findMain(splitter.E3());
        return main != null ? main : findMain(splitter.wGc());
    }

    // ---------- The device panel: inside the main area, or across the full width ----------

    /** Places every profile's device panel across the full width or inside the main area (live apply). */
    static void placeDevicesEverywhere(boolean fullWidth) {
        List<Profile> profiles;
        synchronized (PROFILES) {
            profiles = new ArrayList<>(PROFILES.values());
        }
        for (Profile p : profiles) placeDevices(p, fullWidth);
    }

    private static void placeDevices(Profile p, boolean full) {
        try {
            if (p.keyboardSplit == null || full == isFullWidth(p)) return;
            if (full) {
                if (p.fullWidth == null) {
                    String inner = p.mainDevices.tSS(); // the device panel's split inside the main area: its path is shared
                    p.fullWidth = PassThroughSplitter.create(inner, () -> p.main.casePath(inner));
                    p.fullWidth.cfM(dVx.MRY);               // the device panel keeps its height
                    ((akx) p.fullWidth).MRY((akQ) p.tracks); // device windows dock into the arranger
                }
                p.mainDevices.MRY(standIn());              // takes out the views
                p.mainDevices.IwV(standIn());              // takes out the device panel
                p.main.nameThrough(p.views, p.mainDevices.tSS());
                p.mainCase.cfM(p.views);                   // the views straight in the main area
                akw upper = p.keyboardSplit.E3();          // the Inspector, main area and Browser panel
                p.keyboardSplit.MRY(standIn());
                p.fullWidth.MRY(upper);
                p.fullWidth.IwV(p.devices);
                p.keyboardSplit.MRY(p.fullWidth);
            } else {
                akw upper = p.fullWidth.E3();
                p.fullWidth.MRY(standIn());
                p.fullWidth.IwV(standIn());
                p.keyboardSplit.MRY(upper);
                p.mainCase.cfM(p.mainDevices);             // takes out the views
                p.main.nameThrough(null, null);
                p.mainDevices.MRY(p.views);
                p.mainDevices.IwV(p.devices);
            }
        } catch (Throwable failure) {
            PanelSides.MOD.log("Could not move the device panel", failure);
        }
    }

    private static boolean isFullWidth(Profile p) {
        return p.fullWidth != null && p.keyboardSplit.E3() == p.fullWidth; // E3(): its upper part
    }

    private static akq standIn() {
        return akq.cfM("panel-sides-stand-in");
    }

    /** Whether the panel is a profile's device panel. */
    static boolean isDevices(Object panel) {
        synchronized (PROFILES) {
            for (Profile p : PROFILES.values()) {
                if (panel == p.devices) return true;
            }
        }
        return false;
    }

    // ---------- Modes as presets ----------

    /** A mode is being chosen in a project's view settings: show its view alone. */
    static void modeChosen(rsB settings, akn mode) {
        if (!(settings instanceof gJi viewSettings) || mode == null) return;
        Profile p = PROFILES.get(viewSettings.IwV()); // the settings' profile
        if (p == null) return;
        String view = p.viewOf(mode);
        if (view != null) p.layout.showOnly(viewSettings, MOD_ID, view);
    }

    // ---------- The device panel's button and action ----------

    /**
     * An action, and the device panel button: shows the project view's device panel, or hides it.
     * It flips the panel's own "is shown", whatever has focus and whichever views are shown
     * (Bitwig's toggles focus the panel first, or reach the mixer's own device section).
     */
    static void toggleDevices(aBW window) {
        if (window == null) return;
        gJi settings = window.RDJ();
        Profile p = PROFILES.get(settings.IwV());
        if (p == null) return;
        AQw shown = p.devices.cfM(settings);
        if (shown != null) shown.setBoolValue(!shown.getBoolValue());
    }

    /** The row in place of the per-mode panel buttons: the device panel's button; null without the layout. */
    static dve deviceButton(akC profile, aBW window, IBe style) {
        Profile p = PROFILES.get(profile);
        if (p == null) return null;
        dWb row = dWb.Hnt(style);
        row.UNj(0.5F);
        AQw shown = p.devices.cfM(window.RDJ());
        if (shown != null) {
            dAd button = p.devices.FzB(window); // the device panel's icon and style
            button.cfM((dws) dmQ.cfM(TOGGLE_DEVICES, (Runnable) () -> toggleDevices(window))); // click: shows or hides it, in one click
            button.z3I("Device panel: click to show or hide it"); // the tooltip (a right-click scale menu comes with Panel: Scale Each Panel, on Bitwig's button)
            row.HL(lit(button, p.devices, shown)); // bound after the action is set (setting it unbinds)
        }
        return row;
    }

    /** The bottom bar's styling, and the button lit while the panel is shown (as Bitwig's bottom bar does). */
    private static dAd lit(dAd button, akQ panel, AQw shown) {
        button.MRY(kn.cfM());
        button.MRY(button.LIK(), shown); // selected while shown
        if (panel.sut().E3()) { // a touch window
            button.a_(w3.UNj);
            button.MRY(kn.cfM());
        } else {
            button.a_(w3.Hnt);
        }
        return button;
    }
}
