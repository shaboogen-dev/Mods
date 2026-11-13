"""Optional Demucs stem-separation backend for Audio Tools "Studio" mode.

demucs (facebookresearch/demucs, MIT) is an optional heavy dependency: it
pulls PyTorch and downloads model weights on first use, so nothing here may
require it at import time.  The module therefore:

- detects availability lazily and side-effect free (:func:`demucs_status`);
- reports typed failures instead of leaking subprocess/import errors
  (:class:`StemsUnavailableError`, :class:`StemsCommandError`);
- keys its cache on source content + path/size/mtime + model + options +
  engine version, storing WAVs under ``<cache_root>/<key>/`` without ever
  touching the source audio (macOS default per docs/31:
  ``~/Library/Caches/Audio Tools/stems/<key>/``);
- runs separation through an injectable command runner (never a shell, model
  names validated, stdin closed) so the subprocess boundary is testable
  without demucs or network access;
- maps stems onto the Audio Tools MIDI targets: ``melody`` -> ``vocals``
  (falling back to the original mix when the stem is nearly silent),
  ``chords`` -> ``other``, ``bass`` -> ``bass`` and ``groove`` -> ``drums``.

Per docs/31_AUDIO_TOOLS_STEMS_SPIKE_PLAN_2026-08-31.md, professional
per-instrument stems, real-time separation and destructive edits are
explicitly out of scope.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import wave
from dataclasses import dataclass
from importlib import metadata as importlib_metadata
from pathlib import Path


def _lock_cache_file(lock) -> None:
    """Lock the cache across processes; closing the handle releases the lock."""
    if os.name != "nt":
        import fcntl
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        return
    import msvcrt
    import ctypes
    from ctypes import wintypes
    lock.seek(0, os.SEEK_END)
    if lock.tell() == 0:
        lock.write(b"\0")
        lock.flush()
    class Overlapped(ctypes.Structure):
        _fields_ = [("Internal", ctypes.c_size_t), ("InternalHigh", ctypes.c_size_t),
                    ("Offset", wintypes.DWORD), ("OffsetHigh", wintypes.DWORD),
                    ("hEvent", wintypes.HANDLE)]

    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    lock_file = kernel.LockFileEx
    lock_file.argtypes = [wintypes.HANDLE, wintypes.DWORD, wintypes.DWORD,
                         wintypes.DWORD, wintypes.DWORD, ctypes.POINTER(Overlapped)]
    lock_file.restype = wintypes.BOOL
    handle = msvcrt.get_osfhandle(lock.fileno())
    offset = Overlapped()  # Always byte zero, independent of the buffered cursor.
    while True:
        # Exclusive + fail immediately: retry only the explicit contention code.
        # CRT locking under Wine can report errno=0, losing that distinction.
        if lock_file(handle, 3, 0, 1, 0, ctypes.byref(offset)):
            return
        error = ctypes.get_last_error()
        if error != 33:  # ERROR_LOCK_VIOLATION
            raise ctypes.WinError(error)
        time.sleep(0.1)
from typing import Callable, Mapping, Sequence

import numpy as np

from . import __version__

__all__ = [
    "DEFAULT_MODEL",
    "FALLBACK_TO_MIX_MODES",
    "NEARLY_SILENT_RMS",
    "STEMS",
    "STEMS_FORMAT_VERSION",
    "STEM_FOR_MODE",
    "CommandResult",
    "DemucsStatus",
    "MidiSourceRecommendation",
    "Runner",
    "SeparationResult",
    "StudioSeparation",
    "StemsCommandError",
    "StemsError",
    "StemsUnavailableError",
    "cache_key",
    "default_cache_root",
    "default_command_runner",
    "demucs_status",
    "describe_capabilities",
    "recommend_midi_source",
    "recommend_midi_sources",
    "run_studio_separation",
    "separate_stems",
    "stems_cache_dir",
    "stems_export_manifest",
    "write_stems_manifest",
    "stem_is_audible",
]

#: Four-stem vocabulary produced by the default HTDemucs configuration.
STEMS: tuple[str, ...] = ("vocals", "drums", "bass", "other")
#: Default separation model. Kept explicit so cache keys are self-describing.
DEFAULT_MODEL = "htdemucs"
#: Bump when the on-disk cache layout or manifest contract changes.
STEMS_FORMAT_VERSION = "1"
#: Audio Tools MIDI target -> demucs stem (docs/31_AUDIO_TOOLS_STEMS_SPIKE_PLAN).
STEM_FOR_MODE: Mapping[str, str] = {
    "melody": "vocals",
    "chords": "other",
    "bass": "bass",
    "groove": "drums",
}
#: Modes that fall back to the original mix when their stem is nearly silent.
FALLBACK_TO_MIX_MODES = frozenset({"melody"})
#: Linear RMS below which a stem counts as nearly silent (about -60 dBFS).
NEARLY_SILENT_RMS = 1e-3

_MANIFEST_NAME = "manifest.json"
_MODEL_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._+-]*")
_OPTION_FLAGS: Mapping[str, str] = {
    "two_stems": "--two-stems",
    "shifts": "--shifts",
    "overlap": "--overlap",
    "segment": "--segment",
}
_WAVE_DTYPE = {1: "u1", 2: "<i2", 4: "<i4"}
_HASH_CHUNK = 1 << 20
_ERROR_TAIL = 400


class StemsError(RuntimeError):
    """Base class for stem-separation failures."""


class StemsUnavailableError(StemsError):
    """Raised when separation is requested but the demucs backend is absent."""


class StemsCommandError(StemsError):
    """Raised when the demucs process fails or produces unusable output."""


# --------------------------------------------------------------------------
# Capability detection
# --------------------------------------------------------------------------


@dataclass(frozen=True)
class DemucsStatus:
    """Capability snapshot of the optional demucs backend."""

    installed: bool
    version: str | None
    command: tuple[str, ...] | None
    details: str


def demucs_status() -> DemucsStatus:
    """Detect the optional demucs backend without importing or running it.

    Prefers the interpreter that is already running this engine (so the
    separation shares its exact environment); otherwise accepts a ``demucs``
    console script found on ``PATH``.  Detection never touches the network and
    never downloads model weights.
    """
    try:
        version = importlib_metadata.version("demucs")
    except importlib_metadata.PackageNotFoundError:
        version = None
    if version is not None:
        return DemucsStatus(
            installed=True,
            version=version,
            # BML: our own runner (audio_dna_engine.separate) instead of demucs.separate,
            # whose audio I/O needs sphn (no wheels for Intel macOS or Linux on ARM).
            command=(sys.executable, "-m", "audio_dna_engine.separate"),
            details=(
                f"demucs {version} is importable from {sys.executable}; "
                "the model is downloaded by the stem setup"
            ),
        )
    # BML: no demucs console script from PATH: only the runtime's own Demucs runs.
    return DemucsStatus(
        installed=False,
        version=None,
        command=None,
        details=(
            "demucs is not installed; enable Studio stems with "
            "'pip install -r requirements-audio-dna-stems.txt' "
            "(demucs, MIT license)"
        ),
    )


def describe_capabilities() -> dict:
    """JSON-friendly capability snapshot for UI display or logging."""
    status = demucs_status()
    return {
        "stems": list(STEMS),
        "default_model": DEFAULT_MODEL,
        "midi_modes": dict(STEM_FOR_MODE),
        "mix_fallback_modes": sorted(FALLBACK_TO_MIX_MODES),
        "cache_root": str(default_cache_root()),
        "demucs": {
            "installed": status.installed,
            "version": status.version,
            "command": list(status.command) if status.command else None,
            "details": status.details,
        },
        "note": "Separation never modifies the source audio; weights download on first use.",
    }


# --------------------------------------------------------------------------
# Command runner (injection seam for tests; never a shell)
# --------------------------------------------------------------------------


@dataclass(frozen=True)
class CommandResult:
    argv: tuple[str, ...]
    returncode: int
    stdout: str
    stderr: str


Runner = Callable[[Sequence[str]], CommandResult]


def default_command_runner(argv: Sequence[str]) -> CommandResult:
    """Run one argv list without a shell and capture its output.

    ``stdin`` is closed so the backend can never block on a prompt, and no
    timeout is imposed because separations are legitimately long.
    """
    parts = [str(part) for part in argv]
    process = subprocess.run(
        parts,
        stdin=subprocess.DEVNULL,
        capture_output=True,
        text=True,
        check=False,
    )
    return CommandResult(tuple(parts), process.returncode, process.stdout, process.stderr)


# --------------------------------------------------------------------------
# Cache key and locations
# --------------------------------------------------------------------------


def default_cache_root() -> Path:
    """Per-user cache root for stem WAVs.

    macOS follows docs/31 (``~/Library/Caches/Audio Tools/stems``); elsewhere
    the XDG base directory spec applies.  ``AUDIO_DNA_STEMS_CACHE`` overrides
    everything, which keeps tests and CI hermetic.
    """
    override = os.environ.get("AUDIO_DNA_STEMS_CACHE")
    if override:
        return Path(override).expanduser()
    if sys.platform == "darwin":
        return Path.home() / "Library" / "Caches" / "Audio Tools" / "stems"
    xdg = os.environ.get("XDG_CACHE_HOME")
    base = Path(xdg).expanduser() if xdg else Path.home() / ".cache"
    return base / "audio-tools" / "stems"


def _existing_source(source: str | Path) -> Path:
    path = Path(source).expanduser().resolve()
    if not path.is_file():
        raise StemsError(f"Source audio does not exist: {path}")
    return path


def _validated_model(model: str) -> str:
    if not isinstance(model, str) or not _MODEL_PATTERN.fullmatch(model):
        raise ValueError(f"model must be a plain demucs model name: {model!r}")
    return model


def _canonical_config(config: Mapping[str, object] | None) -> dict[str, float | int | str]:
    """Validate and normalize the options that affect separation output."""
    if not config:
        return {}
    unknown = sorted(set(config) - set(_OPTION_FLAGS))
    if unknown:
        raise ValueError(
            f"Unknown stem separation option(s): {unknown}; "
            f"supported options: {sorted(_OPTION_FLAGS)}"
        )
    canonical: dict[str, float | int | str] = {}
    two_stems = config.get("two_stems")
    if two_stems is not None:
        if two_stems not in STEMS:
            raise ValueError(f"two_stems must be one of {list(STEMS)}: {two_stems!r}")
        canonical["two_stems"] = str(two_stems)
    shifts = config.get("shifts")
    if shifts is not None:
        if not isinstance(shifts, int) or isinstance(shifts, bool) or not 1 <= shifts <= 10:
            raise ValueError("shifts must be an integer within [1, 10]")
        canonical["shifts"] = int(shifts)
    overlap = config.get("overlap")
    if overlap is not None:
        try:
            overlap_value = float(overlap)  # type: ignore[arg-type]
        except (TypeError, ValueError):
            raise ValueError(f"overlap must be a number: {overlap!r}") from None
        if not 0.0 <= overlap_value < 1.0:
            raise ValueError("overlap must be within [0, 1)")
        canonical["overlap"] = overlap_value
    segment = config.get("segment")
    if segment is not None:
        try:
            segment_value = float(segment)  # type: ignore[arg-type]
        except (TypeError, ValueError):
            raise ValueError(f"segment must be a number of seconds: {segment!r}") from None
        if not 1.0 <= segment_value <= 600.0:
            raise ValueError("segment must be within [1, 600] seconds")
        canonical["segment"] = segment_value
    return canonical


def _source_fingerprint(path: Path) -> dict[str, object]:
    """Content hash plus the cheap metadata named in docs/31."""
    stat = path.stat()
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(_HASH_CHUNK), b""):
            digest.update(chunk)
    return {
        "path": str(path),
        "size": stat.st_size,
        "mtime_ns": stat.st_mtime_ns,
        "sha256": digest.hexdigest(),
    }


def cache_key(
    source: str | Path,
    *,
    model: str = DEFAULT_MODEL,
    config: Mapping[str, object] | None = None,
) -> str:
    """Deterministic cache key: source content/metadata + model + options + versions.

    Re-encoding, editing, moving or touching the source yields a new key;
    identical inputs always map to the same 64-hex-character key.
    """
    path = _existing_source(source)
    descriptor = {
        "engine_version": __version__,
        "stems_format": STEMS_FORMAT_VERSION,
        "model": _validated_model(model),
        "config": _canonical_config(config),
        "source": _source_fingerprint(path),
    }
    payload = json.dumps(descriptor, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def _resolve_root(cache_root: str | Path | None) -> Path:
    raw = Path(cache_root).expanduser() if cache_root is not None else default_cache_root()
    return raw.expanduser()


def stems_cache_dir(
    source: str | Path,
    *,
    model: str = DEFAULT_MODEL,
    config: Mapping[str, object] | None = None,
    cache_root: str | Path | None = None,
) -> Path:
    """Directory that does (or will) hold the stems for this exact request."""
    return _resolve_root(cache_root) / cache_key(source, model=model, config=config)


# --------------------------------------------------------------------------
# Separation
# --------------------------------------------------------------------------


@dataclass(frozen=True)
class SeparationResult:
    source: str
    model: str
    config: dict
    key: str
    cache_dir: Path
    cache_hit: bool
    stems: dict[str, Path]
    command: tuple[str, ...]


def _expected_stems(canonical: Mapping[str, object]) -> set[str]:
    two_stems = canonical.get("two_stems")
    return {str(two_stems)} if two_stems else set(STEMS)


def _separation_argv(
    status: DemucsStatus,
    model: str,
    canonical: Mapping[str, object],
    out_dir: Path,
    source: Path,
) -> tuple[str, ...]:
    if status.command is None:
        raise StemsUnavailableError(status.details)
    argv: list[str] = [*status.command, "-n", model, "--out", str(out_dir)]
    for option, flag in _OPTION_FLAGS.items():
        if option in canonical:
            argv.extend([flag, str(canonical[option])])
    argv.append(str(source))
    return tuple(argv)


def _load_cached_stems(cache_dir: Path, expected: set[str]) -> dict[str, Path] | None:
    """Trust the manifest only when every listed stem WAV is still usable."""
    manifest_path = cache_dir / _MANIFEST_NAME
    if not manifest_path.is_file():
        return None
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        stored = manifest["stems"]
        mapping: dict[str, Path] = {}
        for stem in sorted(expected):
            relative = stored[stem]
            path = cache_dir / relative
            if not (path.is_file() and path.stat().st_size > 0):
                return None
            mapping[stem] = path
    except (json.JSONDecodeError, KeyError, OSError, TypeError, ValueError):
        return None
    return mapping


def _discover_stems(cache_dir: Path, expected: set[str]) -> dict[str, Path]:
    """Map stem names onto the WAVs demucs wrote inside this key's directory."""
    mapping: dict[str, Path] = {}
    for path in sorted(cache_dir.rglob("*.wav")):
        if path.stem in expected and path.is_file() and path.stat().st_size > 0:
            mapping.setdefault(path.stem, path)
    missing = sorted(expected - mapping.keys())
    if missing:
        everything = sorted(path.stem for path in cache_dir.rglob("*.wav"))
        raise StemsCommandError(
            f"demucs finished but did not produce usable stem WAV(s) {missing}; "
            f"found: {everything or 'nothing'}"
        )
    return mapping


