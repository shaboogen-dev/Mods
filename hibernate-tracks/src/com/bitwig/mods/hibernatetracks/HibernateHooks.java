package com.bitwig.mods.hibernatetracks;

import bitwig.obf.dfI; // a project's undo history
import bitwig.obf.geM; // a project's view settings (tWF is the one project.EV() gives)
import bitwig.obf.xJo; // a track
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.This;
import com.bitwig.flt.document.core.master.g2L; // a project
import com.bitwig.flt.document.core.master.ruS; // a group track
import com.bitwig.flt.document.core.master.zTW; // Bitwig's track operations

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * The mod's hooks.
 *
 * Deleting: every track delete in Bitwig ends in zTW.cfM(List, g2L), inside Bitwig's own "Delete
 * Tracks" undo step. Deleting a hibernated copy adds its deactivated source to that list, so both
 * go in one step and Undo brings both back, still paired. Deleting a source while its copy stays
 * unlinks the copy in the same step, as Make Permanent does: it becomes a plain audio track.
 *
 * Undo and Redo: Bitwig doesn't record "show deactivated tracks" for Undo (its field has no undo
 * flag), yet testers saw an Undo turn it back on. While this mod hides deactivated tracks in a
 * project that still has hibernated tracks, an Undo or Redo that turns it on is put back. Whatever
 * turns it on while the mod owns the hide is logged with its call stack, to find the writer.
 */
public final class HibernateHooks {
    /** Set while this mod deletes tracks itself (Wake, Make Permanent): the delete hook stays out. */
    static final ThreadLocal<Boolean> OWN_DELETE = ThreadLocal.withInitial(() -> false);

    /** The project of a delete this mod joined, for the hide update once Bitwig has removed the tracks. */
    private static final ThreadLocal<g2L> DELETED_IN = new ThreadLocal<>();

    /** Whether deactivated tracks were hidden when an Undo or Redo began, and in which project. */
    private static final ThreadLocal<g2L> UNDO_PROJECT = new ThreadLocal<>();

    private HibernateHooks() {
    }

    // ---------- Deleting ----------

    @Hook(id = "delete-pairs-together", target = zTW.class,
          method = "cfM(Ljava/util/List;Lcom/bitwig/flt/document/core/master/g2L;)V", at = At.ENTRY)
    public static void beforeDelete(@Arg(0) List<Object> tracks, @Arg(1) g2L project) {
        DELETED_IN.remove();
        if (OWN_DELETE.get() || tracks == null || project == null) return;
        try {
            if (joinPairs(tracks, project)) DELETED_IN.set(project);
        } catch (Throwable failure) {
            Tracks.MOD.log("could not delete hibernated pairs together; Bitwig deletes only what was chosen", failure);
        }
    }

    @Hook(id = "after-delete-pairs", target = zTW.class,
          method = "cfM(Ljava/util/List;Lcom/bitwig/flt/document/core/master/g2L;)V", at = At.BEFORE_RETURN)
    public static void afterDelete() {
        g2L project = DELETED_IN.get();
        DELETED_IN.remove();
        if (project == null) return;
        try {
            Hide.afterWake(project); // the last hibernated track gone: the mod's hide is released
        } catch (Throwable failure) {
            Tracks.MOD.log("could not update hide deactivated tracks after a delete", failure);
        }
    }

