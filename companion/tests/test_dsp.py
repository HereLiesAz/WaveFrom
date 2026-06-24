"""Tests for the pod DoA math (no hardware required)."""
import cmath
import math
import random
import unittest

from wavefrom_pod.backends import RtlSdrBackend
from wavefrom_pod.dsp import (
    SPEED_OF_LIGHT,
    bartlett_doa,
    covariance,
    detect_peaks,
    doa_from_phase,
    interferometric_phase,
    power_spectrum,
    steering_vector,
    ula_positions,
    uca_positions,
)


def _snapshots(positions, true_az, freq, k=24, noise=0.05, seed=0):
    rng = random.Random(seed)
    a = steering_vector(positions, true_az, freq)
    snaps = []
    for _ in range(k):
        s = complex(rng.gauss(0, 1), rng.gauss(0, 1))
        snaps.append([
            ai * s + noise * complex(rng.gauss(0, 1), rng.gauss(0, 1)) for ai in a
        ])
    return snaps


def phase_for(theta_deg, spacing_m, freq_hz):
    lam = SPEED_OF_LIGHT / freq_hz
    return 2.0 * math.pi * spacing_m * math.sin(math.radians(theta_deg)) / lam


class DspTest(unittest.TestCase):
    def test_doa_recovers_angle(self):
        phase = phase_for(30.0, 0.5, 433e6)
        theta, mirror = doa_from_phase(phase, 0.5, 433e6)
        self.assertAlmostEqual(theta, 30.0, places=3)
        self.assertAlmostEqual(mirror, 150.0, places=3)

    def test_interferometric_phase_of_known_offset(self):
        phi = 1.0
        n = 256
        iq_a = [cmath.exp(1j * 0.3 * t) for t in range(n)]
        iq_b = [cmath.exp(1j * (0.3 * t - phi)) for t in range(n)]
        self.assertAlmostEqual(interferometric_phase(iq_a, iq_b), phi, places=4)

    def test_bearing_from_iq_end_to_end(self):
        spacing, freq = 0.5, 433e6
        phi = phase_for(20.0, spacing, freq)
        n = 512
        iq_a = [cmath.exp(1j * 0.2 * t) for t in range(n)]
        iq_b = [cmath.exp(1j * (0.2 * t - phi)) for t in range(n)]
        theta, mirror = RtlSdrBackend.bearing_from_iq(iq_a, iq_b, freq, spacing)
        self.assertAlmostEqual(theta, 20.0, places=2)
        self.assertAlmostEqual(mirror, 160.0, places=2)


class SpectrumTest(unittest.TestCase):
    def test_power_spectrum_peaks_at_tone(self):
        fs, n = 1_000_000.0, 64
        f0 = fs * 8 / n  # exactly bin 8 → 125 kHz
        iq = [cmath.exp(2j * math.pi * f0 * t / fs) for t in range(n)]
        freqs, powers = power_spectrum(iq, fs, center_hz=0.0, nfft=n)
        imax = max(range(len(powers)), key=lambda i: powers[i])
        self.assertLess(abs(freqs[imax] - f0), fs / n)

    def test_detect_peaks_finds_the_tone(self):
        fs, n = 1_000_000.0, 64
        f0 = fs * 8 / n
        iq = [cmath.exp(2j * math.pi * f0 * t / fs) for t in range(n)]
        freqs, powers = power_spectrum(iq, fs, nfft=n)
        peaks = detect_peaks(powers, freqs, threshold_db_over_noise=6.0)
        self.assertTrue(peaks)
        self.assertLess(abs(peaks[0][0] - f0), fs / n)


class BeamformingDoaTest(unittest.TestCase):
    freq = 2.4e9

    def test_ula_recovers_azimuth(self):
        lam = SPEED_OF_LIGHT / self.freq
        positions = ula_positions(4, spacing_m=lam / 2)
        true_az = 60.0
        r = covariance(_snapshots(positions, true_az, self.freq))
        # ULA is ambiguous about the x-axis; scan one hemisphere (0..180).
        peak = bartlett_doa(r, positions, self.freq, range(0, 181), n_peaks=1)[0]
        self.assertLess(abs(peak[0] - true_az), 3.0)

    def test_uca_recovers_full_azimuth(self):
        lam = SPEED_OF_LIGHT / self.freq
        positions = uca_positions(5, radius_m=0.3 * lam)
        true_az = 200.0
        r = covariance(_snapshots(positions, true_az, self.freq))
        peak = bartlett_doa(r, positions, self.freq, range(0, 360), n_peaks=1)[0]
        self.assertLess(abs(peak[0] - true_az), 5.0)


if __name__ == "__main__":
    unittest.main()
