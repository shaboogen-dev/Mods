package com.bitwig.mods.detectkey;

import bml.actions.ActionContext;
import bml.api.Bml;
import bml.api.Settings;
import com.bitwig.mods.audioanalysis.AudioAnalysis;
import com.bitwig.mods.audioanalysis.AudioSelection;
import com.bitwig.mods.audioanalysis.Commands;
import com.bitwig.mods.audioanalysis.Dialogs;
import com.bitwig.mods.audioanalysis.Insert;
import com.bitwig.mods.audioanalysis.Jobs;
import com.bitwig.mods.audioanalysis.ProcessRunner;
import com.bitwig.mods.audioanalysis.Ui;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Audio Clip: Detect Key. Estimates the selected audio clip's key in the background, then (by the
 * setting) asks to add it to the clip's name, adds it, or only shows it. The rename is one Undo
 * step, "Detect Key".
 */
public final class DetectKey {
    static final Bml.Mod MOD = Bml.mod("audio-clip-detect-key");
    static final Settings STORE = MOD.settings();
    static final String TITLE = "Detect Key";
    static final int ASK = 0, RENAME = 1, SHOW = 2;

    private DetectKey() { }

    static int after() {
        int v = STORE.getInt("after", ASK);
        return v < ASK || v > SHOW ? ASK : v;
    }

    static void setAfter(int v) {
        STORE.putInt("after", v);
    }

    static void run(ActionContext context) {
        AudioSelection clip = AudioSelection.of(context);
        if (clip == null) {
            Ui.notify(TITLE + " works on one audio clip that plays one audio file");
            return;
        }
        if (!AudioAnalysis.requireCore(TITLE)) return;
        Object window = context.widget();
        String clipName = clip.name();
        int after = after();
        Jobs.start(TITLE, control -> {
            control.phase("Detecting the key");
            Path script = MOD.resource("estimator"); // audio_clip_key.py, shipped with this mod
            ProcessRunner.Result r = control.run(
                Commands.script(AudioAnalysis.python(), script, List.of(clip.source().getAbsolutePath())),
                AudioAnalysis.engineEnvironment(), AudioAnalysis.cache(), "detect-key", Duration.ofMinutes(10), null);
            List<String> lines = List.of(r.output().split("\\R"));
            KeyNames.Result key = r.ok() ? KeyNames.parse(lines) : null;
            if (key == null) throw new IllegalStateException(r.timedOut() ? "it took longer than 10 minutes" : "no key found");
            String shown = key.name() + " (" + KeyNames.percent(key.confidence()) + ")";
            String renamed = KeyNames.withKey(clipName, KeyNames.shortKey(key.name()));
            String relative = KeyNames.relative(key.name());
            control.onUi(() -> {
                switch (after) {
                    case SHOW -> Ui.result(TITLE + ": " + shown, relative == null ? "" : "Relative key " + relative + ", which has the same notes.");
                    case RENAME -> rename(clip, renamed, shown);
                    default -> Dialogs.confirm(window, TITLE, List.of(
                        "Detected key: " + shown,
                        relative == null ? "" : relative + " has the same notes; the estimator chose by the tonic it heard.",
                        "",
                        "OK renames the clip to: " + renamed), () -> rename(clip, renamed, shown));
                }
                return null;
            });
        });
    }

    private static void rename(AudioSelection clip, String name, String shown) {
        if (Insert.rename(clip, name, TITLE)) Ui.info(TITLE + ": " + shown, "Clip renamed to " + name + ".");
        else Ui.warning(TITLE + ": " + shown, "The clip couldn't be renamed.");
    }
}
