package com.bitwig.mods.audioanalysis;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every command line the runtime runs, and the environment it runs them in. Pure: tested offline.
 * Nothing here runs anything; {@link ProcessRunner} does, off Bitwig's UI thread.
 */
public final class Commands {
    /** Prints "3.12" etc. */
    public static final String VERSION_SCRIPT = "import sys; print('%d.%d' % sys.version_info[:2])";
    /**
     * Imports what the core setup installs, loads Basic Pitch's model in its ONNX runtime and finds
     * ffmpeg; prints OK.
     */
    public static final String VERIFY_CORE_SCRIPT =
        "import numpy, onnxruntime, imageio_ffmpeg, basic_pitch; from basic_pitch.inference import Model;"
        + " assert basic_pitch.ICASSP_2022_MODEL_PATH.name == 'nmp.onnx', basic_pitch.ICASSP_2022_MODEL_PATH;"
        + " Model(basic_pitch.ICASSP_2022_MODEL_PATH); imageio_ffmpeg.get_ffmpeg_exe(); print('AUDIO_ANALYSIS_CORE_OK')";
    /** Fetches the htdemucs weights into HF_HOME / TORCH_HOME (run only by the confirmed stem setup). */
    public static final String FETCH_MODEL_SCRIPT =
        "import torch; from demucs.pretrained import get_model; get_model('htdemucs'); print('AUDIO_ANALYSIS_MODEL_OK')";
    /** PyTorch's CPU-only wheels, used on Linux where PyPI's torch carries CUDA (gigabytes). */
    public static final String TORCH_CPU_INDEX = "https://download.pytorch.org/whl/cpu";
    /** The torch Linux installs from {@link #TORCH_CPU_INDEX}; it satisfies requirements-stems.txt's torch==2.11.0. */
    public static final String TORCH_LINUX = "torch==2.11.0+cpu";
    /** Where an engine run writes its progress, a number 0 to 1 (audio_dna_engine/progress.py); set per run. */
    public static final String PROGRESS_FILE = "AUDIO_DNA_PROGRESS_FILE";
    /** Set for engine runs, removed for the model download: separation never downloads. */
    public static final String HF_OFFLINE = "HF_HUB_OFFLINE";

    private Commands() { }

    public static List<String> version(List<String> python) {
        return join(python, "-c", VERSION_SCRIPT);
    }

    public static List<String> createVenv(Path python, Path venv) {
        return List.of(python.toString(), "-m", "venv", venv.toString());
    }

    /**
     * Installs a lock file exactly: {@code --no-deps} (the file lists every package), wheels only
     * (nothing is compiled on the user's computer).
     */
    public static List<String> pipInstall(Path venvPython, Path requirements) {
        return List.of(venvPython.toString(), "-m", "pip", "install", "--disable-pip-version-check",
            "--no-input", "--no-deps", "--only-binary=:all:", "-r", requirements.toString());
    }

    /** Linux only: torch from PyTorch's CPU index, before the stem requirements. */
    public static List<String> pipInstallTorchCpu(Path venvPython) {
        return List.of(venvPython.toString(), "-m", "pip", "install", "--disable-pip-version-check",
            "--no-input", "--no-deps", "--only-binary=:all:", "--index-url", TORCH_CPU_INDEX, TORCH_LINUX);
    }

    public static List<String> verifyCore(Path venvPython) {
        return List.of(venvPython.toString(), "-c", VERIFY_CORE_SCRIPT);
    }

    public static List<String> fetchModel(Path venvPython) {
        return List.of(venvPython.toString(), "-c", FETCH_MODEL_SCRIPT);
    }

    /** {@code python -m audio_dna_engine <args...>}; the engine is found through PYTHONPATH. */
    public static List<String> engine(Path venvPython, List<String> args) {
        List<String> out = new ArrayList<>();
        out.add(venvPython.toString());
        out.add("-m");
        out.add("audio_dna_engine");
        out.addAll(args);
        return List.copyOf(out);
    }

    /** {@code python <script> <args...>} for a tool's own script, with the engine importable. */
    public static List<String> script(Path venvPython, Path script, List<String> args) {
        List<String> out = new ArrayList<>();
        out.add(venvPython.toString());
        out.add(script.toString());
        out.addAll(args);
        return List.copyOf(out);
    }

    /**
     * The environment for engine runs and setup steps: the inherited one, plus the engine on
     * PYTHONPATH, every cache and model folder inside the runtime's home, no user site-packages, no
     * bytecode files in the mod's folder, UTF-8 output, Hugging Face offline and without telemetry.
     * The engine's ffmpeg is always the one the setup installed (imageio-ffmpeg), never one on PATH.
     */
    public static Map<String, String> engineEnvironment(Map<String, String> inherited, RuntimePaths paths) {
        Map<String, String> env = new LinkedHashMap<>(inherited);
        env.remove(envKey(env, "PYTHONHOME"));
        String sep = Platform.pathSeparator(paths.os());
        String pyKey = envKey(env, "PYTHONPATH");
        String old = env.get(pyKey);
        env.put(pyKey, old == null || old.isBlank() ? paths.engine().toString() : paths.engine() + sep + old);
        env.put("PYTHONNOUSERSITE", "1");
        env.put("PYTHONDONTWRITEBYTECODE", "1");
        env.put("PYTHONIOENCODING", "utf-8");
        env.put("PYTHONUTF8", "1");
        env.put("PYTHONUNBUFFERED", "1"); // output reaches the log as it is printed: the setup reads pip's progress from it
        env.put("PIP_DISABLE_PIP_VERSION_CHECK", "1");
        env.put("PIP_NO_CACHE_DIR", "1"); // no pip cache in the user's folders
        env.put("PIP_CONFIG_FILE", paths.os() == Platform.Os.WINDOWS ? "nul" : "/dev/null"); // no user pip.conf: the locked index
        env.remove(envKey(env, "PIP_INDEX_URL"));
        env.remove(envKey(env, "PIP_EXTRA_INDEX_URL"));
        env.put("TORCH_HOME", paths.models().toString());
        env.put("HF_HOME", paths.huggingFace().toString());
        env.put(HF_OFFLINE, "1");
        env.put("HF_HUB_DISABLE_TELEMETRY", "1");
        env.put("HF_HUB_DISABLE_SYMLINKS_WARNING", "1");
        env.put("AUDIO_DNA_STEMS_CACHE", paths.stemsCache().toString());
        env.put("MPLCONFIGDIR", paths.cache().resolve("matplotlib").toString());
        env.put("NUMBA_CACHE_DIR", paths.cache().resolve("numba").toString());
        env.put("TF_CPP_MIN_LOG_LEVEL", "2");
        return env;
    }

    /** {@code env} with downloads allowed (the stem setup's model step). */
    public static Map<String, String> online(Map<String, String> env) {
        Map<String, String> out = new LinkedHashMap<>(env);
        out.remove(HF_OFFLINE);
        return out;
    }

    private static String envKey(Map<String, String> env, String key) {
        for (String k : env.keySet()) if (k.equalsIgnoreCase(key)) return k;
        return key;
    }

    private static List<String> join(List<String> prefix, String... rest) {
        List<String> out = new ArrayList<>(prefix);
        out.addAll(List.of(rest));
        return List.copyOf(out);
    }
}
