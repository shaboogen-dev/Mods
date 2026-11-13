"""Command-line entry point for the Audio DNA analysis engine."""

from __future__ import annotations

import argparse
import json
import os
import sys
import tempfile
from pathlib import Path

from . import progress
from .analysis import AnalysisBundle, DEFAULT_SENSITIVITY, Sensitivity, analyze_all_file, analyze_file
from .audio import AudioDecodeError
from .stems import StemsError, run_studio_separation, write_stems_manifest


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(
        prog="audio-dna",
        description="Detect BPM/key and extract Melody or Chords MIDI.",
    )
    result.add_argument("audio", type=Path, help="audio file to analyze")
    result.add_argument(
        "--mode",
        choices=("melody", "chords", "both"),
        default="melody",
        help="type of MIDI extraction; 'both' exports separate Melody and Chords files",
    )
    result.add_argument("--midi", type=Path, help="output .mid path")
    result.add_argument("--json", dest="json_path", type=Path, help="optional analysis .json path")
    result.add_argument(
        "--bridge-tsv",
        type=Path,
        help="compact event output for the native Bitwig drag bridge",
    )
    result.add_argument("--sample-rate", type=int, default=22_050)
    result.add_argument(
        "--tempo-hint",
        type=float,
        help="authoritative BPM from the host project at the selected playhead",
    )
    result.add_argument("--max-minutes", type=float, default=30.0)
    result.add_argument(
        "--sensitivity",
        type=float,
        default=DEFAULT_SENSITIVITY.value,
        help="Note Sensitivity in [0, 1]; drives Basic Pitch onset/frame "
        "thresholds, minimum note length and confidence floor (default "
        f"{DEFAULT_SENSITIVITY.value:.2f} is the calibrated general baseline)",
    )
    result.add_argument(
        "--studio", action="store_true",
        help="separate local HTDemucs 4-stems before analysis (optional heavy dependency)",
    )
    result.add_argument(
        "--stems-manifest",
        type=Path,
        help="write a deterministic Studio four-stem export manifest (also enables --studio)",
    )
    result.add_argument(
        "--stems-only", action="store_true",
        help="separate/cache stems and write --stems-manifest without running MIDI analysis",
    )
    result.add_argument(
        "--progress-file", type=Path,
        help="atomic JSON phase file for a stem-separation progress window",
    )
    result.add_argument("--quiet", action="store_true", help="suppress JSON on stdout")
    return result


def _write_progress(path: Path | None, phase: str) -> None:
    if path is None:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps({"phase": phase}, separators=(",", ":")) + "\n"
    with tempfile.NamedTemporaryFile(
        mode="w", encoding="utf-8", dir=path.parent,
        prefix=f".{path.name}.", delete=False,
    ) as temporary:
        temporary.write(payload)
        temporary.flush()
        os.fsync(temporary.fileno())
        temporary_path = Path(temporary.name)
    try:
        os.replace(temporary_path, path)
    finally:
        temporary_path.unlink(missing_ok=True)


