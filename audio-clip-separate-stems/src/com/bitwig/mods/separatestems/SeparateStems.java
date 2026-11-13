package com.bitwig.mods.separatestems;

import bml.actions.ActionContext;
import bml.api.Bml;
import bml.api.Settings;
import com.bitwig.mods.audioanalysis.AudioAnalysis;
import com.bitwig.mods.audioanalysis.AudioSelection;
import com.bitwig.mods.audioanalysis.Commands;
import com.bitwig.mods.audioanalysis.Insert;
import com.bitwig.mods.audioanalysis.Jobs;
import com.bitwig.mods.audioanalysis.ProcessRunner;
import com.bitwig.mods.audioanalysis.Ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Audio Clip: Separate Stems. Splits the selected audio clip's file into vocals, drums, bass and
 * other with Demucs (in the background, cancellable), then adds the chosen stems as new audio
 * tracks below the clip's track, starting where the clip starts: one Undo step.
 */
public final class SeparateStems {
    static final Bml.Mod MOD = Bml.mod("audio-clip-separate-stems");
    static final Settings STORE = MOD.settings();
    static final String TITLE = "Separate Stems";

    private SeparateStems() { }

    static boolean wants(String stem) {
        return STORE.getBoolean("stem_" + stem, true);
    }

    static void setWants(String stem, boolean on) {
        STORE.putBoolean("stem_" + stem, on);
    }

    static Set<String> chosen() {
        Set<String> out = new LinkedHashSet<>();
        for (String s : StemManifest.ORDER) if (wants(s)) out.add(s);
        return out;
    }

    static void run(ActionContext context) {
        AudioSelection clip = AudioSelection.of(context);
        if (clip == null) {
            Ui.notify(TITLE + " works on one audio clip that plays one audio file");
            return;
        }
        Set<String> stems = chosen();
        if (stems.isEmpty()) {
            Ui.notify(TITLE + ": choose at least one stem in Settings > Mods");
            return;
        }
        if (!AudioAnalysis.requireStems(TITLE)) return;
        Jobs.start(TITLE, control -> {
            Path cache = AudioAnalysis.cache();
            String key = AudioAnalysis.audioKey(clip.source());
            Path manifest = cache.resolve(key + ".stems.json");
            Map<String, Path> wavs = StemManifest.read(manifest);
            if (wavs == null) {
                control.phase("Separating stems");
                Path progress = cache.resolve(key + ".stems-progress.json");
                Files.deleteIfExists(progress);
                List<String> args = List.of(clip.source().getAbsolutePath(), "--stems-manifest", manifest.toString(),
                    "--stems-only", "--progress-file", progress.toString(), "--quiet");
                ProcessRunner.Result r = control.run(Commands.engine(AudioAnalysis.python(), args),
                    AudioAnalysis.engineEnvironment(), cache, "separate-stems", Duration.ofMinutes(30), null);
                Files.deleteIfExists(progress);
                if (!r.ok()) throw new IllegalStateException(failure(r));
                wavs = StemManifest.read(manifest);
                if (wavs == null) throw new IllegalStateException("the engine wrote no usable stems");
            } else {
                control.phase("Using the stems separated earlier");
            }
            List<Path> files = StemManifest.choose(wavs, stems);
            control.phase("Adding " + files.size() + " stem track" + (files.size() == 1 ? "" : "s"));
            boolean added = control.onUi(() -> Insert.audioTracks(clip, files));
            if (!added) throw new IllegalStateException("Bitwig didn't add the stems");
            Ui.info(TITLE + ": added " + String.join(", ", stems), "");
        });
    }

    /** A short reason from the engine's output (its last "Audio DNA error:" line), for the notification. */
    static String failure(ProcessRunner.Result r) {
        if (r.timedOut()) return "it took longer than 30 minutes";
        String reason = "";
        for (String line : r.output().split("\\R")) if (line.startsWith("Audio DNA error:")) reason = line.substring(16).strip();
        if (reason.length() > 160) reason = reason.substring(0, 160) + "…";
        return reason.isEmpty() ? "the engine stopped (exit " + r.exitCode() + "); details in the mod log" : reason;
    }
}
