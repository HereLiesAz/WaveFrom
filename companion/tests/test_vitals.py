"""Tests for CSI presence/vitals extraction (no hardware required)."""
import math
import unittest

from wavefrom_pod.vitals import (
    VitalsExtractor,
    _circular_variance,
    _mean_phasor,
    _resample_uniform,
)


def _phasor_csi(phase: float, n_antennas: int = 2, n_subcarriers: int = 8) -> list[list[complex]]:
    """A synthetic antenna x subcarrier CSI frame whose mean phasor is exp(i*phase)."""
    c = complex(math.cos(phase), math.sin(phase))
    return [[c for _ in range(n_subcarriers)] for _ in range(n_antennas)]


def _feed_oscillation(
    extractor: VitalsExtractor,
    freq_hz: float,
    amplitude_rad: float,
    duration_s: float,
    sample_hz: float = 10.0,
    t0: float = 0.0,
    center_rad: float = 0.0,
) -> float:
    """Push a sinusoidal phase signal and return the end timestamp."""
    n = int(duration_s * sample_hz)
    ts = t0
    for i in range(n):
        ts = t0 + i / sample_hz
        phase = center_rad + amplitude_rad * math.sin(2.0 * math.pi * freq_hz * ts)
        extractor.push(_phasor_csi(phase), ts)
    return ts


class HelpersTest(unittest.TestCase):
    def test_mean_phasor_ignores_amplitude(self):
        matrix = [[3.0 * complex(math.cos(0.4), math.sin(0.4))], [0.1 * complex(math.cos(0.4), math.sin(0.4))]]
        p = _mean_phasor(matrix)
        self.assertAlmostEqual(math.atan2(p.imag, p.real), 0.4, places=6)
        self.assertAlmostEqual(abs(p), 1.0, places=6)

    def test_mean_phasor_empty_is_zero(self):
        self.assertEqual(_mean_phasor([[]]), 0j)

    def test_circular_variance_constant_phase_is_zero(self):
        self.assertAlmostEqual(_circular_variance([0.5] * 20), 0.0, places=6)

    def test_circular_variance_uniform_phase_is_near_one(self):
        phases = [2.0 * math.pi * i / 16 for i in range(16)]
        self.assertGreater(_circular_variance(phases), 0.95)

    def test_resample_uniform_linear_interp(self):
        times = [0.0, 1.0, 2.0]
        values = [0.0, 10.0, 0.0]
        grid, out = _resample_uniform(times, values, hz=2.0)
        self.assertAlmostEqual(grid[1], 0.5, places=6)
        self.assertAlmostEqual(out[1], 5.0, places=6)  # midpoint of the rising edge


class BreathingRateTest(unittest.TestCase):
    def test_recovers_breathing_rate(self):
        # 0.2 Hz = 12 BPM, comfortably inside the 0.1-0.5 Hz breathing band.
        extractor = VitalsExtractor(window_s=30.0, calibration_s=1000.0)
        _feed_oscillation(extractor, freq_hz=0.2, amplitude_rad=0.4, duration_s=30.0)
        estimate = extractor.estimate()
        self.assertIsNotNone(estimate.breathing_bpm)
        self.assertAlmostEqual(estimate.breathing_bpm, 12.0, delta=2.0)
        self.assertGreater(estimate.breathing_confidence, 0.3)
        # A pure 0.2 Hz tone has no energy in the heart band.
        self.assertIsNone(estimate.heart_bpm)

    def test_too_short_a_window_yields_no_rate(self):
        extractor = VitalsExtractor(window_s=30.0, calibration_s=1000.0)
        _feed_oscillation(extractor, freq_hz=0.2, amplitude_rad=0.4, duration_s=3.0)
        estimate = extractor.estimate()
        self.assertIsNone(estimate.breathing_bpm)
        self.assertEqual(estimate.breathing_confidence, 0.0)


class HeartRateTest(unittest.TestCase):
    def test_recovers_heart_rate(self):
        # 1.2 Hz = 72 BPM, inside the 0.8-2.0 Hz heart band.
        extractor = VitalsExtractor(window_s=15.0, calibration_s=1000.0)
        _feed_oscillation(extractor, freq_hz=1.2, amplitude_rad=0.15, duration_s=15.0)
        estimate = extractor.estimate()
        self.assertIsNotNone(estimate.heart_bpm)
        self.assertAlmostEqual(estimate.heart_bpm, 72.0, delta=6.0)
        self.assertGreater(estimate.heart_confidence, 0.2)


class PresenceTest(unittest.TestCase):
    def test_static_room_is_not_present_after_calibration(self):
        extractor = VitalsExtractor(window_s=30.0, calibration_s=5.0)
        # Perfectly still phase for the whole run — an empty room.
        t = _feed_oscillation(extractor, freq_hz=0.0, amplitude_rad=0.0, duration_s=10.0)
        estimate = extractor.estimate()
        self.assertFalse(estimate.present)

    def test_motion_after_calibration_is_present(self):
        extractor = VitalsExtractor(window_s=30.0, calibration_s=5.0)
        # Calibrate against a still room, then introduce breathing-like motion.
        t0 = _feed_oscillation(extractor, freq_hz=0.0, amplitude_rad=0.0, duration_s=6.0)
        _feed_oscillation(
            extractor, freq_hz=0.25, amplitude_rad=1.2, duration_s=8.0, t0=t0 + 0.1
        )
        estimate = extractor.estimate()
        self.assertTrue(estimate.present)
        self.assertGreater(estimate.presence_confidence, 0.0)

    def test_no_estimate_before_enough_samples(self):
        extractor = VitalsExtractor()
        extractor.push(_phasor_csi(0.1), ts=0.0)
        estimate = extractor.estimate()
        self.assertFalse(estimate.present)
        self.assertIsNone(estimate.breathing_bpm)
        self.assertIsNone(estimate.heart_bpm)


if __name__ == "__main__":
    unittest.main()
