"""Tests for Heimdall frame parsing and KrakenSDR DoA (no hardware)."""
import random
import unittest

from wavefrom_pod import heimdall
from wavefrom_pod.backends import KrakenSdrBackend
from wavefrom_pod.dsp import SPEED_OF_LIGHT, steering_vector, uca_positions


class HeimdallFrameTest(unittest.TestCase):
    def test_build_parse_decode_roundtrip(self):
        channels = [[complex(c + 0.1 * t, -c + 0.2 * t) for t in range(4)] for c in range(5)]
        frame = heimdall.build_frame(channels, rf_center_freq=433_000_000, sampling_freq=2_400_000)

        header = heimdall.parse_header(frame[: heimdall.HEADER_SIZE])
        self.assertEqual(header["sync_word"], heimdall.SYNC_WORD)
        self.assertEqual(header["active_ant_chs"], 5)
        self.assertEqual(header["cpi_length"], 4)
        self.assertEqual(header["rf_center_freq"], 433_000_000)

        payload = frame[heimdall.HEADER_SIZE : heimdall.HEADER_SIZE + heimdall.payload_size(header)]
        decoded = heimdall.decode_channels(header, payload)
        for c in range(5):
            for t in range(4):
                self.assertAlmostEqual(decoded[c][t].real, channels[c][t].real, places=4)
                self.assertAlmostEqual(decoded[c][t].imag, channels[c][t].imag, places=4)


class KrakenDoaTest(unittest.TestCase):
    def test_recovers_azimuth_from_coherent_channels(self):
        freq, radius, true_az = 433e6, 0.17, 137.0
        positions = uca_positions(5, radius)
        a = steering_vector(positions, true_az, freq)
        rng = random.Random(1)
        channels = [[] for _ in range(5)]
        for _ in range(128):
            s = complex(rng.gauss(0, 1), rng.gauss(0, 1))
            for c in range(5):
                channels[c].append(a[c] * s + 0.05 * complex(rng.gauss(0, 1), rng.gauss(0, 1)))
        peaks = KrakenSdrBackend.doa_from_channels(channels, radius, freq, n_peaks=1)
        self.assertLess(abs(peaks[0][0] - true_az), 6.0)


if __name__ == "__main__":
    unittest.main()
