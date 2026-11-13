package com.bitwig.mods.separatebrowserpanels;

import java.util.List;

import bitwig.obf.ajL; // a tab group (several panels, one shown)
import bitwig.obf.akC; // a display profile: its windows and their layouts
import bitwig.obf.akQ; // a panel
import bitwig.obf.akT; // the Project panel
import bitwig.obf.akW; // the Browser panel
import bitwig.obf.akZ; // a splitter between two parts of a window's layout
import bitwig.obf.akc; // the Inspector panel
import bitwig.obf.akf; // the Mappings panel
import bitwig.obf.akw; // a part of a window's layout
import bitwig.obf.alc; // the Studio I/O panel
import bml.api.Bml;
import bml.panels.Layout;
import bml.panels.LayoutPolicy;
import bml.panels.PanelProvider;
import bml.panels.Panels;

/**
 * Panel: Separate Browser Panels (mod ID separate-browser-panels). The browser side's four panels
 * as columns of their own, through Framework: Panels.
 *
 * The registration declares the four Bitwig panels (Browser, Project, Studio I/O, Mappings) with
 * Bitwig's own icons, so the framework gives each its Show and split actions and its button's
 * clicks, and a layout policy. In the first window of each display profile, the policy replaces the
 * browser-side tab group (those four panels and nothing else) with the framework's side columns of
 * four new panels of those classes, in the splitter that held the group: the Browser at the
 * window's edge, then Project, Studio I/O and Mappings towards the main area, each full height with
 * its own width, shown and hidden on its own. A group that also holds the Inspector stays as
 * Bitwig builds it. Their scales are Panel: Scale Each Panel's, when that mod is on.
 */
public final class SeparateBrowserPanels implements PanelProvider, LayoutPolicy {
    static final Bml.Mod MOD = Bml.mod("separate-browser-panels");

    @Override
    public void declare(Panels panels) {
        panels.bitwigView("browser", "Browser Panel").icon("browser_icon").scale(false); // scaled by Panel: Scale Each Panel, by class
        panels.bitwigView("project", "Project Panel").icon("project_icon").scale(false);
        panels.bitwigView("studio", "Studio Monitoring Panel").icon("studio_icon").scale(false);
        panels.bitwigView("mappings", "Mappings Panel").icon("mappings_icon").scale(false);
        panels.policy(new SeparateBrowserPanels());
    }

    @Override
    public void layout(Layout layout) {
        akC profile = (akC) layout.profile();
        try {
            ajL group = profile.uWE() > 0 ? findBrowserGroup(profile.cfM(0).TMF()) : null; // in the first window's layout
            if (group == null || !(group.uva() instanceof akZ parent)) {
                MOD.log("Display profile " + profile.MIO() + " has no tab group of only the Browser, Project, "
                        + "Studio I/O and Mappings panels in its first window; its browser side stays as tabs.");
                return;
            }
            boolean right = parent.wGc() == group; // wGc(): the splitter's second (right) part
            akW browser = akW.MRY("browser"); // Bitwig's own names for them
            browser.TtY(true);                // shown by default, as Bitwig has it
            akT project = akT.cfM("song");
            project.TtY(false);
            alc studio = alc.cfM("studio");
            studio.TtY(false);
            akf mappings = akf.cfM("mappings");
            mappings.TtY(false);
            layout.bind("browser", browser);
            layout.bind("project", project);
            layout.bind("studio", studio);
            layout.bind("mappings", mappings);
            layout.sideColumns(parent, group, "browser", "project", "studio", "mappings"); // the Browser at the edge
            separateButtons(List.of(browser, project, studio, mappings), right);
        } catch (Throwable failure) {
            MOD.log("Could not make the browser side's separate panels in display profile " + profile.MIO()
                    + "; it stays as tabs.", failure);
        }
    }

    /** A tab group whose panels are only (and all four of) the Browser, Project, Studio I/O and Mappings. */
    private static ajL findBrowserGroup(akw node) {
        if (node instanceof ajL group) {
            boolean browser = false;
            int panels = 0;
            for (Object panel : group.lYc()) { // its panels
                if (panel instanceof akc) return null; // shared with the Inspector: left as it is
                if (!(panel instanceof akW || panel instanceof akT || panel instanceof alc || panel instanceof akf)) return null;
                browser |= panel instanceof akW;
                panels++;
            }
            return browser && panels == 4 ? group : null;
        }
        if (!(node instanceof akZ splitter)) return null;
        ajL found = findBrowserGroup(splitter.E3());
        return found != null ? found : findBrowserGroup(splitter.wGc());
    }

    /**
     * The bottom bar's separator before the columns' buttons, as Bitwig's tab group puts one before
     * its row (ajF.Hnt → ajF.cfM(row)): a panel's own button gets one when its flag is set (aka.Grk,
     * read as jFL() in akQ.Hnt). The first of the columns' buttons in its group: the Browser's on the
     * left, the innermost column's on the right (the buttons follow the columns' order on screen).
     * Panel: Choose Sides sets it again when it moves the columns to the other side.
     */
    private static void separateButtons(List<akQ> columns, boolean right) {
        akQ first = right ? columns.get(columns.size() - 1) : columns.get(0);
        for (akQ panel : columns) panel.Grk(panel == first);
    }
}