def _write_manifest(
    cache_dir: Path,
    model: str,
    canonical: Mapping[str, object],
    key: str,
    source_path: Path,
    stems: Mapping[str, Path],
) -> None:
    manifest = {
        "manifest_version": 1,
        "stems_format": STEMS_FORMAT_VERSION,
        "engine_version": __version__,
        "model": model,
        "config": dict(canonical),
        "key": key,
        "source": _source_fingerprint(source_path),
        "stems": {
            stem: path.relative_to(cache_dir).as_posix()
            for stem, path in sorted(stems.items())
        },
    }
    payload = json.dumps(manifest, indent=2, sort_keys=True) + "\n"
    with tempfile.NamedTemporaryFile(
        mode="w", encoding="utf-8", dir=cache_dir, prefix=".manifest-", delete=False
    ) as temporary:
        temporary.write(payload)
        temporary.flush()
        os.fsync(temporary.fileno())
        temporary_path = Path(temporary.name)
    try:
        os.replace(temporary_path, cache_dir / _MANIFEST_NAME)
    finally:
        temporary_path.unlink(missing_ok=True)


def separate_stems(
    source: str | Path,
    *,
    model: str = DEFAULT_MODEL,
    config: Mapping[str, object] | None = None,
    cache_root: str | Path | None = None,
    status: DemucsStatus | None = None,
    runner: Runner | None = None,
) -> SeparationResult:
    """Separate ``source`` into stems, reusing the cache when possible.

    All output stays inside the per-key cache directory; the source audio is
    never modified.  ``status`` and ``runner`` are injection seams so tests can
    exercise the full flow without demucs installed.  Note that demucs itself
    downloads model weights on first use with a given model name.
    """
    source_path = _existing_source(source)
    model = _validated_model(model)
    canonical = _canonical_config(config)
    expected = _expected_stems(canonical)
    backend = status if status is not None else demucs_status()
    run = runner if runner is not None else default_command_runner
    key = cache_key(source_path, model=model, config=canonical)
    cache_dir = _resolve_root(cache_root) / key
    argv = _separation_argv(backend, model, canonical, cache_dir, source_path)

    cache_dir.mkdir(parents=True, exist_ok=True)
    lock_path = cache_dir / ".separation.lock"
    with lock_path.open("a+b") as lock:
        _lock_cache_file(lock)
        cached = _load_cached_stems(cache_dir, expected)
        if cached is not None:
            return SeparationResult(
                source=str(source_path), model=model, config=canonical, key=key,
                cache_dir=cache_dir, cache_hit=True, stems=cached, command=argv,
            )
        result = run(argv)
        if result.returncode != 0:
            tail = (result.stderr or result.stdout or "").strip()[-_ERROR_TAIL:]
            raise StemsCommandError(
                f"demucs separation failed with exit code {result.returncode} "
                f"for {source_path.name}: {tail or 'no output captured'}"
            )
        stems = _discover_stems(cache_dir, expected)
        _write_manifest(cache_dir, model, canonical, key, source_path, stems)
        return SeparationResult(
            source=str(source_path), model=model, config=canonical, key=key,
            cache_dir=cache_dir, cache_hit=False, stems=stems, command=argv,
        )


