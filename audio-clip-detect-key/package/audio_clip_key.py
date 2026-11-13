"""Key estimator for Detect Event Key, run inside the installed Audio Tools runtime.

The engine's spectral key profile is broad: a clip is often reported as its dominant
(Bb major for Eb major, Eb minor for G# minor). The transcribed notes say more: the
pitch classes that actually sound fix the key signature, and one foreign note (A in
Eb major, F in G# minor) rules a neighbouring key out. This script picks the key
signature from note durations, keeps the engine's major/minor reading unless the
notes contradict it, and prints one "KEY<tab>name<tab>confidence" line.
"""

import sys

MAJOR_STEPS = (0, 2, 4, 5, 7, 9, 11)
MINOR_STEPS = (0, 2, 3, 5, 7, 8, 10)
# Major: I ii iii IV V vi. Natural minor: i III iv v VI VII, plus the harmonic-minor V.
MAJOR_TRIADS = ((0, "major"), (2, "minor"), (4, "minor"), (5, "major"), (7, "major"), (9, "minor"))
MINOR_TRIADS = ((0, "minor"), (3, "major"), (5, "minor"), (7, "minor"), (7, "major"),
                (8, "major"), (10, "major"))
SHARP = ("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
# Less transcribed material than this is too little evidence to overrule the engine.
MIN_NOTE_SECONDS = 2.0
MIN_PITCH_CLASSES = 4
# Note-time share a signature may leave unexplained and still compete (transcription noise).
SIGNATURE_TOLERANCE = 0.02
# Chromatic material (glides, blue notes, noisy transcription) fits no signature this well;
# there the notes cannot overrule the engine.
MIN_SIGNATURE_FIT = 0.95
FLAT = ("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")


def key_name(root, scale):
    """Usual spellings: Db/Eb/Ab/Bb major, C#/F#/G# minor."""
    if scale == "minor":
        return "%s minor" % (SHARP if root in (1, 6, 8) else FLAT)[root]
    return "%s major" % (SHARP if root == 6 else FLAT)[root]


def signature(root, scale):
    """Position on the circle of fifths of the key's major-scale tonic."""
    return ((root + (3 if scale == "minor" else 0)) * 7) % 12


def scale_fit(energy, root, scale):
    """Share of pitch-class energy inside the scale; energy sums to 1."""
    steps = MAJOR_STEPS if scale == "major" else MINOR_STEPS
    return sum(energy[(root + step) % 12] for step in steps)


def rank(notes, chords, energy, base, base_confidence=0.0):
    """notes: seconds sounded per pitch class (12 values); chords: (root, quality, weight) in
    time order; energy: 12 spectral values summing to 1; base: (root, scale) from the engine.
    Returns (root, scale, confidence, method)."""
    sounded = sum(notes)
    if sounded < MIN_NOTE_SECONDS or sum(1 for value in notes if value > 0.02 * sounded) < MIN_PITCH_CLASSES:
        return base[0], base[1], round(base_confidence, 3), "engine"
    share = [value / sounded for value in notes]
    # The tonic is often the rarest note of a loop, so note weight cannot name the key; what
    # the notes do fix is the key signature: a key that leaves sounded notes outside its
    # scale is out. Only the signatures explaining (nearly) the most note time compete.
    fits = {root: scale_fit(share, root, "major") for root in range(12)}
    best = max(fits.values())
    if best < MIN_SIGNATURE_FIT:
        return base[0], base[1], round(base_confidence, 3), "engine"
    admitted = [root for root in range(12) if fits[root] >= best - SIGNATURE_TOLERANCE]
    rejected = max([fit for root, fit in fits.items() if root not in admitted] or [0.0])
    chord_total = sum(weight for _, _, weight in chords)
    scored = []
    for major_root in admitted:
        for root, scale in ((major_root, "major"), ((major_root + 9) % 12, "minor")):
            tonic = (root, scale)
            # Relative major and minor share every note: trust the engine's mode first, then
            # stay near its signature, then weigh the tonic note and chord.
            steps = abs(signature(root, scale) - signature(base[0], base[1]))
            score = (0.25 * (scale == base[1]) + 0.10 * (tonic == tuple(base))
                     - 0.05 * min(steps, 12 - steps) + 0.30 * share[root]
                     + 0.20 * scale_fit(energy, root, scale) + fits[major_root])
            if chord_total > 0.0:
                score += 0.20 * sum(w for r, q, w in chords if (r, q) == tonic) / chord_total
            scored.append((score, root, scale))
    scored.sort(reverse=True)
    top, root, scale = scored[0]
    confidence = 0.45 + (0.20 if len(admitted) == 1 else 0.0) + min(0.20, (best - rejected) * 2.0) \
        + min(0.10, (top - scored[1][0]) * 0.4)
    return root, scale, round(min(0.95, confidence), 3), "notes"


def main(path):
    import numpy as np
    from audio_dna_engine import analysis
    from audio_dna_engine.audio import decode_audio

    audio = decode_audio(path)
    base = analysis.detect_key(audio.samples, audio.sample_rate)
    try:
        chroma, rms = analysis.chromagram(audio.samples, audio.sample_rate)
        active = rms > max(1e-5, float(np.percentile(rms, 25)) * 0.7)
        vector = (chroma[active] if np.any(active) else chroma).sum(axis=0).astype(float) ** 2
        energy = [float(value) for value in vector / max(float(vector.sum()), 1e-12)]
        chords, notes = [], [0.0] * 12
        try:
            _, events, harmony = analysis._transcribe_targets(
                path, audio, base, sensitivity=analysis.DEFAULT_SENSITIVITY)
            chords = [(int(c.root) % 12, str(c.quality), float(c.end - c.start) * float(c.confidence))
                      for c in events if c.confidence >= 0.5 and c.end - c.start >= 0.3
                      and c.quality in ("major", "minor")]
            for note in harmony:
                notes[int(note.pitch) % 12] += max(0.0, float(note.end - note.start))
        except Exception as failure:  # transcription is optional evidence
            print("KEYINFO\ttranscription unavailable: %s" % failure)
        scale = base.scale if base.scale in ("major", "minor") else "major"
        root, scale, confidence, method = rank(notes, chords, energy, (int(base.root) % 12, scale), float(base.confidence))
        print("KEYINFO\tengine %s, %.1f s of notes, %d chords, method %s"
              % (base.name, sum(notes), len(chords), method))
        print("KEY\t%s\t%.3f" % (key_name(root, scale), confidence))
    except Exception as failure:  # never lose the engine's own answer
        print("KEYINFO\tre-ranking failed: %s" % failure)
        print("KEY\t%s\t%.3f" % (base.name, base.confidence))


if __name__ == "__main__":
    main(sys.argv[1])
