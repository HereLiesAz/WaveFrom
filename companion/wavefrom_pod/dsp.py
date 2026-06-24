"""Direction-of-arrival helpers for the Pi pod.

Pure-math pieces (``doa_from_phase``) have no third-party deps and are unit
tested. The array-processing helpers use numpy when present and degrade
gracefully otherwise — the Pi is where real phase-coherent DF runs, but the
math here is the same as the phone's TwoElementInterferometer so both agree.
"""
from __future__ import annotations

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