# --------------------------------------------------------------------------
# Stem -> MIDI target mapping
# --------------------------------------------------------------------------


_STEM_REASON = {
    "melody": "vocals stem isolates the lead voice for melody transcription",
    "chords": "other stem keeps the harmonic instruments for chord transcription",
    "bass": "bass stem isolates the low end for bass transcription",
    "groove": "drums stem isolates the rhythmic bed for groove transcription",
}


@dataclass(frozen=True)
class MidiSourceRecommendation:
    """Which audio to feed one Audio Tools MIDI target, and why."""

    mode: str
    stem: str
    path: Path
    used_mix_fallback: bool
    reason: str


def stem_is_audible(
    path: str | Path,
    *,
    rms_floor: float = NEARLY_SILENT_RMS,
    max_seconds: float = 8.0,
) -> bool:
    """Best-effort silence check used to trigger the melody mix fallback.

    Reads at most ``max_seconds`` of PCM from the middle of the WAV, so a
    silent leader does not hide audible material.  Containers the stdlib
    ``wave`` module cannot parse (for example IEEE-float WAVs) are treated as
    usable when the file is non-trivially sized: a wrong fallback to the full
    mix is worse than a missed fallback.
    """
    file_path = Path(path)
    if not file_path.is_file():
        return False
    try:
        with wave.open(str(file_path), "rb") as handle:
            rate = handle.getframerate() or 44_100
            width = handle.getsampwidth()
            frames = handle.getnframes()
            if width not in _WAVE_DTYPE or rate <= 0 or frames <= 0:
                return file_path.stat().st_size > 1024
            take = min(frames, int(rate * max_seconds))
            start = (frames - take) // 2
            if start > 0:
                handle.setpos(start)
            raw = handle.readframes(take)
    except (OSError, EOFError, wave.Error):
        return file_path.stat().st_size > 1024
    samples = np.frombuffer(raw, dtype=_WAVE_DTYPE[width]).astype(np.float64)
    if samples.size == 0:
        return False
    if width == 1:  # 8-bit WAV PCM is unsigned with a 128 midpoint.
        samples -= 128.0
        full_scale = 128.0
    else:
        full_scale = float(2 ** (8 * width - 1))
    rms = float(np.sqrt(np.mean((samples / full_scale) ** 2)))
    return rms >= rms_floor


