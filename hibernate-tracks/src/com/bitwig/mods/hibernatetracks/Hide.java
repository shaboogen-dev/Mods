package com.bitwig.mods.hibernatetracks;

import bitwig.obf.tWF; // a project's view settings
import bitwig.obf.xJo; // a track
import bml.api.Settings;
import com.bitwig.flt.document.core.master.g2L; // a project

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Bitwig's hide deactivated tracks button. It's a setting of each project, so what this mod did is
 * remembered per project, by the project's own UUID, in the mod's settings. The project's file only
 * gets the setting Bitwig already saves.
 */
final class Hide {
    private static final Settings STORE = Tracks.MOD.settings();

    /** The view settings this mod hid deactivated tracks in, this session: for the diagnostic trace only. */
    private static final Set<Object> HIDDEN_BY_MOD = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

    /** Set while this mod shows deactivated tracks itself. */
    private static final ThreadLocal<Boolean> SHOWING = ThreadLocal.withInitial(() -> false);

    private Hide() {
    }

    private static String key(g2L project) {
        return "turned_on_hide." + project.jFL();
    }

    /** Whether this mod turned on hide deactivated tracks in this project and the option is on. */
    static boolean owns(g2L project) {
        return HibernateSettings.autoHide() && STORE.getBoolean(key(project), false);
    }

    /** Notes the project's view settings for the trace below. */
    static void watch(g2L project) {
        HIDDEN_BY_MOD.add(project.EV());
    }

    /** After a Hibernate: hide them if they're showing, and remember it was this mod. If they're hidden already, nothing. */
    static void afterHibernate(g2L project) {
        if (!HibernateSettings.autoHide()) return;
        tWF view = project.EV();
        if (!view.SQN()) return; // SQN: deactivated tracks are shown
        view.Hnt(false);
        HIDDEN_BY_MOD.add(view);
        STORE.putBoolean(key(project), true);
        Tracks.MOD.log("hide deactivated tracks turned on");
    }

    /** After a Wake, Make Permanent or delete: show them again only if this mod hid them and no hibernated track is left in this project. */
    static void afterWake(g2L project) {
        if (!STORE.getBoolean(key(project), false)) return;
        if (anyCopy(project)) return;
        STORE.remove(key(project)); // the last hibernated track is gone: forget the project, even with the option off
        tWF view = project.EV();
        HIDDEN_BY_MOD.remove(view);
        if (!HibernateSettings.autoHide()) return;
        boolean hidden = !view.SQN();
        if (hidden) show(view);
        Tracks.MOD.log(hidden ? "hide deactivated tracks turned off" : "hide deactivated tracks was already off: left alone");
    }

    /**
     * After an Undo or Redo that began with the mod's hide on: if it turned deactivated tracks back
     * on and hibernated tracks remain, hide them again. Bitwig doesn't record the setting for Undo.
     */
    static void keepAfterUndo(g2L project, String what) {
        tWF view = project.EV();
        if (!view.SQN() || !owns(project) || !anyCopy(project)) return;
        view.Hnt(false);
        HIDDEN_BY_MOD.add(view);
        Tracks.MOD.log(what + " showed deactivated tracks: hidden again");
    }

    /** Logs who shows deactivated tracks in a project where the mod hid them, with the call stack. */
    static void traceShow(Object view) {
        if (SHOWING.get() || !HIDDEN_BY_MOD.contains(view)) return;
        if (view instanceof tWF settings && settings.SQN()) return; // already shown: no change
        Tracks.MOD.log("deactivated tracks shown while this mod hides them (the button, an Undo or Bitwig):",
                new Throwable("call stack"));
    }

    private static void show(tWF view) {
        SHOWING.set(true);
        try {
            view.Hnt(true);
        } finally {
            SHOWING.remove();
        }
    }

    private static boolean anyCopy(g2L project) {
        for (xJo track : Pairing.allTracks(project)) {
            if (Pairing.sourceId(track) != null) return true;
        }
        return false;
    }
}
