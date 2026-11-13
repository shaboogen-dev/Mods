"""Audio decoding helpers backed by the ffmpeg already used in the toolchain.

BML changes (Helper: Audio Analysis): ffmpeg is always imageio-ffmpeg's bundled build, which the
setup installs, never one on PATH; no ffprobe: a file over the length limit is found from the
decoded audio. Stem separation decodes through ``_binary`` too (``separate.py``).
"""

from __future__ import annotations

import os
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path

import numpy as np


class AudioDecodeError(RuntimeError):
    """Raised when an input cannot be decoded as audio."""


@dataclass(frozen=True)
class DecodedAudio:
    samples: np.ndarray
    sample_rate: int
    duration: float
    source_duration: float | None


def _binary(name: str) -> str:
    # BML: the Audio Analysis setup installs imageio-ffmpeg, whose wheel carries an ffmpeg build for
    # Windows, macOS and Linux. That one is used on every computer, so results don't depend on
    # whatever ffmpeg happens to be on PATH.
    if name != "ffmpeg":
        raise AudioDecodeError(f"Required executable is not available: {name}")
    try:
        import imageio_ffmpeg  # type: ignore

        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception as error:  # noqa: BLE001 - any failure means "not available"
        raise AudioDecodeError(f"Required executable is not available: {name} ({error})") from error


def decode_audio(
    source: str | Path,
    *,
    sample_rate: int = 22_050,
    max_duration: float = 30 * 60,
) -> DecodedAudio:
    """Decode an audio file to normalized mono float32 samples.

    Audio DNA targets files lasting minutes, so the default ceiling is thirty
    minutes instead of the short-preview limits common in sample browsers.
    """
    path = Path(source).expanduser().resolve()
    if not path.is_file():
        raise AudioDecodeError(f"Audio file does not exist: {path}")
    if sample_rate < 8_000:
        raise ValueError("sample_rate must be at least 8000 Hz")
    if max_duration <= 0:
        raise ValueError("max_duration must be positive")

    command = [
        _binary("ffmpeg"),
        "-y",
        "-v",
        "error",
        "-i",
        str(path),
        "-vn",
        "-ac",
        "1",
        "-ar",
        str(sample_rate),
        "-t",
        f"{max_duration + 1.0:.6f}",  # BML: a second over the limit shows the file is too long
        "-f",
        "f32le",
    ]
    descriptor, decoded_name = tempfile.mkstemp(prefix="audio-dna-", suffix=".f32")
    os.close(descriptor)
    decoded_path = Path(decoded_name)
    try:
        process = subprocess.run(
            [*command, str(decoded_path)], stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE, check=False,
        )
        if process.returncode != 0:
            detail = process.stderr.decode("utf-8", "replace").strip()
            raise AudioDecodeError(f"ffmpeg could not decode {path.name}: {detail}")
        samples = np.fromfile(decoded_path, dtype="<f4")
    finally:
        decoded_path.unlink(missing_ok=True)
    if samples.size == 0:
        raise AudioDecodeError(f"Decoded audio is empty: {path}")
    source_duration = float(samples.size / sample_rate)
    if source_duration > max_duration + 0.1:
        raise AudioDecodeError(
            f"Audio is longer than the configured limit of {max_duration:.1f}s"
        )
    if not np.isfinite(samples).all():
        samples = np.nan_to_num(samples, copy=False)

    peak = float(np.max(np.abs(samples)))
    if peak > 1.0:
        samples /= peak
    return DecodedAudio(
        samples=samples,
        sample_rate=sample_rate,
        duration=float(samples.size / sample_rate),
        source_duration=source_duration,
    )