def recommend_midi_source(
    mode: str,
    stems: Mapping[str, Path],
    mix: str | Path | None = None,
    *,
    audible: Callable[[Path], bool] = stem_is_audible,
) -> MidiSourceRecommendation:
    """Map one Audio Tools MIDI target onto the stem (or mix) to transcribe.

    ``melody`` falls back to the original mix when the ``vocals`` stem is
    nearly silent, per docs/31; the other targets always use their stem.
    """
    if mode not in STEM_FOR_MODE:
        raise ValueError(
            f"Unsupported MIDI target mode: {mode!r}; expected one of {sorted(STEM_FOR_MODE)}"
        )
    stem = STEM_FOR_MODE[mode]
    path = stems.get(stem)
    if path is None:
        raise StemsError(
            f"Stem {stem!r} needed for mode {mode!r} is not present in this "
            f"separation; available stems: {sorted(stems)}"
        )
    if mode in FALLBACK_TO_MIX_MODES and not audible(path):
        mix_path = Path(mix).expanduser().resolve() if mix is not None else None
        if mix_path is not None and mix_path.is_file():
            return MidiSourceRecommendation(
                mode, stem, mix_path, True,
                f"{stem} stem is nearly silent (RMS floor {NEARLY_SILENT_RMS:g}); "
                "falling back to the original mix",
            )
        return MidiSourceRecommendation(
            mode, stem, path, False,
            f"{stem} stem is nearly silent but no mix is available; using the stem anyway",
        )
    return MidiSourceRecommendation(mode, stem, path, False, _STEM_REASON[mode])


