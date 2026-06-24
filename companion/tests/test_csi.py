"""Tests for Nexmon CSI parsing and Wi-Fi CSI DoA (no hardware)."""
import random
import unittest

from wavefrom_pod import nexmon
from wavefrom_pod.backends import WifiCsiBackend
from wavefrom_pod.dsp import steering_vector, ula_positions


class NexmonPacketTest(unittest.TestCase):
    def test_build_parse_roundtrip(self):
        csi = [complex(10 * i, -5 * i) for i in range(8)]
        pkt = nexmon.build_packet(
            rssi=-42, src_mac=bytes.fromhex("001122334455"), core=2, seq=777,
            chanspec=0x1000, csi=csi,
        )
        parsed = nexmon.parse_packet(pkt)
        self.assertEqual(parsed["magic"], nexmon.MAGIC)
        self.assertEqual(parsed["rssi"], -42)
        self.assertEqual(parsed["core"], 2)
        self.assertEqual(parsed["seq"], 777)
        self.assertEqual(parsed["csi"], csi)


class CsiDoaTest(unittest.TestCase):
    def test_recovers_azimuth(self):
        freq, spacing, true_az = 2.437e9, 0.03, 70.0
        positions = ula_positions(3, spacing)
        a = steering_vector(positions, true_az, freq)
        rng = random.Random(2)
        matrix = [[] for _ in range(3)]
        for _ in range(48):  # subcarriers
            sig = complex(rng.gauss(0, 1), rng.gauss(0, 1))
            for ant in range(3):
                matrix[ant].append(a[ant] * sig + 0.05 * complex(rng.gauss(0, 1), rng.gauss(0, 1)))
        peaks = WifiCsiBackend.doa_from_csi(matrix, spacing, freq, n_peaks=1)
        self.assertLess(abs(peaks[0][0] - true_az), 6.0)


if __name__ == "__main__":
    unittest.main()
