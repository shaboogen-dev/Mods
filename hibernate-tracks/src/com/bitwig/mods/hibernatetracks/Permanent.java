package com.bitwig.mods.hibernatetracks;

import bitwig.obf.dtJ; // Bitwig's message dialog
import bitwig.obf.dtY; // a dialog's buttons
import bitwig.obf.dwp; // a dialog's result
import bitwig.obf.foz; // a listener
import bitwig.obf.xJo; // a track
import com.bitwig.flt.app.TL; // the application
import com.bitwig.flt.document.core.master.g2L; // a project
import com.bitwig.flt.document.core.master.zTW; // Bitwig's track operations
import com.bitwig.flt.document.core.master.zwV; // a channel

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Make Permanent: keeps the audio, deletes the deactivated source, and the pair is no longer a pair. */
final class Permanent {
    private static final String ANY_MESSAGE_KEY = "save_device_error"; // a dialog of Bitwig's whose message is only its argument; its title is replaced

    private Permanent() {
    }

    static void run(List<Object> items) {
        try {
            Map<xJo, xJo> pairs = new LinkedHashMap<>();
            g2L project = null;
            for (Object item : items) {
                if (!(item instanceof xJo track)) continue;
                Pairing.Pair pair = Pairing.find(track);
                if (pair.problem() != null) {
                    Tracks.note(pair.problem() + " Nothing was changed.");
                    return;
                }
                pairs.put(pair.source(), pair.copy());
                project = track.cc_();
            }
            if (pairs.isEmpty()) return;
            if (project.oQj() != null || project.TD2()) { // rendering, recording
                Tracks.note("Can't make a track permanent while audio is being rendered or recorded. Nothing was changed.");
                return;
            }
            g2L target = project;
            List<String> names = new ArrayList<>();
            for (xJo source : pairs.keySet()) names.add("\"" + source.cb_() + "\"");
            dtJ dialog = dtJ.cfM(ANY_MESSAGE_KEY, new Object[]{"Keep only the rendered audio and delete the original of " + String.join(", ", names)
                    + ", with its clips and devices? Wake Track won't be able to bring it back."});
            dialog.MJ("Make Permanent");
            dialog.LEd(true);
            dialog.eej(true);
            dialog.BDm(false);
            dialog.Nnn((foz) result -> {
                if (((dwp) result).Hnt() == dtY.Hnt.ordinal()) apply(target, pairs); // Hnt: the OK button
            });
            dialog.FzB(TL.hIO().SQN()); // show it on the main window
        } catch (Throwable failure) {
            Tracks.MOD.log("make permanent failed", failure);
            Tracks.note("Make Permanent failed: " + failure);
        }
    }

    /** The copy is no longer a copy of anything: the link leaves its comment and the leading ^ its name. In an open undo step. */
    static void unlink(xJo copy) {
        copy.TtY(Pairing.withoutLink(copy.AgD()));
        String name = copy.cb_();
        if (name.length() > 1 && name.startsWith("^")) copy.d_(name.substring(1)); // no longer a bounce of anything
    }

    private static void apply(g2L project, Map<xJo, xJo> pairs) {
        boolean began = false;
        try {
            project.cfM("Make Permanent", false); // open one undo step
            began = true;
            List<zwV> doomed = new ArrayList<>();
            for (Map.Entry<xJo, xJo> pair : pairs.entrySet()) {
                doomed.add(pair.getKey());
                unlink(pair.getValue());
            }
            HibernateHooks.OWN_DELETE.set(true);
            try {
                zTW.cfM(doomed, project); // Bitwig's delete tracks
            } finally {
                HibernateHooks.OWN_DELETE.remove();
            }
            project.cfM(false); // close the undo step
            began = false;
        } catch (Throwable failure) {
            Tracks.MOD.log("make permanent failed", failure);
            if (began) {
                project.cfM(false);
                project.Mb2(); // undo what was done
            }
            Tracks.note("Make Permanent failed, and nothing was changed: " + failure);
            return;
        }
        Hide.afterWake(project);
        Tracks.MOD.log("made permanent: " + pairs.size() + " tracks");
    }
}