def recommend_midi_sources(
    source: str | Path,
    stems: Mapping[str, Path],
    modes: Sequence[str] = ("melody", "chords"),
    *,
    audible: Callable[[Path], bool] = stem_is_audible,
) -> dict[str, MidiSourceRecommendation]:
    """Recommend the audio input for each requested MIDI target mode."""
    ordered = list(dict.fromkeys(modes))
    mix = Path(source).expanduser().resolve() if source is not None else None
    return {
        mode: recommend_midi_source(mode, stems, mix, audible=audible)
        for mode in ordered
    }


# --------------------------------------------------------------------------
# One-call Studio foundation
# --------------------------------------------------------------------------


@dataclass(frozen=True)
class StudioSeparation:
    """Complete Studio result: stems plus per-target MIDI input advice."""

    source: str
    model: str
    config: dict
    key: str
    cache_dir: Path
    cache_hit: bool
    stems: dict[str, Path]
    recommendations: dict[str, MidiSourceRecommendation]

    def to_dict(self) -> dict:
        """JSON-friendly view for the future Inspector bridge."""
        return {
            "source": self.source,
            "model": self.model,
            "config": dict(self.config),
            "key": self.key,
            "cache_dir": str(self.cache_dir),
            "cache_hit": self.cache_hit,
            "stems": {stem: str(path) for stem, path in self.stems.items()},
            "recommendations": {
                mode: {
                    "stem": advice.stem,
                    "path": str(advice.path),
                    "used_mix_fallback": advice.used_mix_fallback,
                    "reason": advice.reason,
                }
                for mode, advice in self.recommendations.items()
            },
        }

    def to_json(self) -> str:
        return json.dumps(self.to_dict(), indent=2, ensure_ascii=False)


