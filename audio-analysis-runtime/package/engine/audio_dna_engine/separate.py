"""BML: Demucs stem separation without ``demucs.separate``.

``demucs.separate`` reads and writes audio through ``sphn``, which has no wheels for Intel
macOS or Linux on ARM. This runner uses Demucs's model and ``apply_model`` only: the audio is
decoded with the same ffmpeg as the rest of the engine (any format it reads), and each stem is
written as a 16-bit WAV in the layout ``demucs.separate`` uses, ``<out>/<model>/<track>/<stem>.wav``,
rescaled when it would clip (``demucs.separate``'s default). Command line: the subset of
``demucs.separate``'s options that :mod:`audio_dna_engine.stems` passes.

Progress: when Helper: Audio Analysis asks for it (``AUDIO_DNA_PROGRESS_FILE``), the fraction of
Demucs's segments done is written there (:mod:`audio_dna_engine.progress`); output only.

The model must already be on disk (the Audio Analysis stem setup downloads it); engine runs
have ``HF_HUB_OFFLINE`` set, so nothing is downloaded here.
"""

from __future__ import annotations

import argparse
import subprocess
import sys
import types
import wave
from pathlib import Path

import numpy as np

from . import progress
from .audio import AudioDecodeError, _binary


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(prog="audio_dna_engine.separate",
                                     description="Separate tracks into stems with Demucs.")
    result.add_argument("tracks", nargs="+", type=Path)
    result.add_argument("-n", "--name", default="htdemucs")
    result.add_argument("-o", "--out", type=Path, default=Path("separated"))
    result.add_argument("--two-stems", dest="stem", default=None)
    result.add_argument("--shifts", type=int, default=1)
    result.add_argument("--overlap", type=float, default=0.25)
    result.add_argument("--segment", type=float, default=None)
    result.add_argument("-j", "--jobs", type=int, default=0)
    return result


def decode(path: Path, sample_rate: int, channels: int) -> np.ndarray:
    """``path`` as float32 samples shaped [channels, frames], through ffmpeg."""
    command = [_binary("ffmpeg"), "-v", "error", "-nostdin", "-i", str(path), "-vn",
               "-ac", str(channels), "-ar", str(sample_rate), "-f", "f32le", "-"]
    process = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE, check=False)
    if process.returncode != 0:
        detail = process.stderr.decode("utf-8", "replace").strip()
        raise AudioDecodeError(f"ffmpeg could not decode {path.name}: {detail}")
    samples = np.frombuffer(process.stdout, dtype="<f4")
    if samples.size < channels:
        raise AudioDecodeError(f"Decoded audio is empty: {path}")
    samples = samples[: samples.size - samples.size % channels]
    return np.ascontiguousarray(samples.reshape(-1, channels).T)


def write_wav(path: Path, samples: np.ndarray, sample_rate: int) -> None:
    """A [channels, frames] float array as a 16-bit WAV, rescaled when it would clip."""
    peak = float(np.max(np.abs(samples))) if samples.size else 0.0
    samples = samples / max(1.01 * peak, 1.0)
    pcm = (np.clip(samples, -1.0, 1.0) * (2 ** 15 - 1)).astype("<i2")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".part")
    with wave.open(str(temporary), "wb") as handle:
        handle.setnchannels(pcm.shape[0])
        handle.setsampwidth(2)
        handle.setframerate(sample_rate)
        handle.writeframes(pcm.T.tobytes())
    temporary.replace(path)


def report_segments(model, tracks: int, shifts: int) -> None:
    """Reports the fraction of Demucs's segments done, through the progress bar ``apply_model`` already
    shows (``progress=True``): the same items in the same order, so the result doesn't change."""
    import demucs.apply as demucs_apply
    from demucs.apply import BagOfModels

    real = getattr(demucs_apply, "tqdm", None)
    if real is None or not hasattr(real, "tqdm"):
        return
    models = len(model.models) if isinstance(model, BagOfModels) else 1
    counter = progress.SegmentCounter(tracks * models * max(1, shifts))
    demucs_apply.tqdm = types.SimpleNamespace(
        tqdm=lambda items, **kwargs: counter.wrap(items, real.tqdm, **kwargs))


def main(argv: list[str] | None = None) -> int:
    arguments = parser().parse_args(argv)
    import torch
    from demucs.apply import BagOfModels, apply_model
    from demucs.htdemucs import HTDemucs
    from demucs.pretrained import get_model

    model = get_model(arguments.name)
    model.eval()
    if arguments.segment is not None:
        longest = float("inf")
        if isinstance(model, HTDemucs):
            longest = float(model.segment)
        elif isinstance(model, BagOfModels):
            longest = model.max_allowed_segment
        if arguments.segment > longest:
            print(f"segment is longer than the model allows ({longest})", file=sys.stderr)
            return 1
    if arguments.stem is not None and arguments.stem not in model.sources:
        print(f"stem {arguments.stem!r} is not one of {', '.join(model.sources)}", file=sys.stderr)
        return 1
    report_segments(model, len(arguments.tracks), arguments.shifts)
    out = arguments.out / arguments.name
    for track in arguments.tracks:
        if not track.is_file():
            print(f"File {track} does not exist", file=sys.stderr)
            return 1
        print(f"Separating track {track}", flush=True)
        wav = torch.from_numpy(decode(track, model.samplerate, model.audio_channels))
        reference = wav.mean(0)
        mean = reference.mean()
        std = reference.std() + 1e-8
        with torch.no_grad():
            separated = apply_model(model, ((wav - mean) / std)[None], shifts=arguments.shifts,
                                    split=True, overlap=arguments.overlap, progress=True,
                                    device="cpu", num_workers=arguments.jobs,
                                    segment=arguments.segment)
        separated = separated * std + mean
        stems = {name: separated[0, index].numpy() for index, name in enumerate(model.sources)}
        folder = out / track.name.rsplit(".", 1)[0]
        if arguments.stem is None:
            for name, samples in stems.items():
                write_wav(folder / f"{name}.wav", samples, model.samplerate)
        else:
            chosen = stems.pop(arguments.stem)
            write_wav(folder / f"{arguments.stem}.wav", chosen, model.samplerate)
            write_wav(folder / f"no_{arguments.stem}.wav", sum(stems.values()), model.samplerate)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AudioDecodeError as error:
        print(f"Audio DNA error: {error}", file=sys.stderr)
        raise SystemExit(2)
