"""BML: progress output for the task row in Bitwig's notification centre.

Output only: nothing here changes what the engine computes. When ``AUDIO_DNA_PROGRESS_FILE`` is
set (Helper: Audio Analysis sets it for each run), :func:`report` writes the overall fraction,
0 to 1, as one number to that file, replacing it atomically. ``AUDIO_DNA_PROGRESS_SPAN``
("low:high") maps a run's own 0..1 into part of a larger job; :func:`span` narrows it for the code
it wraps, child processes included (they inherit the environment).
"""

from __future__ import annotations

import os
from contextlib import contextmanager
from typing import Iterator

FILE = "AUDIO_DNA_PROGRESS_FILE"
SPAN = "AUDIO_DNA_PROGRESS_SPAN"


def _span() -> tuple[float, float]:
    try:
        low, high = (float(part) for part in os.environ.get(SPAN, "0:1").split(":", 1))
        return low, high
    except ValueError:
        return 0.0, 1.0


def report(fraction: float) -> None:
    """Writes ``fraction`` of this run (within the current span); never raises."""
    path = os.environ.get(FILE)
    if not path:
        return
    try:
        low, high = _span()
        value = low + (high - low) * min(1.0, max(0.0, float(fraction)))
        temporary = f"{path}.{os.getpid()}.tmp"
        with open(temporary, "w", encoding="ascii") as handle:
            handle.write(f"{value:.4f}\n")
        os.replace(temporary, path)
    except (OSError, ValueError):
        pass  # progress is optional: a reader holding the file open, a full disk


@contextmanager
def span(low: float, high: float) -> Iterator[None]:
    """Within this block, a run's 0..1 is ``low``..``high`` of the current span."""
    old = os.environ.get(SPAN)
    outer_low, outer_high = _span()
    os.environ[SPAN] = (f"{outer_low + (outer_high - outer_low) * low:.4f}:"
                        f"{outer_low + (outer_high - outer_low) * high:.4f}")
    try:
        yield
    finally:
        if old is None:
            os.environ.pop(SPAN, None)
        else:
            os.environ[SPAN] = old


class SegmentCounter:
    """Counts finished items over a known number of loops, reporting after each one."""

    def __init__(self, loops: int) -> None:
        self.loops = max(1, loops)
        self.finished_loops = 0

    def wrap(self, items, real_tqdm, **kwargs):
        """Iterates ``items`` through ``real_tqdm`` (unchanged output), reporting progress as it goes."""
        try:
            total = len(items)
        except TypeError:
            total = 0
        iterator = real_tqdm(items, **kwargs) if real_tqdm is not None else items
        done = 0
        for item in iterator:
            yield item
            done += 1
            if total:
                report((self.finished_loops + done / total) / self.loops)
        self.finished_loops = min(self.loops, self.finished_loops + 1)
        report(self.finished_loops / self.loops)
