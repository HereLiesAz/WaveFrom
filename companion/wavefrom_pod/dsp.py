"""Direction-of-arrival helpers for the Pi pod.

Pure-math pieces (``doa_from_phase``) have no third-party deps and are unit
tested. The array-processing helpers use numpy when present and degrade
gracefully otherwise — the Pi is where real phase-coherent DF runs, but the
math here is the same as the phone's TwoElementInterferometer so both agree.
"""
from __future__ import annotations

import cmath
import math

SPEED_OF_LIGHT = 299_792_458.0


def doa_from_phase(phase_delta_rad: float, spacing_m: float, freq_hz: float) -> tuple[float, float]:
    """Bearing (deg from broadside) for a two-element baseline.

    Returns (theta, mirror) where mirror = 180 - theta is the front/back
    ambiguity a linear two-element array cannot resolve. Matches the phone's
    Kotlin TwoElementInterferometer.
    """
    lam = SPEED_OF_LIGHT / freq_hz
    s = max(-1.0, min(1.0, phase_delta_rad * lam / (2.0 * math.pi * spacing_m)))
    theta = math.degrees(math.asin(s))
    return theta, 180.0 - theta


def interferometric_phase(iq_a, iq_b) -> float:
    """Mean phase difference (radians) between two coherent IQ streams.

    Uses numpy if available; falls back to a pure-Python loop. Inputs are
    sequences of complex samples from two antennas.
    """
    try:
        import numpy as np  # local import: optional dependency

        a = np.asarray(iq_a, dtype=np.complex128)
        b = np.asarray(iq_b, dtype=np.complex128)
        cross = np.vdot(b, a)  # sum(conj(b) * a)
        return float(math.atan2(cross.imag, cross.real))
    except ImportError:
        sr = si = 0.0
        for x, y in zip(iq_a, iq_b):
            # x * conj(y)
            sr += x.real * y.real + x.imag * y.imag
            si += x.imag * y.real - x.real * y.imag
        return math.atan2(si, sr)


# --------------------------------------------------------------------------- #
# Power spectrum
# --------------------------------------------------------------------------- #

def _fft(samples: list[complex], n: int) -> list[complex]:
    """FFT via numpy if available, else a direct O(n^2) DFT (fine for tests)."""
    try:
        import numpy as np

        return np.fft.fft(np.asarray(samples, dtype=np.complex128), n).tolist()
    except ImportError:
        out = [0j] * n
        for k in range(n):
            acc = 0j
            ang = -2.0 * math.pi * k / n
            for t in range(n):
                acc += samples[t] * cmath.exp(1j * ang * t)
            out[k] = acc
        return out


