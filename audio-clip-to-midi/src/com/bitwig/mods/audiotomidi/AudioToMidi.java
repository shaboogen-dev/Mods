package com.bitwig.mods.audiotomidi;

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

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Audio Clip: Audio to MIDI. Transcribes the selected audio clip's melody, harmony or drums (in
 * the background, cancellable) and adds them as a MIDI clip on a new instrument track next to the
 * clip's track, from where the clip starts: one Undo step.
 */
public final class AudioToMidi {
    static final Bml.Mod MOD = Bml.mod("audio-clip-to-midi");
    static final Settings STORE = MOD.settings();
    static final String TITLE = "Audio to MIDI";
    /** Note detail in percent: higher finds more notes. The original's default was 15 (0.15). */
    static final int DEFAULT_DETAIL = 15;

    private AudioToMidi() { }

    static int detail() {
        return Math.max(5, Math.min(50, STORE.getInt("detail", DEFAULT_DETAIL)));
    }

    static void setDetail(double percent) {
        STORE.putInt("detail", (int) Math.round(Math.max(5, Math.min(50, percent))));
    }

    static boolean separateFirst() {
        return STORE.getBoolean("separate_first", true);
    }

    static void setSeparateFirst(boolean on) {
        STORE.putBoolean("separate_first", on);
    }

    /** The engine's arguments for one analysis of every target into {@code tsv}. */
    public static List<String> arguments(Path source, Path cache, String key, String sensitivity, boolean studio, Path tsv) {
        List<String> args = new ArrayList<>(List.of(source.toAbsolutePath().toString(), "--mode", "both"));
        if (studio) args.add("--studio");
        String base = key + ".s" + sensitivity + (studio ? ".studio" : "");
        args.addAll(List.of("--midi", cache.resolve(base + ".mid").toString(),
            "--json", cache.resolve(base + ".json").toString(),
            "--bridge-tsv", tsv.toString(), "--sensitivity", sensitivity, "--quiet"));
        return List.copyOf(args);
    }

    public static String sensitivity(int detailPercent) {
        return String.format(Locale.ROOT, "%.2f", detailPercent / 100.0);
    }

    static void run(ActionContext context, Target target) {
        AudioSelection clip = AudioSelection.of(context);
        if (clip == null) {
            Ui.notify(TITLE + " works on one audio clip that plays one audio file");
            return;
        }
        if (!AudioAnalysis.requireCore(TITLE)) return;
        String title = TITLE + " (" + target.label + ")";
        boolean studio = separateFirst() && AudioAnalysis.stemsReady();
        boolean hint = separateFirst() && !studio;
        String sensitivity = sensitivity(detail());
        Jobs.start(title, control -> {
            Path cache = AudioAnalysis.cache();
            String key = AudioAnalysis.audioKey(clip.source());
            Path tsv = cache.resolve(key + ".s" + sensitivity + (studio ? ".studio" : "") + ".tsv");
            if (!BridgeTsv.complete(tsv)) {
                control.phase("Transcribing");
                ProcessRunner.Result r = control.run(
                    Commands.engine(AudioAnalysis.python(), arguments(clip.source().toPath(), cache, key, sensitivity, studio, tsv)),
                    AudioAnalysis.engineEnvironment(), cache, "audio-to-midi", Duration.ofMinutes(30), null);
                if (!r.ok()) throw new IllegalStateException(failure(r));
            }
            BridgeTsv.Analysis analysis = BridgeTsv.read(tsv, target);
            List<Insert.Note> notes = BridgeTsv.toBeats(analysis, clip.lengthBeats());
            if (notes.isEmpty()) {
                Ui.warning(title + ": no notes found", target == Target.DRUMS ? "" : "Try a higher Note detail percentage in Settings > Mods > Audio to MIDI.");
                return;
            }
            control.phase("Adding " + notes.size() + " notes");
            boolean added = control.onUi(() -> Insert.midiTrack(clip, notes));
            if (!added) throw new IllegalStateException("Bitwig didn't add the clip");
            Ui.info(title + ": added " + notes.size() + " notes on a new track",
                hint ? "From the full mix: set up stem separation for cleaner results." : "");
        });
    }

    static String failure(ProcessRunner.Result r) {
        if (r.timedOut()) return "it took longer than 30 minutes";
        String reason = "";
        for (String line : r.output().split("\\R")) if (line.startsWith("Audio DNA error:")) reason = line.substring(16).strip();
        if (reason.length() > 160) reason = reason.substring(0, 160) + "…";
        return reason.isEmpty() ? "the engine stopped (exit " + r.exitCode() + "); details in the mod log" : reason;
    }
}
