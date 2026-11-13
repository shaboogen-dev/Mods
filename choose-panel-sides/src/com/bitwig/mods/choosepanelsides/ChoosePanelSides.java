package com.bitwig.mods.choosepanelsides;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import bitwig.obf.ajL; // a tab group (several panels, one shown)
import bitwig.obf.aka; // a panel or tab group: which end of the bottom bar its buttons go to
import bitwig.obf.akC; // a display profile: its windows and their layouts
import bitwig.obf.akM; // the layout root of one window
import bitwig.obf.akQ; // a panel
import bitwig.obf.akT; // the Project panel
import bitwig.obf.akW; // the Browser panel
import bitwig.obf.akZ; // a splitter between two parts of a window's layout
import bitwig.obf.akc; // the Inspector panel
import bitwig.obf.akf; // the Mappings panel
import bitwig.obf.akq; // a layout part used here as a stand-in
import bitwig.obf.akw; // a part of a window's layout
import bitwig.obf.alc; // the Studio I/O panel
import bitwig.obf.dVx; // which side of a splitter keeps its size
import bitwig.obf.doH; // the dialog windows (plug-in windows, undocked views)
import bitwig.obf.dpN; // Bitwig's UI event loop
import bml.api.Bml;
import bml.api.Settings;
import com.bitwig.flt.app.CHR; // the application (its windows)

/**
 * Panel: Choose Sides (mod ID choose-panel-sides). Puts the Inspector and the Browser panel each on
 * the left or the right of the window.
 *
 * Framework: Panels runs this mod's layout policy (SidesPolicy) on every display profile Bitwig
 * builds, after the other layout mods have built their layouts and the views are placed: every
 * left/right splitter that has the Inspector on one side, or the Browser panel (or a panel area
 * holding it) on one side, gets that panel on the side the settings ask for. The splitter's fixed
 * size moves with the panel, so a panel keeps its width. A panel area that holds the Inspector as
 * well as the Browser follows the Browser setting. A change of a setting rebuilds the window at once.
 */
public final class ChoosePanelSides {
    static final Bml.Mod MOD = Bml.mod("choose-panel-sides");
    private static final Settings STORE = MOD.settings();
    private static final String INSPECTOR_KEY = "inspector_side";
    private static final String BROWSER_KEY = "browser_side";

    static final int LEFT = 0;
    static final int RIGHT = 1;

    /** The sides this session uses: read at startup, then changed with the settings. */
    private static volatile int inspectorSide = stored(INSPECTOR_KEY, LEFT);
    private static volatile int browserSide = stored(BROWSER_KEY, RIGHT);

    /** Every display profile this mod has arranged, so a change of a setting can arrange them again. */
    private static final List<akC> PROFILES = Collections.synchronizedList(new ArrayList<>());

    private ChoosePanelSides() {
    }

    // ---------- Settings (stored, and applied at once) ----------

    static int inspectorSide() {
        return inspectorSide;
    }

    static void setInspectorSide(int side) {
        if (side != LEFT && side != RIGHT) return;
        STORE.putInt(INSPECTOR_KEY, side);
        if (side == inspectorSide) return;
        inspectorSide = side;
        relayOut();
    }

    static int browserSide() {
        return browserSide;
    }

    static void setBrowserSide(int side) {
        if (side != LEFT && side != RIGHT) return;
        STORE.putInt(BROWSER_KEY, side);
        if (side == browserSide) return;
        browserSide = side;
        relayOut();
    }

    private static int stored(String key, int fallback) {
        int side = STORE.getInt(key, fallback);
        return side == LEFT || side == RIGHT ? side : fallback;
    }

    // ---------- Applying a changed setting at once ----------

    /**
     * Arranges every profile again with the settings and rebuilds the window, as Bitwig does when a
     * display profile is chosen in Settings: after the current event, with the documents' updates
     * held (puu / sc) and the open dialog windows hidden and shown again around the rebuild. The
     * project's view settings stay, and the paths don't change, so sizes and views stay as they were.
     */
    private static void relayOut() {
        dpN.TVZ().asyncExec(() -> { // dpN.TVZ(): Bitwig's UI event loop
            CHR app = CHR.Grk(); // the application
            app.puu();           // holds the documents' updates
            try {
                doH.JhA();       // hides the open dialog windows (plug-in windows, undocked views)
                try {
                    List<akC> profiles;
                    synchronized (PROFILES) {
                        profiles = new ArrayList<>(PROFILES);
                    }
                    for (akC profile : profiles) arrange(profile, inspectorSide, browserSide);
                    app.kTh();   // rebuilds the windows from the profile
                } finally {
                    doH.VpW();   // shows them again
                }
            } catch (Throwable failure) {
                MOD.log("Could not apply the changed sides; restart Bitwig to apply them.", failure);
            } finally {
                app.sc();        // releases the documents' updates
            }
        });
    }

    // ---------- The layout edit ----------

    /** Puts the Inspector and the Browser panel of every window of the profile on their sides. */
    static void arrange(akC profile) {
        if (profile == null) return;
        if (!PROFILES.contains(profile)) PROFILES.add(profile);
        arrange(profile, inspectorSide, browserSide);
    }

    /** As {@link #arrange(akC)}, with the sides given. */
    private static void arrange(akC profile, int inspectorSide, int browserSide) {
        try {
            List<akZ> splitters = new ArrayList<>();
            int panels = 0;
            for (int i = 0; i < profile.uWE(); i++) { // its windows
                akM window = profile.cfM(i);
                List<akQ> columns = new ArrayList<>();
                panels += findSwaps(window.TMF(), inspectorSide, browserSide, splitters, columns); // the window's layout
                separateColumns(columns, browserSide == LEFT);
            }
            if (panels == 0) {
                MOD.log("No Inspector or Browser panel beside the main area in display profile "
                        + profile.MIO() + "; it stays as Bitwig builds it.");
                return;
            }
            for (akZ splitter : splitters) swap(splitter);
        } catch (Throwable failure) {
            MOD.log("Could not arrange display profile " + profile.MIO() + "; it stays as Bitwig builds it.", failure);
        }
    }

