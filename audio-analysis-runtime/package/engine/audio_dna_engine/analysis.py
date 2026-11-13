"""Tempo, key, melody, and chord analysis for Audio DNA."""

from __future__ import annotations

import json
import math
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Literal, Sequence

import numpy as np

try:
    import aubio
except ImportError:  # The model runtime intentionally does not depend on aubio.
    aubio = None

try:
    from basic_pitch.inference import predict as basic_pitch_predict
except ImportError:
    basic_pitch_predict = None

from .audio import DecodedAudio, decode_audio
from .midi import MidiNote, write_midi


Mode = Literal["melody", "chords", "groove"]
NOTE_NAMES_SHARP = ("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")


@dataclass(frozen=True)
class Sensitivity:
    """Single-knob transcription sensitivity mapped onto Basic Pitch controls.

    The device exposes one ``Note Sensitivity`` parameter in ``[0, 1]``; 0.5
    reproduces the calibrated defaults.  Every mapping below is linear in the
    knob value ``v`` so results are reproducible and there are no hidden magic
    values:

    ======================  =========================  =======================
    control                 formula                    range (v=0 ... v=1)
    ======================  =========================  =======================
    onset_threshold         0.76 - 0.56 * v            0.76 ... 0.20
    frame_threshold         0.50 - 0.44 * v            0.50 ... 0.06
    minimum_note_length_ms  round(120 - 100 * v)       120 ... 20 ms
    minimum_confidence      max(0, 0.40 - 0.80 * v)    0.40 ... 0.00
    ======================  =========================  =======================

    Lower thresholds and a shorter minimum note length admit more notes, so a
    higher knob value yields a denser transcription.  The confidence floor
    removes the weakest notes only at low sensitivity; at ``v >= 0.5`` every
    model note is kept (Basic Pitch already filtered it against the onset and
    frame thresholds).
    """

    value: float = 0.5

    def __post_init__(self) -> None:
        if not math.isfinite(self.value) or not 0.0 <= self.value <= 1.0:
            raise ValueError(f"Note Sensitivity must be within [0, 1]: {self.value}")

    @property
    def onset_threshold(self) -> float:
        return round(0.76 - 0.56 * self.value, 6)

    @property
    def frame_threshold(self) -> float:
        return round(0.50 - 0.44 * self.value, 6)

    @property
    def minimum_note_length_ms(self) -> int:
        return int(round(120 - 100 * self.value))

    @property
    def minimum_confidence(self) -> float:
        return round(max(0.0, 0.40 - 0.80 * self.value), 6)


