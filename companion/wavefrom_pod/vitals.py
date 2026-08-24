"""Presence and vital-sign estimation from a live CSI stream.

This is a second, independent consumer of the same antenna x subcarrier CSI
matrix [WifiCsiBackend] already parses for direction-of-arrival — it never
touches the network. Only derived scalars (presence, breathing/heart rate,
each with a confidence) are meant to leave the Pi; raw CSI stays local.

Method: reduce each frame to one "coherent phasor" — the mean unit phasor
across every antenna x subcarrier CSI value — then track its angle over time,
incrementally unwrapped so it stays a continuous real-valued signal (taking
cos/sin of the wrapped angle instead would double the apparent frequency for
small phase deviations — the classic phase-detector artifact of squaring a
modulated phase). Breathing (0.1-0.5 Hz) and heart rate (0.8-2.0 Hz) are
recovered by bandpass-filtering that unwrapped-phase series (FFT mask +
inverse FFT, so no scipy dependency) and counting zero-crossings in the
filtered signal. Presence has
no model requirement: it compares the current window's circular variance of
phase against a variance baseline learned during an initial calibration
period, the same "ambient calibration, no labels needed" shape used elsewhere
in this pod's DoA code.

Every estimate carries an explicit confidence and can be `None` — this module
never fabricates a rate it didn't actually measure. It is not wired to the
wire protocol yet (no `occupancy` message exists) and every reading here is a
room-level estimate from a single NIC, not a medical measurement.
"""
from __future__ import annotations

import math
from dataclasses import dataclass

from .dsp import _fft  # reuse the same FFT (numpy fast path + pure-Python fallback)


@dataclass
class VitalsEstimate:
    """One occupancy snapshot. `None` rates mean "not enough signal yet", not zero."""

    present: bool
    presence_confidence: float
    breathing_bpm: float | None
    breathing_confidence: float
    heart_bpm: float | None
    heart_confidence: float


def _mean_phasor(csi_matrix: list[list[complex]]) -> complex:
    """Mean unit phasor across every CSI value in the frame.

    Averaging unit phasors (not raw values) makes the result insensitive to
    per-subcarrier amplitude, and its angle is a single wrap-safe "representative
    phase" for the whole frame — no per-subcarrier unwrapping needed.
    """
    total = 0j
    count = 0
    for row in csi_matrix:
        for c in row:
            mag = abs(c)
            if mag > 0:
                total += c / mag
                count += 1
    return total / count if count else 0j


def _circular_variance(phases: list[float]) -> float:
    """1 - |mean resultant length|, in [0, 1]. 0 = phase is constant, 1 = uniform."""
    if not phases:
        return 0.0
    n = len(phases)
    s = sum(math.sin(p) for p in phases)
    c = sum(math.cos(p) for p in phases)
    r = math.hypot(s, c) / n
    return 1.0 - r


def _resample_uniform(
    times: list[float], values: list[float], hz: float
) -> tuple[list[float], list[float]]:
    """Linear-interpolate an irregularly-sampled series onto a uniform grid."""
    t0, t1 = times[0], times[-1]
    n = max(2, int(round((t1 - t0) * hz)) + 1)
    grid = [t0 + i / hz for i in range(n)]
    out: list[float] = []
    j = 0
    last = len(times) - 1
    for g in grid:
        while j < last and times[j + 1] < g:
            j += 1
        t_lo, v_lo = times[j], values[j]
        if j < last:
            t_hi, v_hi = times[j + 1], values[j + 1]
        else:
            t_hi, v_hi = t_lo, v_lo
        out.append(v_lo if t_hi == t_lo else v_lo + (g - t_lo) / (t_hi - t_lo) * (v_hi - v_lo))
    return grid, out


def _ifft(spectrum: list[complex], n: int) -> list[complex]:
    """Inverse FFT via the conjugation trick, reusing the forward [_fft]."""
    out = _fft([x.conjugate() for x in spectrum], n)
    return [x.conjugate() / n for x in out]