def _source_duration_seconds(path: Path) -> float | None:
    """Read WAV duration without adding a decoder dependency to Studio export."""
    try:
        with wave.open(str(path), "rb") as handle:
            rate = handle.getframerate()
            if rate > 0:
                return handle.getnframes() / rate
    except (OSError, EOFError, wave.Error):
        pass
    return None


def stems_export_manifest(studio: StudioSeparation) -> dict:
    """Build the stable external manifest for a completed Studio separation.

    The manifest intentionally contains no run-time fields (such as cache-hit
    state) so equal source, model, and separation options yield byte-identical
    JSON.  Stem records always follow :data:`STEMS`, regardless of filesystem
    traversal order, and WAV paths are absolute for direct host consumption.
    """
    source_path = _existing_source(studio.source)
    missing = [stem for stem in STEMS if stem not in studio.stems]
    if missing:
        raise StemsError(
            "Cannot export a four-stem manifest; missing stem WAV(s): "
            f"{missing}"
        )
    return {
        "manifest_version": 1,
        "source": {
            **_source_fingerprint(source_path),
            "duration_seconds": _source_duration_seconds(source_path),
        },
        "model": studio.model,
        "cache_key": studio.key,
        "stems": [
            {"name": stem, "wav_path": str(Path(studio.stems[stem]).resolve())}
            for stem in STEMS
        ],
    }