    /**
     * Adds each deleted copy's source to the list, and unlinks each copy whose source is deleted
     * without it. Returns true if a hibernated pair was involved.
     */
    private static boolean joinPairs(List<Object> tracks, g2L project) {
        Set<xJo> deleted = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object item : tracks) {
            if (item instanceof xJo track) deleted.add(track);
            if (item instanceof ruS group) {
                for (Object inner : group.BBT()) if (inner instanceof xJo track) deleted.add(track); // its tracks, nested ones too
            }
        }
        if (deleted.isEmpty()) return false;
        List<xJo> all = Pairing.allTracks(project);
        boolean involved = false;
        List<String> added = new ArrayList<>();
        for (xJo track : new ArrayList<>(deleted)) {
            java.util.UUID source = Pairing.sourceId(track);
            if (source != null) { // a copy: its source goes too
                xJo found = null;
                int count = 0;
                for (xJo other : all) {
                    if (source.equals(other.kFU())) {
                        found = other;
                        count++;
                    }
                }
                if (count == 1 && !deleted.contains(found)) {
                    tracks.add(found);
                    deleted.add(found);
                    added.add("\"" + found.cb_() + "\"");
                }
                involved |= count > 0;
                continue;
            }
            java.util.UUID id = track.kFU();
            if (id == null) continue;
            for (xJo other : all) { // a source: copies that stay become plain audio tracks
                if (id.equals(Pairing.sourceId(other)) && !deleted.contains(other)) {
                    Permanent.unlink(other);
                    involved = true;
                    Tracks.MOD.log("source of \"" + other.cb_() + "\" deleted: unlinked it");
                }
            }
        }
        if (!added.isEmpty()) Tracks.MOD.log("deleting the hibernated sources too: " + String.join(", ", added));
        return involved;
    }

    // ---------- Undo and Redo ----------

    // dfI is the project's undo manager: tp2() is Undo, MIO() is Redo (g2L.XO / acL call them). Hooked
    // here because the map packs translate these on every version; g2L.XO and acL have no rows.

    @Hook(id = "undo-start", target = dfI.class, method = "tp2()V", at = At.ENTRY)
    public static void beforeUndo(@This dfI history) {
        noteHide(projectOf(history));
    }

    @Hook(id = "undo-end", target = dfI.class, method = "tp2()V", at = At.BEFORE_RETURN)
    public static void afterUndo() {
        keepHide("Undo");
    }

    @Hook(id = "redo-start", target = dfI.class, method = "MIO()V", at = At.ENTRY)
    public static void beforeRedo(@This dfI history) {
        noteHide(projectOf(history));
    }

    @Hook(id = "redo-end", target = dfI.class, method = "MIO()V", at = At.BEFORE_RETURN)
    public static void afterRedo() {
        keepHide("Redo");
    }

    private static volatile boolean rootLogged;

    /** The project an undo manager belongs to (the document it was made for), or null. */
    private static g2L projectOf(dfI history) {
        Object root = history.IwV(); // the document the undo manager was made for
        if (root instanceof g2L project) return project;
        if (!rootLogged) {
            rootLogged = true;
            Tracks.MOD.log("undo history isn't a project's (" + (root == null ? "null" : root.getClass().getName()) + "): left alone");
        }
        return null;
    }

    private static void noteHide(g2L project) {
        UNDO_PROJECT.remove();
        if (project == null) return;
        try {
            if (Hide.owns(project) && !project.EV().SQN()) { // the mod's hide is on
                Hide.watch(project); // also after a restart, for the trace
                UNDO_PROJECT.set(project);
            }
        } catch (Throwable failure) {
            Tracks.MOD.log("could not read hide deactivated tracks before an undo", failure);
        }
    }

    private static void keepHide(String what) {
        g2L project = UNDO_PROJECT.get();
        UNDO_PROJECT.remove();
        if (project == null) return;
        try {
            Hide.keepAfterUndo(project, what);
        } catch (Throwable failure) {
            Tracks.MOD.log("could not keep deactivated tracks hidden after " + what, failure);
        }
    }

    // ---------- Diagnostics: who shows deactivated tracks while the mod hides them ----------

    @Hook(id = "trace-show-deactivated", target = geM.class, method = "iHb(Z)V", at = At.ENTRY)
    public static void beforeShowDeactivated(@This geM view, @Arg(0) boolean shown) {
        if (!shown) return;
        try {
            Hide.traceShow(view);
        } catch (Throwable ignored) {
            // logging only
        }
    }
}
