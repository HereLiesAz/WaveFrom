"""Tests for the pod DoA math (no hardware required)."""
import cmath
import math
import unittest

from wavefrom_pod.backends import RtlSdrBackend
from wavefrom_pod.dsp import SPEED_OF_LIGHT, doa_from_phase, interferometric_phase


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


if __name__ == "__main__":
    unittest.main()