def power_spectrum(
    iq: list[complex],
    fs_hz: float,
    center_hz: float = 0.0,
    nfft: int | None = None,
) -> tuple[list[float], list[float]]:
    """Return (freqs_hz, powers_db) ascending in frequency (DC-centred).

    powers_db is 10*log10 of the per-bin power. Works with or without numpy.
    """
    n = nfft or len(iq)
    if n <= 0 or len(iq) == 0:
        return [], []
    samples = list(iq[:n])
    if len(samples) < n:
        samples += [0j] * (n - len(samples))
    spectrum = _fft(samples, n)
    df = fs_hz / n
    pairs = []
    for k in range(n):
        # Map bin index to a centred frequency in [-fs/2, fs/2).
        f = (k - n if k >= (n + 1) // 2 else k) * df + center_hz
        power = (abs(spectrum[k]) ** 2) / n
        pairs.append((f, 10.0 * math.log10(power + 1e-12)))
    pairs.sort(key=lambda p: p[0])
    return [p[0] for p in pairs], [p[1] for p in pairs]


def detect_peaks(
    powers_db: list[float],
    freqs_hz: list[float],
    threshold_db_over_noise: float = 10.0,
    min_sep_bins: int = 3,
) -> list[tuple[float, float]]:
    """Local maxima rising threshold dB over the noise floor (median), spaced
    at least min_sep_bins apart. Returns (freq_hz, power_db) sorted by power."""
    n = len(powers_db)
    if n == 0:
        return []
    floor = sorted(powers_db)[n // 2]  # median
    cutoff = floor + threshold_db_over_noise
    candidates = []
    for i in range(1, n - 1):
        p = powers_db[i]
        if p >= cutoff and p >= powers_db[i - 1] and p >= powers_db[i + 1]:
            candidates.append((freqs_hz[i], p, i))
    candidates.sort(key=lambda c: c[1], reverse=True)
    chosen: list[tuple[float, float]] = []
    used: list[int] = []
    for f, p, idx in candidates:
        if all(abs(idx - u) >= min_sep_bins for u in used):
            chosen.append((f, p))
            used.append(idx)
    return chosen


# --------------------------------------------------------------------------- #
# Array geometry + beamforming DoA
# --------------------------------------------------------------------------- #

def ula_positions(n: int, spacing_m: float) -> list[tuple[float, float]]:
    """Uniform linear array along x, centred on the origin."""
    return [((i - (n - 1) / 2.0) * spacing_m, 0.0) for i in range(n)]


def uca_positions(n: int, radius_m: float) -> list[tuple[float, float]]:
    """Uniform circular array (e.g. KrakenSDR's 5 elements)."""
    return [
        (radius_m * math.cos(2 * math.pi * i / n), radius_m * math.sin(2 * math.pi * i / n))
        for i in range(n)
    ]


def steering_vector(
    positions: list[tuple[float, float]], az_deg: float, freq_hz: float
) -> list[complex]:
    """Array response for a plane wave from [az_deg] (measured from +x, CCW)."""
    k = 2.0 * math.pi * freq_hz / SPEED_OF_LIGHT
    a = math.radians(az_deg)
    dx, dy = math.cos(a), math.sin(a)
    return [cmath.exp(1j * k * (x * dx + y * dy)) for (x, y) in positions]


def covariance(snapshots: list[list[complex]]) -> list[list[complex]]:
    """Spatial covariance R = (1/K) sum_k x_k x_k^H for length-N snapshots."""
    if not snapshots:
        return []
    n = len(snapshots[0])
    r = [[0j] * n for _ in range(n)]
    for x in snapshots:
        for i in range(n):
            for j in range(n):
                r[i][j] += x[i] * x[j].conjugate()
    k = len(snapshots)
    for i in range(n):
        for j in range(n):
            r[i][j] /= k
    return r


def _quad_form(a: list[complex], r: list[list[complex]]) -> float:
    """Real value of a^H R a."""
    n = len(a)
    acc = 0j
    for i in range(n):
        ai = a[i].conjugate()
        row = r[i]
        for j in range(n):
            acc += ai * row[j] * a[j]
    return acc.real


def bartlett_doa(
    r: list[list[complex]],
    positions: list[tuple[float, float]],
    freq_hz: float,
    scan_deg: range | list[float] = range(0, 360),
    n_peaks: int = 1,
) -> list[tuple[float, float]]:
    """Conventional (Bartlett) beamformer DoA: peaks of P(θ)=a(θ)^H R a(θ).

    No eigendecomposition, so it runs without numpy. Returns up to [n_peaks]
    (azimuth_deg, power_db) sorted by power.
    """
    if not r:
        return []
    spectrum = []
    for az in scan_deg:
        p = _quad_form(steering_vector(positions, float(az), freq_hz), r)
        spectrum.append((float(az), 10.0 * math.log10(p + 1e-12)))
    # Local maxima (circular if scanning a full turn).
    m = len(spectrum)
    peaks = []
    for i in range(m):
        prev = spectrum[(i - 1) % m][1]
        nxt = spectrum[(i + 1) % m][1]
        if spectrum[i][1] >= prev and spectrum[i][1] >= nxt:
            peaks.append(spectrum[i])
    peaks.sort(key=lambda c: c[1], reverse=True)
    return peaks[:n_peaks]


def music_doa(
    r: list[list[complex]],
    positions: list[tuple[float, float]],
    freq_hz: float,
    scan_deg: range | list[float] = range(0, 360),
    n_sources: int = 1,
    n_peaks: int = 1,
) -> list[tuple[float, float]]:
    """High-resolution MUSIC DoA. Requires numpy (eigendecomposition); raises
    NotImplementedError without it — callers fall back to [bartlett_doa]."""
    try:
        import numpy as np
    except ImportError as e:  # pragma: no cover - depends on env
        raise NotImplementedError("music_doa requires numpy") from e

    rm = np.asarray(r, dtype=np.complex128)
    _, vecs = np.linalg.eigh(rm)
    noise = vecs[:, : rm.shape[0] - n_sources]  # smallest eigenvalues' vectors
    out = []
    for az in scan_deg:
        a = np.asarray(steering_vector(positions, float(az), freq_hz), dtype=np.complex128)
        proj = noise.conj().T @ a
        p = 1.0 / float((proj.conj() @ proj).real + 1e-12)
        out.append((float(az), 10.0 * math.log10(p)))
    out.sort(key=lambda c: c[1], reverse=True)
    return out[:n_peaks]
