"""Tests for the pod wire protocol and simulator backend."""
import json
import unittest

from wavefrom_pod.backends import SimulatorBackend
from wavefrom_pod.protocol import Bearing, heartbeat


class ProtocolTest(unittest.TestCase):
    def test_bearing_json_matches_android_contract(self):
        b = Bearing("krk-7", int(5.8e9), -61.2, 134.5, 3.0, 0.82, "5.8GHz FPV")
        d = json.loads(b.to_json(ts_ms=1719100000000))
        self.assertEqual(d["type"], "bearing")
        self.assertEqual(d["trackId"], "krk-7")
        self.assertEqual(d["freqHz"], 5800000000)
        self.assertEqual(d["azimuthDeg"], 134.5)
        self.assertEqual(d["elevationDeg"], 3.0)
        self.assertEqual(d["ts"], 1719100000000)

    def test_elevation_may_be_null(self):
        d = json.loads(Bearing("x", 1, -50, 10).to_json())
        self.assertIsNone(d["elevationDeg"])

    def test_heartbeat(self):
        d = json.loads(heartbeat("pod", 4, ts_ms=1))
        self.assertEqual(d["type"], "heartbeat")
        self.assertEqual(d["antennaCount"], 4)

    def test_simulator_emits_requested_count(self):
        sim = SimulatorBackend(count=3)
        first = sim.poll()
        second = sim.poll()
        self.assertEqual(len(first), 3)
        # Emitters orbit, so azimuth advances between polls.
        self.assertNotEqual(first[0].azimuth_deg, second[0].azimuth_deg)


if __name__ == "__main__":
    unittest.main()
