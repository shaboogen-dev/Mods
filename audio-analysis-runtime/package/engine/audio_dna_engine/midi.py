"""Minimal standards-compliant MIDI writer for Audio DNA events."""

from __future__ import annotations

import math
import struct
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


@dataclass(frozen=True)
class MidiNote:
    start: float
    end: float
    pitch: int
    velocity: int = 96

    def __post_init__(self) -> None:
        if self.start < 0 or self.end <= self.start:
            raise ValueError(f"Invalid note interval: {self.start}..{self.end}")
        if not 0 <= self.pitch <= 127:
            raise ValueError(f"Invalid MIDI pitch: {self.pitch}")
        if not 1 <= self.velocity <= 127:
            raise ValueError(f"Invalid MIDI velocity: {self.velocity}")


def _variable_length(value: int) -> bytes:
    if value < 0:
        raise ValueError("MIDI delta cannot be negative")
    buffer = value & 0x7F
    result = bytearray([buffer])
    while value >> 7:
        value >>= 7
        buffer = (value & 0x7F) | 0x80
        result.insert(0, buffer)
    return bytes(result)


def _text_meta(kind: int, text: str) -> bytes:
    payload = text.encode("utf-8")
    return bytes([0xFF, kind]) + _variable_length(len(payload)) + payload


def write_midi(
    path: str | Path,
    notes: Iterable[MidiNote],
    *,
    bpm: float,
    track_name: str,
    ppq: int = 480,
) -> Path:
    """Write a format-0 MIDI file preserving Audio DNA's detected timing."""
    if not math.isfinite(bpm) or bpm <= 0:
        raise ValueError(f"Invalid BPM: {bpm}")
    output = Path(path).expanduser().resolve()
    output.parent.mkdir(parents=True, exist_ok=True)

    ticks_per_second = ppq * bpm / 60.0
    events: list[tuple[int, int, bytes]] = []
    for note in notes:
        start_tick = max(0, round(note.start * ticks_per_second))
        end_tick = max(start_tick + 1, round(note.end * ticks_per_second))
        events.append((start_tick, 1, bytes([0x90, note.pitch, note.velocity])))
        events.append((end_tick, 0, bytes([0x80, note.pitch, 0])))
    events.sort(key=lambda item: (item[0], item[1], item[2][1]))

    tempo = max(1, round(60_000_000 / bpm))
    track = bytearray()
    track.extend(_variable_length(0))
    track.extend(_text_meta(0x03, track_name))
    track.extend(_variable_length(0))
    track.extend(bytes([0xFF, 0x51, 0x03]))
    track.extend(tempo.to_bytes(3, "big"))

    previous_tick = 0
    for tick, _, message in events:
        track.extend(_variable_length(tick - previous_tick))
        track.extend(message)
        previous_tick = tick
    track.extend(_variable_length(0))
    track.extend(bytes([0xFF, 0x2F, 0x00]))

    header = b"MThd" + struct.pack(">IHHH", 6, 0, 1, ppq)
    chunk = b"MTrk" + struct.pack(">I", len(track)) + bytes(track)
    output.write_bytes(header + chunk)
    return output