# One neutral, instrument-agnostic baseline. A measured sweep across the
# deterministic piano/chords/vocal/bass corpus selects 0.15: it preserves the
# known notes while rejecting the harmonic fragments admitted by the old 0.50.
# It remains one Sensitivity control, not a collection of profiles.
DEFAULT_SENSITIVITY = Sensitivity(0.15)
NOTE_NAMES_FLAT = ("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
MAJOR_PROFILE = np.array([6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88])
MINOR_PROFILE = np.array([6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17])


@dataclass(frozen=True)
class TempoResult:
    bpm: float
    confidence: float


@dataclass(frozen=True)
class KeyResult:
    root: int
    tonic: str
    scale: str
    name: str
    confidence: float


@dataclass(frozen=True)
class NoteEvent:
    start: float
    end: float
    pitch: int
    note: str
    velocity: int
    confidence: float

    def midi(self) -> MidiNote:
        return MidiNote(self.start, self.end, self.pitch, self.velocity)


@dataclass(frozen=True)
class ChordEvent:
    start: float
    end: float
    root: int
    quality: str
    name: str
    confidence: float
    pitches: tuple[int, ...]
    velocity: int

    def midi(self) -> list[MidiNote]:
        return [MidiNote(self.start, self.end, pitch, self.velocity) for pitch in self.pitches]


@dataclass(frozen=True)
class AnalysisResult:
    source: str
    duration: float
    sample_rate: int
    mode: Mode
    tempo: TempoResult
    key: KeyResult
    notes: tuple[NoteEvent, ...]
    chords: tuple[ChordEvent, ...]
    harmony: tuple[NoteEvent, ...] = ()
    groove: tuple[NoteEvent, ...] = ()
    midi_path: str | None = None
    engine_version: str = "0.3.0"

    def to_dict(self) -> dict:
        return asdict(self)

    def to_json(self) -> str:
        return json.dumps(self.to_dict(), indent=2, ensure_ascii=False)

    def bridge_tsv(self) -> str:
        """Compact interchange consumed by the patched Bitwig drag bridge."""
        lines = [
            "ADNA\t1",
            f"META\tbpm\t{self.tempo.bpm:.6f}",
            f"META\tduration\t{self.duration:.6f}",
            f"META\tbpm_confidence\t{self.tempo.confidence:.6f}",
            f"META\tkey\t{self.key.name}",
            f"META\tkey_confidence\t{self.key.confidence:.6f}",
            f"META\tmode\t{self.mode}",
        ]
        if self.mode == "melody":
            midi_notes = [event.midi() for event in self.notes]
        elif self.mode == "chords":
            midi_notes = [event.midi() for event in self.harmony]
        else:
            midi_notes = [event.midi() for event in self.groove]
        for note in midi_notes:
            lines.append(
                f"NOTE\t{note.start:.6f}\t{note.end:.6f}\t{note.pitch}\t{note.velocity}"
            )
        return "\n".join(lines) + "\n"


@dataclass(frozen=True)
class AnalysisBundle:
    """One decoded source, with all MIDI interpretations kept separate.

    Tempo and key are global properties of the source. Melody, Chords and
    Groove remain independent outputs so a native UI can offer explicit
    destinations instead of guessing which result a producer intended.
    """

    source: str
    duration: float
    sample_rate: int
    tempo: TempoResult
    key: KeyResult
    melody: tuple[NoteEvent, ...]
    chords: tuple[ChordEvent, ...]
    harmony: tuple[NoteEvent, ...]
    groove: tuple[NoteEvent, ...]
    midi_paths: dict[str, str]
    engine_version: str = "0.3.0"

    def to_dict(self) -> dict:
        return asdict(self)

    def to_json(self) -> str:
        return json.dumps(self.to_dict(), indent=2, ensure_ascii=False)

    def bridge_tsv(self) -> str:
        """Versioned interchange containing all explicit MIDI targets."""
        lines = [
            "ADNA\t3",
            f"META\tbpm\t{self.tempo.bpm:.6f}",
            f"META\tduration\t{self.duration:.6f}",
            f"META\tbpm_confidence\t{self.tempo.confidence:.6f}",
            f"META\tkey\t{self.key.name}",
            f"META\tkey_confidence\t{self.key.confidence:.6f}",
        ]
        for mode, events in (
            ("melody", self.melody),
            ("chords", self.harmony),
            ("groove", self.groove),
        ):
            for event in events:
                note = event.midi()
                lines.append(
                    f"NOTE\t{mode}\t{note.start:.6f}\t{note.end:.6f}\t"
                    f"{note.pitch}\t{note.velocity}"
                )
        return "\n".join(lines) + "\n"


def _frames(samples: np.ndarray, frame_size: int, hop_size: int) -> np.ndarray:
    if samples.size < frame_size:
        samples = np.pad(samples, (0, frame_size - samples.size))
    count = 1 + (samples.size - frame_size) // hop_size
    shape = (count, frame_size)
    strides = (samples.strides[0] * hop_size, samples.strides[0])
    return np.lib.stride_tricks.as_strided(samples, shape=shape, strides=strides)


def _spectral_flux(samples: np.ndarray, sample_rate: int, *, frame_size: int = 2048, hop_size: int = 512) -> np.ndarray:
    frames = _frames(samples, frame_size, hop_size)
    window = np.hanning(frame_size).astype(np.float32)
    magnitudes = np.abs(np.fft.rfft(frames * window, axis=1))
    flux = np.maximum(0.0, np.diff(magnitudes, axis=0)).sum(axis=1)
    flux = np.concatenate(([0.0], flux))
    if flux.size >= 9:
        kernel = np.ones(9) / 9
        baseline = np.convolve(flux, kernel, mode="same")
        flux = np.maximum(0.0, flux - baseline)
    maximum = float(np.max(flux)) if flux.size else 0.0
    return (flux / maximum).astype(np.float32) if maximum > 0 else flux.astype(np.float32)


def extract_groove(samples: np.ndarray, sample_rate: int) -> tuple[NoteEvent, ...]:
    """Convert perceptually distinct onsets into a compact MIDI groove.

    Groove is intentionally an onset map, not an attempt to infer drum names
    from a mixed loop.  It preserves the timing and relative accent of the
    selected audio in a single, transferable MIDI lane (C1 / MIDI note 36).
    This makes the result useful for triggering a Drum Machine, driving a
    side-chain pattern, or applying the source feel to another sound.
    """
    hop_size = 512
    flux = _spectral_flux(samples, sample_rate, hop_size=hop_size).astype(np.float64)
    if flux.size < 3 or float(np.max(flux)) <= 0.0:
        return ()

    threshold = max(0.12, float(np.percentile(flux, 75)))
    candidates = np.flatnonzero(
        (flux[1:-1] > flux[:-2])
        & (flux[1:-1] >= flux[2:])
        & (flux[1:-1] >= threshold)
    ) + 1
    # Do not turn a single kick transient into several MIDI hits.  55 ms is
    # shorter than a 1/32 at common tempos, so fast rhythmic material survives.
    minimum_spacing = max(1, int(round(0.055 * sample_rate / hop_size)))
    selected: list[int] = []
    for candidate in candidates:
        if not selected or candidate - selected[-1] >= minimum_spacing:
            selected.append(int(candidate))
        elif flux[candidate] > flux[selected[-1]]:
            selected[-1] = int(candidate)

    duration = samples.size / sample_rate
    events: list[NoteEvent] = []
    for index, frame in enumerate(selected):
        start = frame * hop_size / sample_rate
        following = (
            selected[index + 1] * hop_size / sample_rate
            if index + 1 < len(selected) else duration
        )
        end = min(start + 0.09, following - 0.008, duration)
        if end <= start + 0.015:
            end = min(duration, start + 0.03)
        strength = float(np.clip(flux[frame], 0.0, 1.0))
        events.append(NoteEvent(
            round(start, 6), round(end, 6), 36, _midi_name(36),
            int(np.clip(round(35 + 90 * strength), 35, 125)),
            round(strength, 3),
        ))
    return tuple(events)


def detect_tempo(samples: np.ndarray, sample_rate: int) -> TempoResult:
    """Estimate a global tempo from onset-envelope periodicity."""
    hop = 512
    envelope = _spectral_flux(samples, sample_rate, hop_size=hop).astype(np.float64)
    if envelope.size < 8 or float(np.max(envelope)) <= 0:
        return TempoResult(120.0, 0.0)
    envelope -= float(np.mean(envelope))
    size = 1 << max(1, (2 * envelope.size - 1).bit_length())
    spectrum = np.fft.rfft(envelope, size)
    autocorrelation = np.fft.irfft(spectrum * np.conj(spectrum), size)[: envelope.size]
    if autocorrelation[0] > 0:
        autocorrelation /= autocorrelation[0]

    min_bpm, max_bpm = 55.0, 200.0
    min_lag = max(1, int(round(60.0 * sample_rate / (hop * max_bpm))))
    max_lag = min(autocorrelation.size - 1, int(round(60.0 * sample_rate / (hop * min_bpm))))
    if max_lag <= min_lag:
        return TempoResult(120.0, 0.0)
    lags = np.arange(min_lag, max_lag + 1)
    values = autocorrelation[lags].copy()
    bpms = 60.0 * sample_rate / (hop * lags)
    # A gentle 110-BPM prior resolves common half/double ambiguities without
    # forcing dance music onto genuinely slow or fast material.
    values *= np.exp(-0.5 * (np.log2(bpms / 110.0) / 1.35) ** 2)
    index = int(np.argmax(values))
    lag = int(lags[index])
    # Prefer the half-lag (double tempo) when it has nearly the same periodic
    # evidence. Downbeats often make the two-beat lag slightly stronger.
    initial_bpm = 60.0 * sample_rate / (hop * lag)
    if initial_bpm < 80 and lag >= 4:
        center = lag / 2
        choices = range(max(min_lag, round(center) - 2), min(max_lag, round(center) + 2) + 1)
        double_lag = max(choices, key=lambda candidate: autocorrelation[candidate])
        if autocorrelation[double_lag] >= 0.58 * autocorrelation[lag]:
            lag = int(double_lag)
    refined_lag = float(lag)
    if 0 < lag < autocorrelation.size - 1:
        left, middle, right = autocorrelation[lag - 1 : lag + 2]
        denominator = left - 2 * middle + right
        if abs(denominator) > 1e-12:
            refined_lag += float(np.clip(0.5 * (left - right) / denominator, -0.5, 0.5))
    bpm = 60.0 * sample_rate / (hop * refined_lag)
    peak = float(max(0.0, autocorrelation[lag]))
    alternatives = np.delete(values, slice(max(0, index - 2), min(values.size, index + 3)))
    rival = float(np.max(alternatives)) if alternatives.size else 0.0
    confidence = float(np.clip(0.55 * peak + 0.45 * max(0.0, peak - rival), 0.0, 1.0))

    # Regularly spaced onset peaks resolve the classic half-tempo ambiguity
    # of autocorrelation (for example, a strong downbeat every other beat).
    positive = envelope - float(np.min(envelope))
    threshold = float(np.percentile(positive, 62))
    local = np.flatnonzero(
        (positive[1:-1] > positive[:-2])
        & (positive[1:-1] >= positive[2:])
        & (positive[1:-1] > threshold)
    ) + 1
    minimum_distance = max(1, int(60.0 * sample_rate / (hop * 220.0)))
    selected: list[int] = []
    for candidate in local:
        if not selected or candidate - selected[-1] >= minimum_distance:
            selected.append(int(candidate))
        elif positive[candidate] > positive[selected[-1]]:
            selected[-1] = int(candidate)
    if len(selected) >= 5:
        intervals = np.diff(selected).astype(np.float64)
        low = 60.0 * sample_rate / (hop * max_bpm)
        high = 60.0 * sample_rate / (hop * min_bpm)
        intervals = intervals[(intervals >= low) & (intervals <= high)]
        if intervals.size >= 4:
            median_interval = float(np.median(intervals))
            mean_interval = float(np.mean(intervals))
            timing_interval = mean_interval if abs(mean_interval - median_interval) <= 1.0 else median_interval
            onset_bpm = 60.0 * sample_rate / (hop * timing_interval)
            if onset_bpm < 75:
                onset_bpm *= 2
            regularity = float(
                np.clip(1.0 - np.median(np.abs(intervals - median_interval)) / max(median_interval, 1), 0, 1)
            )
            if regularity >= 0.55 and abs(math.log2(onset_bpm / bpm)) <= 0.10:
                confidence = max(confidence, 0.45 + 0.5 * regularity)
    return TempoResult(round(bpm, 2), round(confidence, 3))


def chromagram(
    samples: np.ndarray,
    sample_rate: int,
    *,
    frame_size: int = 4096,
    hop_size: int = 1024,
) -> tuple[np.ndarray, np.ndarray]:
    """Return per-frame normalized chroma and frame RMS."""
    frames = _frames(samples, frame_size, hop_size)
    window = np.hanning(frame_size).astype(np.float32)
    spectrum = np.abs(np.fft.rfft(frames * window, axis=1)) ** 2
    frequencies = np.fft.rfftfreq(frame_size, 1.0 / sample_rate)
    valid = (frequencies >= 55.0) & (frequencies <= min(5000.0, sample_rate / 2))
    midi = 69.0 + 12.0 * np.log2(frequencies[valid] / 440.0)
    pitch_classes = np.mod(np.rint(midi).astype(int), 12)
    weights = 1.0 / np.sqrt(np.maximum(frequencies[valid], 55.0))
    result = np.zeros((frames.shape[0], 12), dtype=np.float64)
    for pitch_class in range(12):
        bins = pitch_classes == pitch_class
        if np.any(bins):
            result[:, pitch_class] = (spectrum[:, valid][:, bins] * weights[bins]).sum(axis=1)
    norms = np.linalg.norm(result, axis=1, keepdims=True)
    result = np.divide(result, norms, out=np.zeros_like(result), where=norms > 1e-12)
    rms = np.sqrt(np.mean(frames.astype(np.float64) ** 2, axis=1))
    return result.astype(np.float32), rms.astype(np.float32)


def detect_key(samples: np.ndarray, sample_rate: int) -> KeyResult:
    chroma, rms = chromagram(samples, sample_rate)
    if chroma.size == 0:
        return KeyResult(0, "C", "major", "C major", 0.0)
    active = rms > max(1e-5, float(np.percentile(rms, 25)) * 0.7)
    global_chroma = chroma[active].sum(axis=0) if np.any(active) else chroma.sum(axis=0)
    norm = float(np.linalg.norm(global_chroma))
    if norm <= 1e-12:
        return KeyResult(0, "C", "major", "C major", 0.0)
    global_chroma /= norm

    pitch_salience = np.zeros(12, dtype=np.float64)
    if aubio is not None:
        pitch_detector = aubio.pitch("yinfast", 4096, 512, sample_rate)
        pitch_detector.set_unit("midi")
        pitch_detector.set_silence(-50)
        pitch_detector.set_tolerance(0.80)
        pitch_samples = np.pad(samples.astype(np.float32), (0, (-samples.size) % 512))
        for offset in range(0, pitch_samples.size, 512):
            frame = pitch_samples[offset : offset + 512]
            pitch = float(pitch_detector(frame)[0])
            pitch_confidence = float(pitch_detector.get_confidence())
            level = float(np.sqrt(np.mean(frame.astype(np.float64) ** 2)))
            if 24 <= pitch <= 108 and pitch_confidence >= 0.60 and level >= 2e-4:
                pitch_salience[round(pitch) % 12] += pitch_confidence * level
    pitch_norm = float(np.linalg.norm(pitch_salience))
    normalized_pitch = pitch_salience / pitch_norm if pitch_norm > 1e-12 else np.zeros(12)
    maximum_tonic = float(np.max(pitch_salience))

    candidates: list[tuple[float, int, str]] = []
    for root in range(12):
        for scale, profile in (("major", MAJOR_PROFILE), ("minor", MINOR_PROFILE)):
            template = np.roll(profile, root)
            template = template / np.linalg.norm(template)
            spectral_score = float(np.dot(global_chroma, template))
            melodic_score = float(np.dot(normalized_pitch, template)) if pitch_norm > 1e-12 else spectral_score
            tonic_score = pitch_salience[root] / maximum_tonic if maximum_tonic > 1e-12 else global_chroma[root]
            third = (root + (4 if scale == "major" else 3)) % 12
            opposite_third = (root + (3 if scale == "major" else 4)) % 12
            third_evidence = float(global_chroma[third] - global_chroma[opposite_third])
            score = (
                0.40 * spectral_score
                + 0.15 * melodic_score
                + 0.30 * tonic_score
                + 0.30 * third_evidence
            )
            candidates.append((score, root, scale))
    candidates.sort(reverse=True)
    score, root, scale = candidates[0]
    rival = candidates[1][0]
    confidence = float(np.clip((score - rival) * 5.0 + (score - 0.48) * 0.55, 0.0, 1.0))
    prefer_flats = root in {1, 3, 6, 8, 10}
    tonic = (NOTE_NAMES_FLAT if prefer_flats else NOTE_NAMES_SHARP)[root]
    return KeyResult(root, tonic, scale, f"{tonic} {scale}", round(confidence, 3))


def refine_key_from_notes(
    base: KeyResult, notes: Sequence[NoteEvent]
) -> KeyResult:
    """Resolve relative-major/minor ambiguity from neural note evidence.

    Major and relative minor share the same pitch classes, so a global chroma
    profile often labels a G-minor phrase as Bb major.  The performed tonic is
    better exposed by duration and by the lowest note at the opening of a
    repeating phrase.  Strong acoustic classifications are left untouched.
    """
    if not notes or base.confidence >= 0.45:
        return base
    relative_root = (base.root + (9 if base.scale == "major" else 3)) % 12
    relative_scale = "minor" if base.scale == "major" else "major"
    weights = np.zeros(12, dtype=np.float64)
    for note in notes:
        weights[note.pitch % 12] += max(0.0, note.end - note.start) * note.confidence
    total = float(np.sum(weights))
    if total <= 1e-12:
        return base
    opening_start = min(note.start for note in notes)
    opening = [note for note in notes if note.start <= opening_start + 0.35]
    opening_bass = min(opening, key=lambda note: note.pitch).pitch % 12

    def tonic_score(root: int) -> float:
        return float(weights[root] / total) + (0.18 if root == opening_bass else 0.0)

    base_score = tonic_score(base.root)
    relative_score = tonic_score(relative_root)
    margin = relative_score - base_score
    if margin < 0.08:
        return base
    tonic = (NOTE_NAMES_FLAT if relative_root in {1, 3, 6, 8, 10}
             else NOTE_NAMES_SHARP)[relative_root]
    confidence = float(np.clip(max(base.confidence, 0.34 + 1.05 * margin), 0.0, 0.78))
    return KeyResult(
        relative_root, tonic, relative_scale, f"{tonic} {relative_scale}",
        round(confidence, 3),
    )


def refine_key_from_harmony(base: KeyResult, chords: Sequence[ChordEvent]) -> KeyResult:
    """Re-rank a global key using the actual polyphonic chord sequence.

    Spectral key profiles are deliberately broad: a short progression that
    ends on a dominant can otherwise be reported as that dominant's key.  When
    Basic Pitch has produced several stable triads, their diatonic functions
    are much stronger evidence.  The acoustic estimate remains part of the
    score, so noisy or sparse sources retain the dependency-light fallback.
    """
    stable = [chord for chord in chords if chord.confidence >= 0.60
              and chord.end - chord.start >= 0.35]
    if len(stable) < 3:
        return base

    candidates: list[tuple[float, int, str]] = []
    for root in range(12):
        for scale in ("major", "minor"):
            # Major: I ii iii IV V vi; natural minor: i ii III iv v VI VII.
            intervals = ((0, "major"), (2, "minor"), (4, "minor"),
                         (5, "major"), (7, "major"), (9, "minor"))
            if scale == "minor":
                intervals = ((0, "minor"), (2, "minor"), (3, "major"),
                             (5, "minor"), (7, "minor"), (8, "major"),
                             (10, "major"))
            expected = {(root + interval) % 12: quality for interval, quality in intervals}
            weighted = 0.0
            total = 0.0
            for chord in stable:
                weight = (chord.end - chord.start) * chord.confidence
                total += weight
                if expected.get(chord.root) == chord.quality:
                    weighted += weight
                elif chord.root in expected:
                    # Borrowed quality is informative, but not a full vote.
                    weighted += weight * 0.30
            diatonic = weighted / total if total > 1e-12 else 0.0
            cadence = 0.0
            tonic_quality = "minor" if scale == "minor" else "major"
            first = stable[0]
            previous, last = stable[-2:]
            if first.root == root and first.quality == tonic_quality:
                cadence += 0.22
            if last.root == root and last.quality == tonic_quality:
                cadence += 0.25
            if (previous.root == (root + 7) % 12 and previous.quality == "major"
                    and last.root == root and last.quality == tonic_quality):
                cadence += 0.25
            # A progression ending on V is commonly a loop whose next event
            # resolves to the tonic at its beginning (i–VI–iv–V, for example).
            if last.root == (root + 7) % 12 and last.quality == "major":
                cadence += 0.22
                if first.root == root and first.quality == tonic_quality:
                    cadence += 0.12
            # An I → V at the end of a loop is a reliable clue that the next
            # pass resolves to I (Eb → Bb means Eb major, not Bb major).
            if (previous.root == root and previous.quality == tonic_quality
                    and last.root == (root + 7) % 12 and last.quality == "major"):
                cadence += 0.24
            acoustic = 1.0 if (root, scale) == (base.root, base.scale) else 0.0
            candidates.append((0.66 * diatonic + cadence + 0.14 * acoustic, root, scale))

    candidates.sort(reverse=True)
    score, root, scale = candidates[0]
    rival = candidates[1][0]
    if (root, scale) == (base.root, base.scale) and score - rival < 0.06:
        return base
    confidence = float(np.clip(0.42 + (score - rival) * 1.25, 0.0, 0.95))
    tonic = (NOTE_NAMES_FLAT if root in {1, 3, 6, 8, 10} else NOTE_NAMES_SHARP)[root]
    return KeyResult(root, tonic, scale, f"{tonic} {scale}", round(confidence, 3))


def refine_tempo_from_harmony(
    base: TempoResult, chords: Sequence[ChordEvent], duration: float
) -> TempoResult:
    """Recover bar tempo from regular chord changes when onset flux is weak."""
    stable = [chord for chord in chords if chord.confidence >= 0.60
              and chord.end - chord.start >= 0.35]
    if len(stable) < 4 or base.confidence >= 0.10:
        return base
    intervals = np.diff(np.asarray([chord.start for chord in stable], dtype=np.float64))
    if intervals.size < 3 or np.any(intervals <= 0):
        return base
    median = float(np.median(intervals))
    regularity = float(np.clip(1.0 - np.median(np.abs(intervals - median))
        / max(median, 1e-9), 0.0, 1.0))
    if regularity < 0.88:
        return base
    # Chord changes often mark a bar. Prefer that interpretation only when
    # the onset detector has explicitly said it is uncertain.
    candidate = 60.0 * 4.0 / median
    coverage = (stable[-1].end - stable[0].start) / max(duration, 1e-9)
    # For a self-contained musical loop, the source duration gives a cleaner
    # bar length than model onset timestamps (which can be 50–150 ms late).
    if coverage >= 0.85:
        duration_candidate = 60.0 * 4.0 * len(stable) / duration
        if 88.0 <= duration_candidate <= 155.0:
            candidate = duration_candidate
    if not 88.0 <= candidate <= 155.0:
        return base
    # Sustained sources make neural chord boundaries a little late. Snap only
    # very near familiar session tempi, never for a confident rhythmic result.
    anchors = np.asarray((90, 95, 100, 105, 110, 115, 120, 124, 125, 126,
                          128, 130, 135, 140, 145, 150), dtype=np.float64)
    nearest = float(anchors[np.argmin(np.abs(anchors - candidate))])
    if abs(nearest - candidate) / candidate <= 0.030:
        candidate = nearest
    confidence = float(np.clip(0.46 + 0.44 * regularity, 0.0, 0.90))
    return TempoResult(round(candidate, 2), round(confidence, 3))


def resolve_project_tempo(detected: TempoResult, tempo_hint: float | None) -> TempoResult:
    """Prefer Bitwig's local project tempo when the native Inspector supplies it.

    A bounced one- or two-second melodic clip commonly contains no complete
    rhythmic cycle.  In that situation an audio-only BPM is necessarily an
    estimate, whereas Bitwig already knows the authoritative tempo (including
    the current point of a tempo map).  The hint is never guessed or rounded:
    it is used only when it is a plausible host-provided BPM.
    """
    if tempo_hint is None:
        return detected
    if not math.isfinite(tempo_hint) or not 20.0 <= tempo_hint <= 400.0:
        return detected
    return TempoResult(round(float(tempo_hint), 2), 1.0)


def _midi_name(pitch: int, *, flats: bool = False) -> str:
    names = NOTE_NAMES_FLAT if flats else NOTE_NAMES_SHARP
    return f"{names[pitch % 12]}{pitch // 12 - 1}"


def _median_filter(values: np.ndarray, width: int = 5) -> np.ndarray:
    if values.size == 0 or width <= 1:
        return values.copy()
    half = width // 2
    padded = np.pad(values, (half, half), mode="edge")
    return np.array([np.median(padded[index : index + width]) for index in range(values.size)])


def transcribe_melody(samples: np.ndarray, sample_rate: int) -> tuple[NoteEvent, ...]:
    """Transcribe monophonic foreground pitch into consolidated MIDI notes."""
    if aubio is None:
        raise RuntimeError("aubio is unavailable and the Basic Pitch model is not installed")
    hop_size = 256
    buffer_size = 2048
    # aubio's YIN stream reports pitch after accumulating its analysis window.
    # Its timestamp belongs at the centre of that window, rather than at the
    # hop where a stable pitch first appears.  Correct the note-on only: pitch
    # loss at note release is driven by the signal envelope and is not the
    # same fixed buffering delay.
    onset_latency_seconds = buffer_size / (2 * sample_rate)
    detector = aubio.pitch("yinfast", buffer_size, hop_size, sample_rate)
    detector.set_unit("midi")
    detector.set_silence(-45)
    detector.set_tolerance(0.82)

    padded = np.pad(samples.astype(np.float32), (0, (-samples.size) % hop_size))
    pitch_values: list[float] = []
    confidences: list[float] = []
    levels: list[float] = []
    for offset in range(0, padded.size, hop_size):
        frame = padded[offset : offset + hop_size]
        pitch_values.append(float(detector(frame)[0]))
        confidences.append(float(detector.get_confidence()))
        levels.append(float(np.sqrt(np.mean(frame.astype(np.float64) ** 2))))

    pitch_track = np.asarray(pitch_values)
    confidence_track = np.asarray(confidences)
    level_track = np.asarray(levels)
    voiced = (
        np.isfinite(pitch_track)
        & (pitch_track >= 24)
        & (pitch_track <= 108)
        & (confidence_track >= 0.58)
        & (level_track >= max(2e-4, float(np.percentile(level_track, 20)) * 0.7))
    )
    cleaned = pitch_track.copy()
    cleaned[~voiced] = np.nan
    # Fill only very short gaps so legato notes survive a weak analysis frame.
    for index in range(1, cleaned.size - 1):
        if np.isnan(cleaned[index]) and np.isfinite(cleaned[index - 1]) and np.isfinite(cleaned[index + 1]):
            if abs(cleaned[index - 1] - cleaned[index + 1]) < 1.0:
                cleaned[index] = (cleaned[index - 1] + cleaned[index + 1]) / 2
                confidence_track[index] = min(confidence_track[index - 1], confidence_track[index + 1])
    finite = np.isfinite(cleaned)
    if np.any(finite):
        replacement = np.interp(np.arange(cleaned.size), np.flatnonzero(finite), cleaned[finite])
        smoothed = _median_filter(replacement, 5)
        cleaned[finite] = smoothed[finite]
    quantized = np.where(np.isfinite(cleaned), np.rint(cleaned), -1).astype(int)

    events: list[NoteEvent] = []
    start: int | None = None
    current = -1
    for index in range(quantized.size + 1):
        pitch = int(quantized[index]) if index < quantized.size else -1
        if pitch != current:
            if start is not None and current >= 0:
                raw_event_start = start * hop_size / sample_rate
                event_end = index * hop_size / sample_rate
                # Preserve the raw span for filtering.  Applying the onset
                # timestamp correction before this check would promote short
                # pitch-transition fragments into MIDI notes.
                if event_end - raw_event_start >= 0.065:
                    event_start = max(0.0, raw_event_start - onset_latency_seconds)
                    segment_conf = float(np.mean(confidence_track[start:index]))
                    segment_level = float(np.sqrt(np.mean(level_track[start:index] ** 2)))
                    velocity = int(np.clip(round(44 + 500 * segment_level), 35, 122))
                    events.append(
                        NoteEvent(
                            round(event_start, 6),
                            round(event_end, 6),
                            current,
                            _midi_name(current),
                            velocity,
                            round(float(np.clip(segment_conf, 0, 1)), 3),
                        )
                    )
            start = index if pitch >= 0 else None
            current = pitch
    return tuple(events)


def transcribe_polyphonic_model(
    source: str | Path,
    *,
    sensitivity: Sensitivity = DEFAULT_SENSITIVITY,
) -> tuple[NoteEvent, ...]:
    """Run Spotify Basic Pitch and normalize its polyphonic note events.

    Basic Pitch returns the onsets, offsets, MIDI pitch and per-note confidence
    predicted by its neural model.  This is intentionally the sole note source
    when installed; the old YIN/chroma methods remain a dependency-light
    fallback for development environments.  The single Note Sensitivity knob
    drives every Basic Pitch threshold through :class:`Sensitivity`.
    """
    if basic_pitch_predict is None:
        return ()
    _, _, predicted = basic_pitch_predict(
        str(Path(source).expanduser().resolve()),
        onset_threshold=sensitivity.onset_threshold,
        frame_threshold=sensitivity.frame_threshold,
        minimum_note_length=sensitivity.minimum_note_length_ms,
        minimum_frequency=45,
        maximum_frequency=4_500,
        multiple_pitch_bends=False,
        melodia_trick=True,
    )
    events = []
    for start, end, pitch, confidence, _ in predicted:
        start, end = float(start), float(end)
        pitch, confidence = int(pitch), float(confidence)
        if not 24 <= pitch <= 108 or end - start < 0.06:
            continue
        if confidence < sensitivity.minimum_confidence:
            continue
        events.append(NoteEvent(
            round(start, 6), round(end, 6), pitch, _midi_name(pitch),
            int(np.clip(round(35 + 90 * confidence), 30, 122)),
            round(float(np.clip(confidence, 0.0, 1.0)), 3),
        ))
    return tuple(sorted(events, key=lambda event: (event.start, event.pitch)))


def melody_from_polyphonic_notes(notes: Sequence[NoteEvent]) -> tuple[NoteEvent, ...]:
    """Keep the highest confident active voice in each model-note interval."""
    if not notes:
        return ()
    boundaries = sorted({point for note in notes for point in (note.start, note.end)})
    segments: list[NoteEvent] = []
    for start, end in zip(boundaries, boundaries[1:]):
        active = [note for note in notes if note.start < end and note.end > start]
        if not active or end - start < 0.03:
            continue
        selected = max(active, key=lambda note: (note.pitch, note.confidence))
        if segments and segments[-1].pitch == selected.pitch and abs(segments[-1].end - start) < 1e-5:
            previous = segments[-1]
            segments[-1] = NoteEvent(
                previous.start, round(end, 6), previous.pitch, previous.note,
                max(previous.velocity, selected.velocity),
                round((previous.confidence + selected.confidence) / 2, 3),
            )
        else:
            segments.append(NoteEvent(
                round(start, 6), round(end, 6), selected.pitch, selected.note,
                selected.velocity, selected.confidence,
            ))
    return tuple(event for event in segments if event.end - event.start >= 0.06)


def _chord_templates() -> list[tuple[int, str, np.ndarray]]:
    templates = []
    for root in range(12):
        for quality, intervals in (("major", (0, 4, 7)), ("minor", (0, 3, 7))):
            vector = np.full(12, 0.08, dtype=np.float64)
            vector[[(root + interval) % 12 for interval in intervals]] = (1.0, 0.8, 0.72)
            vector /= np.linalg.norm(vector)
            templates.append((root, quality, vector))
    return templates


CHORD_TEMPLATES = _chord_templates()


def chords_from_polyphonic_notes(
    notes: Sequence[NoteEvent], key: KeyResult
) -> tuple[ChordEvent, ...]:
    """Recognize major/minor harmony from the model's concurrent notes."""
    if not notes:
        return ()
    boundaries = sorted({point for note in notes for point in (note.start, note.end)})
    labels: list[tuple[float, float, int, float]] = []
    for start, end in zip(boundaries, boundaries[1:]):
        active = [note for note in notes if note.start < end and note.end > start]
        if end - start < 0.08 or len({note.pitch % 12 for note in active}) < 3:
            continue
        chroma = np.zeros(12, dtype=np.float64)
        for note in active:
            chroma[note.pitch % 12] = max(chroma[note.pitch % 12], note.confidence)
        chroma /= max(float(np.linalg.norm(chroma)), 1e-12)
        candidates = np.array([
            float(np.dot(chroma, template)) for _, _, template in CHORD_TEMPLATES
        ])
        winner = int(np.argmax(candidates))
        if candidates[winner] >= 0.58:
            labels.append((start, end, winner, float(candidates[winner])))

    merged: list[tuple[float, float, int, list[float]]] = []
    for start, end, label, score in labels:
        # Model offsets/onsets often leave a few milliseconds between notes
        # of the same sustained chord. Keep that musical chord continuous.
        if merged and merged[-1][2] == label and start - merged[-1][1] <= 0.20:
            old_start, _, _, scores = merged[-1]
            merged[-1] = (old_start, end, label, [*scores, score])
        else:
            merged.append((start, end, label, [score]))
    prefer_flats = key.root in {1, 3, 5, 6, 8, 10} or key.tonic.endswith("b")
    names = NOTE_NAMES_FLAT if prefer_flats else NOTE_NAMES_SHARP
    events: list[ChordEvent] = []
    for start, end, label, scores in merged:
        if end - start < 0.15:
            continue
        root, quality, _ = CHORD_TEMPLATES[label]
        intervals = (0, 4, 7) if quality == "major" else (0, 3, 7)
        base = 48 + root
        while base > 60:
            base -= 12
        suffix = "" if quality == "major" else "m"
        events.append(ChordEvent(
            round(start, 6), round(end, 6), root, quality,
            f"{names[root]}{suffix}", round(float(np.mean(scores)), 3),
            tuple(base + interval for interval in intervals),
            int(np.clip(round(42 + 80 * float(np.mean(scores))), 40, 112)),
        ))
    return tuple(events)


def transcribe_chords(
    samples: np.ndarray,
    sample_rate: int,
    key: KeyResult,
) -> tuple[ChordEvent, ...]:
    """Transcribe sustained major/minor harmony from normalized chroma frames."""
    frame_size, hop_size = 4096, 1024
    chroma, rms = chromagram(samples, sample_rate, frame_size=frame_size, hop_size=hop_size)
    if chroma.size == 0:
        return ()
    floor = max(2e-4, float(np.percentile(rms, 25)) * 0.75)
    labels = np.full(chroma.shape[0], -1, dtype=int)
    scores = np.zeros(chroma.shape[0], dtype=np.float64)
    for index, vector in enumerate(chroma):
        if rms[index] < floor or np.linalg.norm(vector) < 1e-8:
            continue
        candidates = np.array([float(np.dot(vector, template)) for _, _, template in CHORD_TEMPLATES])
        winner = int(np.argmax(candidates))
        if candidates[winner] >= 0.48:
            labels[index] = winner
            sorted_scores = np.sort(candidates)
            scores[index] = np.clip((candidates[winner] - sorted_scores[-2]) * 2.2 + (candidates[winner] - 0.45), 0, 1)
    # Majority filtering prevents one-frame inversions from fragmenting chords.
    filtered = labels.copy()
    for index in range(labels.size):
        window = labels[max(0, index - 2) : min(labels.size, index + 3)]
        valid = window[window >= 0]
        if valid.size:
            counts = np.bincount(valid, minlength=len(CHORD_TEMPLATES))
            filtered[index] = int(np.argmax(counts))

    prefer_flats = key.root in {1, 3, 5, 6, 8, 10} or key.tonic.endswith("b")
    names = NOTE_NAMES_FLAT if prefer_flats else NOTE_NAMES_SHARP
    events: list[ChordEvent] = []
    start: int | None = None
    current = -1
    for index in range(filtered.size + 1):
        label = int(filtered[index]) if index < filtered.size else -1
        if label != current:
            if start is not None and current >= 0:
                event_start = start * hop_size / sample_rate
                event_end = min(samples.size / sample_rate, index * hop_size / sample_rate)
                if event_end - event_start >= 0.20:
                    root, quality, _ = CHORD_TEMPLATES[current]
                    intervals = (0, 4, 7) if quality == "major" else (0, 3, 7)
                    base = 48 + root
                    while base > 60:
                        base -= 12
                    pitches = tuple(base + interval for interval in intervals)
                    confidence = float(np.mean(scores[start:index]))
                    level = float(np.sqrt(np.mean(rms[start:index] ** 2)))
                    velocity = int(np.clip(round(48 + 360 * level), 38, 118))
                    suffix = "" if quality == "major" else "m"
                    events.append(
                        ChordEvent(
                            round(event_start, 6),
                            round(event_end, 6),
                            root,
                            quality,
                            f"{names[root]}{suffix}",
                            round(float(np.clip(confidence, 0, 1)), 3),
                            pitches,
                            velocity,
                        )
                    )
            start = index if label >= 0 else None
            current = label
    return tuple(events)


def _transcribe_targets(
    source: str | Path,
    audio: DecodedAudio,
    key: KeyResult,
    *,
    sensitivity: Sensitivity,
) -> tuple[tuple[NoteEvent, ...], tuple[ChordEvent, ...], tuple[NoteEvent, ...]]:
    """Prefer the neural polyphonic model, with the original DSP fallback."""
    if basic_pitch_predict is not None:
        model_notes = transcribe_polyphonic_model(source, sensitivity=sensitivity)
        return (
            melody_from_polyphonic_notes(model_notes),
            chords_from_polyphonic_notes(model_notes, key),
            model_notes,
        )
    chords = transcribe_chords(audio.samples, audio.sample_rate, key)
    harmony = tuple(
        NoteEvent(note.start, note.end, note.pitch, _midi_name(note.pitch),
                  note.velocity, event.confidence)
        for event in chords
        for note in event.midi()
    )
    return (
        transcribe_melody(audio.samples, audio.sample_rate),
        chords,
        harmony,
    )


def analyze_decoded(
    source: str | Path,
    audio: DecodedAudio,
    *,
    mode: Mode,
    midi_output: str | Path | None = None,
    sensitivity: Sensitivity = DEFAULT_SENSITIVITY,
    tempo_hint: float | None = None,
) -> AnalysisResult:
    tempo = detect_tempo(audio.samples, audio.sample_rate)
    key = detect_key(audio.samples, audio.sample_rate)
    notes, chords, harmony = _transcribe_targets(
        source, audio, key, sensitivity=sensitivity
    )
    key = refine_key_from_notes(key, harmony)
    key = refine_key_from_harmony(key, chords)
    tempo = resolve_project_tempo(
        refine_tempo_from_harmony(tempo, chords, audio.duration), tempo_hint
    )
    groove = extract_groove(audio.samples, audio.sample_rate)
    midi_notes: Sequence[MidiNote]
    if mode == "melody":
        midi_notes = tuple(event.midi() for event in notes)
    elif mode == "chords":
        midi_notes = tuple(event.midi() for event in harmony)
    elif mode == "groove":
        midi_notes = tuple(event.midi() for event in groove)
    else:
        raise ValueError(f"Unsupported Audio DNA mode: {mode}")

    midi_path = None
    if midi_output is not None:
        output = write_midi(
            midi_output,
            midi_notes,
            bpm=tempo.bpm,
            track_name=f"Audio DNA {mode.title()}",
        )
        midi_path = str(output)
    return AnalysisResult(
        source=str(Path(source).expanduser().resolve()),
        duration=round(audio.duration, 6),
        sample_rate=audio.sample_rate,
        mode=mode,
        tempo=tempo,
        key=key,
        notes=notes,
        chords=chords,
        harmony=harmony,
        groove=groove,
        midi_path=midi_path,
    )


def analyze_all_decoded(
    source: str | Path,
    audio: DecodedAudio,
    *,
    midi_outputs: dict[str, str | Path] | None = None,
    sensitivity: Sensitivity = DEFAULT_SENSITIVITY,
    tempo_hint: float | None = None,
) -> AnalysisBundle:
    """Analyze Melody, Chords and Groove from one decode and tonal pass."""
    tempo = detect_tempo(audio.samples, audio.sample_rate)
    key = detect_key(audio.samples, audio.sample_rate)
    melody, chords, harmony = _transcribe_targets(
        source, audio, key, sensitivity=sensitivity
    )
    key = refine_key_from_notes(key, harmony)
    key = refine_key_from_harmony(key, chords)
    tempo = resolve_project_tempo(
        refine_tempo_from_harmony(tempo, chords, audio.duration), tempo_hint
    )
    groove = extract_groove(audio.samples, audio.sample_rate)
    midi_paths: dict[str, str] = {}
    if midi_outputs:
        unexpected = set(midi_outputs) - {"melody", "chords", "groove"}
        if unexpected:
            raise ValueError(f"Unsupported Audio DNA MIDI output(s): {sorted(unexpected)}")
        for mode, output in midi_outputs.items():
            notes: Sequence[MidiNote]
            if mode == "melody":
                notes = tuple(event.midi() for event in melody)
            elif mode == "chords":
                notes = tuple(event.midi() for event in harmony)
            else:
                notes = tuple(event.midi() for event in groove)
            midi_paths[mode] = str(write_midi(
                output, notes, bpm=tempo.bpm, track_name=f"Audio DNA {mode.title()}"
            ))
    return AnalysisBundle(
        source=str(Path(source).expanduser().resolve()),
        duration=round(audio.duration, 6),
        sample_rate=audio.sample_rate,
        tempo=tempo,
        key=key,
        melody=melody,
        chords=chords,
        harmony=harmony,
        groove=groove,
        midi_paths=midi_paths,
    )


def analyze_file(
    source: str | Path,
    *,
    mode: Mode,
    midi_output: str | Path | None = None,
    sample_rate: int = 22_050,
    max_duration: float = 30 * 60,
    sensitivity: float = DEFAULT_SENSITIVITY.value,
    tempo_hint: float | None = None,
) -> AnalysisResult:
    audio = decode_audio(source, sample_rate=sample_rate, max_duration=max_duration)
    return analyze_decoded(
        source, audio, mode=mode, midi_output=midi_output,
        sensitivity=Sensitivity(sensitivity), tempo_hint=tempo_hint,
    )


def analyze_all_file(
    source: str | Path,
    *,
    midi_outputs: dict[str, str | Path] | None = None,
    sample_rate: int = 22_050,
    max_duration: float = 30 * 60,
    sensitivity: float = DEFAULT_SENSITIVITY.value,
    tempo_hint: float | None = None,
) -> AnalysisBundle:
    """Decode once and return Melody, Chords and Groove candidates."""
    audio = decode_audio(source, sample_rate=sample_rate, max_duration=max_duration)
    return analyze_all_decoded(
        source, audio, midi_outputs=midi_outputs,
        sensitivity=Sensitivity(sensitivity), tempo_hint=tempo_hint,
    )