def main(argv: list[str] | None = None) -> int:
    arguments = parser().parse_args(argv)
    if arguments.stems_only and arguments.stems_manifest is None:
        parser().error("--stems-only requires --stems-manifest")
    if arguments.progress_file and not arguments.stems_only:
        parser().error("--progress-file requires --stems-only")
    _write_progress(arguments.progress_file, "separating")
    try:
        sensitivity = Sensitivity(arguments.sensitivity)
        source_by_mode: dict[str, Path] = {}
        studio_report: dict | None = None
        studio_enabled = bool(arguments.studio or arguments.stems_manifest)
        if studio_enabled:
            # BML: progress output only (stems first, then each analysis); see progress.py
            with progress.span(0.0, 1.0 if arguments.stems_only else 0.6):
                studio = run_studio_separation(arguments.audio, modes=("melody", "chords", "groove"))
            studio_report = studio.to_dict()
            if arguments.stems_manifest:
                studio_report["manifest_path"] = str(
                    write_stems_manifest(studio, arguments.stems_manifest)
                )
            if arguments.stems_only:
                _write_progress(arguments.progress_file, "complete")
                if not arguments.quiet:
                    print(json.dumps(studio_report, indent=2, ensure_ascii=False))
                return 0
            source_by_mode = {
                mode: recommendation.path
                for mode, recommendation in studio.recommendations.items()
            }
        if arguments.mode == "both":
            base = arguments.midi or arguments.audio.with_suffix(".mid")
            midi_outputs = {
                mode: base.with_name(f"{base.stem}.{mode}.mid")
                for mode in ("melody", "chords")
            }
            if studio_enabled:
                melody = analyze_file(source_by_mode["melody"], mode="melody",
                    midi_output=midi_outputs["melody"], sample_rate=arguments.sample_rate,
                    max_duration=arguments.max_minutes * 60, sensitivity=sensitivity.value,
                    tempo_hint=arguments.tempo_hint)
                progress.report(0.7)
                chords = analyze_file(source_by_mode["chords"], mode="chords",
                    midi_output=midi_outputs["chords"], sample_rate=arguments.sample_rate,
                    max_duration=arguments.max_minutes * 60, sensitivity=sensitivity.value,
                    tempo_hint=arguments.tempo_hint)
                progress.report(0.8)
                drums = analyze_file(source_by_mode["groove"], mode="groove",
                    sample_rate=arguments.sample_rate,
                    max_duration=arguments.max_minutes * 60, sensitivity=sensitivity.value,
                    tempo_hint=arguments.tempo_hint)
                progress.report(0.9)
                direct = analyze_all_file(arguments.audio, sample_rate=arguments.sample_rate,
                    max_duration=arguments.max_minutes * 60, sensitivity=sensitivity.value,
                    tempo_hint=arguments.tempo_hint)
                analysis = AnalysisBundle(source=direct.source, duration=direct.duration,
                    sample_rate=direct.sample_rate, tempo=direct.tempo, key=direct.key,
                    melody=melody.notes, chords=chords.chords, harmony=chords.harmony,
                    groove=drums.groove, midi_paths={
                        "melody": melody.midi_path or "", "chords": chords.midi_path or "",
                    })
            else:
                analysis = analyze_all_file(
                    arguments.audio, midi_outputs=midi_outputs, sample_rate=arguments.sample_rate,
                    max_duration=arguments.max_minutes * 60, sensitivity=sensitivity.value,
                    tempo_hint=arguments.tempo_hint,
                )
        else:
            midi_path = arguments.midi or arguments.audio.with_suffix(
                f".{arguments.mode}.mid"
            )
            analysis = analyze_file(
                source_by_mode.get(arguments.mode, arguments.audio),
                mode=arguments.mode,
                midi_output=midi_path,
                sample_rate=arguments.sample_rate,
                max_duration=arguments.max_minutes * 60,
                sensitivity=sensitivity.value,
                tempo_hint=arguments.tempo_hint,
            )
    except (AudioDecodeError, StemsError, ValueError) as error:
        _write_progress(arguments.progress_file, "failed")
        print(f"Audio DNA error: {error}", file=sys.stderr)
        return 2

    payload = analysis.to_dict()
    if studio_report is not None:
        payload["studio"] = studio_report
    text = json.dumps(payload, indent=2, ensure_ascii=False) + "\n"
    if arguments.json_path:
        arguments.json_path.parent.mkdir(parents=True, exist_ok=True)
        arguments.json_path.write_text(text, encoding="utf-8")
    if arguments.bridge_tsv:
        arguments.bridge_tsv.parent.mkdir(parents=True, exist_ok=True)
        arguments.bridge_tsv.write_text(analysis.bridge_tsv(), encoding="utf-8")
    if not arguments.quiet:
        print(text, end="")
    return 0