def _bandpass(
    values: list[float], sample_hz: float, lo_hz: float, hi_hz: float
) -> tuple[list[float], float]:
    """FFT-mask [values] to [lo_hz, hi_hz] and return (filtered, in-band energy ratio)."""
    n = len(values)
    mean = sum(values) / n
    spectrum = _fft([complex(v - mean, 0.0) for v in values], n)
    total_energy = sum(abs(x) ** 2 for x in spectrum[1:])  # exclude DC
    df = sample_hz / n
    masked = [0j] * n
    band_energy = 0.0
    for k in range(n):
        f = (k - n if k >= (n + 1) // 2 else k) * df
        if lo_hz <= abs(f) <= hi_hz:
            masked[k] = spectrum[k]
            band_energy += abs(spectrum[k]) ** 2
    filtered = [x.real for x in _ifft(masked, n)]
    energy_ratio = min(1.0, band_energy / total_energy) if total_energy > 0 else 0.0
    return filtered, energy_ratio


def _zero_crossing_rate_hz(values: list[float], sample_hz: float) -> float:
    """Oscillation frequency from zero-crossings (2 crossings per cycle)."""
    if len(values) < 2:
        return 0.0
    crossings = sum(
        1 for i in range(1, len(values)) if (values[i - 1] < 0) != (values[i] < 0)
    )
    duration_s = (len(values) - 1) / sample_hz
    return (crossings / 2.0) / duration_s if duration_s > 0 else 0.0


class VitalsExtractor:
    """Stateful per-zone extractor: feed it CSI frames, read back an estimate.

    One instance per sensing zone (a NIC covers one zone). Not thread-safe;
    call [push] and [estimate] from the same poll loop, mirroring how
    [WifiCsiBackend.poll] already batches frames.
    """

    BREATHING_BAND_HZ = (0.1, 0.5)
    HEART_BAND_HZ = (0.8, 2.0)
    RESAMPLE_HZ = 8.0
    MIN_SAMPLES = 8
    #: Below this fraction of the (non-DC) spectral energy in-band, don't report a
    #: rate at all — spectral leakage from an out-of-band signal is not a measurement.
    MIN_BAND_ENERGY_RATIO = 0.15
    #: How many multiples of the empty-room baseline variance counts as "present".
    PRESENCE_RATIO = 3.0

    def __init__(self, window_s: float = 30.0, calibration_s: float = 20.0) -> None:
        self.window_s = window_s
        self.calibration_s = calibration_s
        # (ts, wrapped phase, unwrapped phase) — wrapped feeds circular-variance
        # presence; unwrapped is the continuous signal the rate bandpass filters.
        self._samples: list[tuple[float, float, float]] = []
        self._t_start: float | None = None
        self._last_wrapped: float | None = None
        self._unwrap_offset = 0.0
        self._baseline_variance: float | None = None

    def push(self, csi_matrix: list[list[complex]], ts: float) -> None:
        """Feed one frame's antenna x subcarrier CSI matrix at time [ts] (seconds)."""
        phasor = _mean_phasor(csi_matrix)
        if phasor == 0:
            return
        wrapped = math.atan2(phasor.imag, phasor.real)
        if self._t_start is None:
            self._t_start = ts
            self._last_wrapped = wrapped
        else:
            delta = wrapped - self._last_wrapped
            if delta > math.pi:
                self._unwrap_offset -= 2.0 * math.pi
            elif delta < -math.pi:
                self._unwrap_offset += 2.0 * math.pi
            self._last_wrapped = wrapped
        self._samples.append((ts, wrapped, wrapped + self._unwrap_offset))
        cutoff = ts - self.window_s
        while self._samples and self._samples[0][0] < cutoff:
            self._samples.pop(0)
        if self._baseline_variance is None and ts - self._t_start >= self.calibration_s:
            self._baseline_variance = max(
                1e-4, _circular_variance([w for _, w, _ in self._samples])
            )

    def estimate(self) -> VitalsEstimate:
        present, presence_conf = self._presence()
        breathing_bpm, breathing_conf = self._rate_bpm(*self.BREATHING_BAND_HZ)
        heart_bpm, heart_conf = self._rate_bpm(*self.HEART_BAND_HZ)
        return VitalsEstimate(
            present=present,
            presence_confidence=presence_conf,
            breathing_bpm=breathing_bpm,
            breathing_confidence=breathing_conf,
            heart_bpm=heart_bpm,
            heart_confidence=heart_conf,
        )

    def _presence(self) -> tuple[bool, float]:
        phases = [w for _, w, _ in self._samples]
        if len(phases) < self.MIN_SAMPLES:
            return False, 0.0
        variance = _circular_variance(phases)
        baseline = self._baseline_variance if self._baseline_variance is not None else 0.01
        ratio = variance / baseline
        present = ratio >= self.PRESENCE_RATIO
        confidence = max(0.0, min(1.0, (ratio - 1.0) / (self.PRESENCE_RATIO * 2.0)))
        return present, confidence

    def _rate_bpm(self, lo_hz: float, hi_hz: float) -> tuple[float | None, float]:
        if len(self._samples) < self.MIN_SAMPLES:
            return None, 0.0
        times = [t for t, _, _ in self._samples]
        duration = times[-1] - times[0]
        if duration < 2.0 / lo_hz:  # need at least ~2 cycles of the slowest band edge
            return None, 0.0
        unwrapped = [u for _, _, u in self._samples]
        _, resampled = _resample_uniform(times, unwrapped, self.RESAMPLE_HZ)
        if len(resampled) < self.MIN_SAMPLES:
            return None, 0.0
        filtered, energy_ratio = _bandpass(resampled, self.RESAMPLE_HZ, lo_hz, hi_hz)
        if energy_ratio < self.MIN_BAND_ENERGY_RATIO:
            return None, 0.0
        freq_hz = _zero_crossing_rate_hz(filtered, self.RESAMPLE_HZ)
        if freq_hz <= 0.0:
            return None, 0.0
        return freq_hz * 60.0, energy_ratio
