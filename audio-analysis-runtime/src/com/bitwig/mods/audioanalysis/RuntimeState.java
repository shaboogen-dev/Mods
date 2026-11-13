package com.bitwig.mods.audioanalysis;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * What the setup finished, kept in {@code state.properties} in the runtime's home. A part counts as
 * set up only when its state says so and its files are still there, so deleting the folder (or
 * the venv) by hand simply means "not set up".
 */
public final class RuntimeState {
    public static final String CORE = "core";
    public static final String STEMS = "stems";
    public static final String PYTHON = "python";
    public static final String PYTHON_VERSION = "python.version";

    private RuntimeState() { }

    public static Properties read(Path file) {
        Properties p = new Properties();
        if (file == null || !Files.isRegularFile(file)) return p;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException | RuntimeException e) {
            return new Properties();
        }
        return p;
    }

    public static void write(Path file, Properties p) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            p.store(w, "Helper: Audio Analysis setup state");
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        AudioAnalysis.stateChanged();
    }

    public static void set(Path file, String key, String value) throws IOException {
        Properties p = read(file);
        if (value == null) p.remove(key);
        else p.setProperty(key, value);
        write(file, p);
    }

    /** The core part (Audio to MIDI, Detect Key) is usable. */
    public static boolean coreReady(RuntimePaths paths) {
        return "ok".equals(read(paths.state()).getProperty(CORE)) && Files.isRegularFile(paths.python());
    }

    /** Stem separation is usable: the core, demucs and the model on disk (so nothing downloads at run time). */
    public static boolean stemsReady(RuntimePaths paths) {
        return coreReady(paths) && "ok".equals(read(paths.state()).getProperty(STEMS)) && hasModel(paths.models());
    }

    /**
     * The separation model is on disk: Demucs keeps it in the Hugging Face cache
     * ({@code huggingface/hub/.../*.safetensors}) or, from its older host, as a
     * {@code hub/checkpoints/*.th} file in TORCH_HOME. Both are under {@code models/}.
     */
    public static boolean hasModel(Path models) {
        if (!Files.isDirectory(models)) return false;
        try (var files = Files.walk(models, 8)) {
            return files.anyMatch(f -> {
                String n = f.getFileName().toString();
                return (n.endsWith(".th") || n.endsWith(".safetensors")) && f.toFile().length() > 0;
            });
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