def write_stems_manifest(studio: StudioSeparation, destination: str | Path) -> Path:
    """Write a deterministic, standalone four-stem export manifest.

    Parent directories are created when needed.  Returns the resolved manifest
    path so callers can add it to a response payload without ambiguity.
    """
    path = Path(destination).expanduser().resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(
        stems_export_manifest(studio), indent=2, sort_keys=True, ensure_ascii=False
    ) + "\n"
    path.write_text(text, encoding="utf-8")
    return path


def run_studio_separation(
    source: str | Path,
    *,
    modes: Sequence[str] = ("melody", "chords"),
    model: str = DEFAULT_MODEL,
    config: Mapping[str, object] | None = None,
    cache_root: str | Path | None = None,
    status: DemucsStatus | None = None,
    runner: Runner | None = None,
) -> StudioSeparation:
    """Status gate, cached separation and MIDI-target recommendations.

    Raises :class:`StemsUnavailableError` with an actionable hint when demucs
    is not installed; modes are validated before any expensive work runs.
    """
    if isinstance(modes, str):
        raise ValueError("modes must be a sequence of mode names, not a single string")
    unknown = sorted(set(modes) - set(STEM_FOR_MODE))
    if unknown:
        raise ValueError(
            f"Unsupported MIDI target mode(s): {unknown}; expected one of {sorted(STEM_FOR_MODE)}"
        )
    backend = status if status is not None else demucs_status()
    if not backend.installed:
        raise StemsUnavailableError(backend.details)
    separation = separate_stems(
        source, model=model, config=config, cache_root=cache_root,
        status=backend, runner=runner,
    )
    recommendations = recommend_midi_sources(source, separation.stems, modes)
    return StudioSeparation(
        source=separation.source,
        model=separation.model,
        config=separation.config,
        key=separation.key,
        cache_dir=separation.cache_dir,
        cache_hit=separation.cache_hit,
        stems=separation.stems,
        recommendations=recommendations,
    )