    /**
     * Walks a layout and collects the splitters whose panel is on the wrong side. Returns how many
     * splitters with an Inspector or Browser panel it found, on either side. The browser side's
     * panels that stand on their own (not in a tab group) are collected in {@code columns}.
     *
     * Each such panel also gets its bottom-bar button(s) on its side: Bitwig's own flag for it
     * (aka.ucQ, read as Scv()), which puts a panel's buttons in the bottom bar's left group when
     * true and the right group when false; Bitwig sets it for the Inspector in its profiles. Set
     * every time, so the stock side keeps Bitwig's value and the other side gets the mirror.
     *
     * On the right the Inspector's button also gets a divider before it (aka.Grk, read as jFL(),
     * as Bitwig gives the keyboard's): with the Browser panel also on the right, the right group
     * reads Browser panel buttons | Inspector, the mirror of the left's Inspector | Browser panel
     * buttons (whose divider is the tab group's own). Bitwig adds a divider only after another
     * button, never first or twice (ajF.cfM(dWb)), so alone in its group it shows none.
     */
    private static int findSwaps(akw node, int inspectorSide, int browserSide, List<akZ> swaps, List<akQ> columns) {
        if (!(node instanceof akZ splitter)) return 0;
        akw first = splitter.E3();  // left or top
        akw second = splitter.wGc(); // right or bottom
        int found = findSwaps(first, inspectorSide, browserSide, swaps, columns)
                  + findSwaps(second, inspectorSide, browserSide, swaps, columns);
        if (!splitter.lYc()) return found; // top and bottom, not left and right

        int side;
        boolean panelFirst;
        if (first instanceof akc || second instanceof akc) {
            side = inspectorSide;
            panelFirst = first instanceof akc;
        } else if (holdsBrowser(first) || holdsBrowser(second)) {
            side = browserSide;
            panelFirst = holdsBrowser(first);
        } else {
            return found;
        }
        if (panelFirst != (side == LEFT)) swaps.add(splitter);
        akw panel = panelFirst ? first : second;
        if (panel instanceof aka grouped) grouped.ucQ(side == LEFT); // its buttons: left group on the left
        if (panel instanceof akc inspector) inspector.Grk(side == RIGHT); // a divider before it on the right
        if (columnOrder(panel) >= 0) columns.add((akQ) panel);
        return found + 1;
    }

    /**
     * The Browser panel, a tab group with the Browser panel among its panels, or one of the browser
     * side's panels standing on its own in a splitter (Panel: Separate Browser Panels makes each a
     * column of its own; each column's splitter is swapped like the Browser's, so the columns
     * mirror: the Browser at the window's edge either way).
     */
    private static boolean holdsBrowser(akw node) {
        if (columnOrder(node) >= 0) return true;
        if (!(node instanceof ajL tabs)) return false;
        for (Object panel : tabs.lYc()) { // its panels
            if (panel instanceof akW) return true;
        }
        return false;
    }

    /** A browser-side panel's place among the columns, from the window's edge; -1 for anything else. */
    private static int columnOrder(Object node) {
        if (node instanceof akW) return 0; // Browser
        if (node instanceof akT) return 1; // Project
        if (node instanceof alc) return 2; // Studio I/O
        if (node instanceof akf) return 3; // Mappings
        return -1;
    }

    /**
     * The bottom bar's separator before the browser side's buttons when its panels are columns of
     * their own (two or more in one window), as Bitwig's tab group puts one before its row
     * (ajF.Hnt → ajF.cfM(row)): a panel's own button gets one when its flag is set (aka.Grk, read
     * as jFL() in akQ.Hnt). The first of the columns' buttons in its group: the Browser's on the
     * left (the bar reads Inspector, separator, Browser ...), the innermost column's on the right
     * (the buttons follow the columns' order on screen).
     */
    private static void separateColumns(List<akQ> columns, boolean left) {
        if (columns.size() < 2) return; // a lone panel keeps Bitwig's flag
        akQ first = null;
        for (akQ panel : columns) {
            if (first == null
                    || (left ? columnOrder(panel) < columnOrder(first) : columnOrder(panel) > columnOrder(first))) {
                first = panel;
            }
        }
        for (akQ panel : columns) panel.Grk(panel == first);
    }

    /**
     * Swaps the splitter's two sides and which of them keeps its size. A part of the layout can
     * only be placed after it has been taken out, so the second side holds a stand-in meanwhile.
     */
    private static void swap(akZ splitter) {
        akw first = splitter.E3();
        akw second = splitter.wGc();
        splitter.IwV(akq.cfM("choose-panel-sides-stand-in")); // the second side: a stand-in
        splitter.MRY(second);                                  // the first side
        splitter.IwV(first);                                   // the second side
        splitter.cfM(otherSide(splitter.LaE()));               // which side keeps its size
    }

    private static dVx otherSide(dVx sizing) {
        if (sizing == dVx.cfM) return dVx.MRY; // the first side's size → the second's
        if (sizing == dVx.MRY) return dVx.cfM; // the second side's size → the first's
        if (sizing == dVx.Hnt) return dVx.FzB; // the first side's proportion → the second's
        if (sizing == dVx.FzB) return dVx.Hnt; // the second side's proportion → the first's
        return sizing;
    }
}
