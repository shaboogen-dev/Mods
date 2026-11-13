package com.bitwig.mods.sharedtoolmode;

import bitwig.obf.B1W; // the arranger's editor state
import bitwig.obf.EkP; // the state of one Detail Editor mode (holds that mode's tool settings)
import bitwig.obf.Ha3; // an editor's tool settings: the selected tool
import bitwig.obf.WuN; // the Detail Editor's per-mode states (piano roll, drum, audio, automation, comping)
import bitwig.obf.ZHJ; // an editor's tool settings, with the tools that editor offers
import bitwig.obf.aDy; // a tool (pointer, time selection, pencil, spray, knife, eraser, audition, step input)
import bitwig.obf.tWF; // a project's UI state for one display profile (arranger, Detail Editor)
import bitwig.obf.zvs; // the Detail Editor's mode settings
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.This;
import com.bitwig.flt.document.core.master.AJH; // a project document
import com.bitwig.ramona.core.oDc; // an object in a project document

import java.util.ArrayList;
import java.util.List;

/**
 * Panel: Shared Tool Mode. The helper class: the hook, and the on/off setting.
 *
 * Each editor keeps its own tool, in a tool settings object of the project's UI state:
 * one for the arranger and one per Detail Editor mode. Every user path that changes a
 * tool (the tool buttons, the select-tool shortcuts and Commander, holding a tool's key
 * to use it for a moment, an editor switching itself back to the pointer) ends in Ha3.E3,
 * which stores the new tool. After it, the same tool is set in the other editors of that
 * UI state and of the project's other display profiles. The UI state is per display
 * profile, not per window: the windows of a Dual or Triple profile share its editors.
 * Loading a project stores the tool without Ha3.E3, so it is left alone.
 */
public final class SharedToolMode {
    static final Bml.Mod MOD = Bml.mod("shared-tool-mode");

    private static final bml.api.Settings STORE = MOD.settings();
    private static final String KEY_ENABLED = "enabled";

    /** How far up from a tool settings object its UI state may be. */
    private static final int MAX_DEPTH = 16;

    private static volatile boolean enabled = readEnabled();

    /** Logged once: a tool settings object with no UI state above it. */
    private static volatile boolean missedLogged;

    /** True while the hook sets the other editors' tools; their own passes through the hook do nothing. */
    private static final ThreadLocal<Boolean> SHARING = ThreadLocal.withInitial(() -> false);

    private SharedToolMode() {
    }

    /** After an editor's tool is stored: set the same tool in every other editor that has it. */
    @Hook(id = "share-tool", target = Ha3.class, method = "E3(LaDy;)V", at = At.BEFORE_RETURN)
    public static void afterToolSet(@This Ha3 settings) {
        if (!enabled || SHARING.get()) {
            return;
        }
        SHARING.set(true);
        try {
            aDy tool = settings.OzW(); // the tool just stored
            tWF state = uiState(settings);
            if (tool == null || state == null) {
                if (state == null && !missedLogged) {
                    missedLogged = true;
                    MOD.log("no UI state above " + settings.getClass().getName() + "; its tool is not shared");
                }
                return;
            }
            for (tWF profile : allProfiles(state)) {
                for (ZHJ other : toolSettings(profile)) {
                    if (other != settings && offers(other, tool) && other.OzW() != tool) {
                        other.cfM(tool); // Bitwig's tool setter, as the tool buttons use it
                    }
                }
            }
        } catch (Throwable t) {
            MOD.log("could not share the tool", t);
        } finally {
            SHARING.remove();
        }
    }

    /** The UI state the tool settings belong to, or null. */
    private static tWF uiState(oDc object) {
        oDc o = object;
        for (int i = 0; i < MAX_DEPTH && o != null; i++) {
            if (o instanceof tWF state) {
                return state;
            }
            o = o.K_(); // its parent in the document
        }
        return null;
    }

    /**
     * The project's UI states, one per display profile used with it: the given one first.
     * Every window of a profile (Dual, Triple) shows that profile's editors, so this covers
     * every window, and switching profile finds the same tool.
     */
    private static List<tWF> allProfiles(tWF state) {
        List<tWF> all = new ArrayList<>(4);
        all.add(state);
        oDc o = state.K_(); // its parent: the project
        for (int i = 0; i < MAX_DEPTH && o != null && !(o instanceof AJH); i++) {
            o = o.K_();
        }
        if (o instanceof AJH project && project.sc != null) { // sc: the project's UI states by display profile name
            for (Object profile : new ArrayList<>(project.sc.values())) {
                if (profile instanceof tWF other && other != state) {
                    all.add(other);
                }
            }
        }
        return all;
    }

    /** The tool settings of the arranger and of every Detail Editor mode in this UI state. */
    private static List<ZHJ> toolSettings(tWF state) {
        List<ZHJ> all = new ArrayList<>(6);
        B1W arranger = state.iAj();
        if (arranger != null) {
            add(all, arranger.nfq()); // the arranger's tool settings
        }
        zvs detail = state.SI();
        WuN modes = detail != null ? detail.luv() : null;
        if (modes != null) {
            addMode(all, modes.ejd()); // piano roll
            addMode(all, modes.uWE()); // drum editor
            addMode(all, modes.dEI()); // audio editor
            addMode(all, modes.Z1C()); // automation editor
            addMode(all, modes.dnz()); // comping editor
        }
        return all;
    }

    private static void addMode(List<ZHJ> all, EkP mode) {
        if (mode != null) {
            add(all, mode.fFM()); // that mode's tool settings
        }
    }

    private static void add(List<ZHJ> all, ZHJ settings) {
        if (settings != null) {
            all.add(settings);
        }
    }

    /** Whether the editor offers the tool (its tool bar shows it). Only piano roll and drum editor offer step input. */
    private static boolean offers(ZHJ settings, aDy tool) {
        if (tool == aDy.cfM) return settings.bv_(); // pointer
        if (tool == aDy.MRY) return settings.Hnt(); // time selection
        if (tool == aDy.Hnt) return settings.fJR(); // pencil
        if (tool == aDy.FzB) return settings.bw_(); // spray
        if (tool == aDy.IwV) return settings.MJ();  // knife
        if (tool == aDy.UNj) return settings.bx_(); // eraser
        if (tool == aDy.hUR) return settings.kFU(); // audition
        if (tool == aDy.TMF) return settings.rGR(); // step input
        return false;
    }

    static boolean enabled() {
        return enabled;
    }

    static void setEnabled(boolean value) {
        enabled = value;
        STORE.putBoolean(KEY_ENABLED, value);
    }

    private static boolean readEnabled() {
        return STORE.getBoolean(KEY_ENABLED, true);
    }
}
