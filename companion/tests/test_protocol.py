"""Tests for the pod wire protocol and simulator backend."""
import json
import unittest

from wavefrom_pod.backends import SimulatorBackend, decimate_iq
from wavefrom_pod.protocol import Bearing, Spectrum, Waveform, heartbeat


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

    def test_spectrum_json_shape(self):
        s = Spectrum(start_hz=int(2.4e9), bin_hz=int(1e6), powers_dbm=[-100.0, -50.0, -90.0])
        d = json.loads(s.to_json(ts_ms=1))
        self.assertEqual(d["type"], "spectrum")
        self.assertEqual(d["startHz"], int(2.4e9))
        self.assertEqual(len(d["powersDbm"]), 3)

    def test_waveform_json_matches_android_contract(self):
        w = Waveform("krk-0", int(5.8e9), i=[0.0, 1.0, -0.5], q=[1.0, 0.0, 0.5])
        d = json.loads(w.to_json(ts_ms=1719100000000))
        self.assertEqual(d["type"], "waveform")
        self.assertEqual(d["trackId"], "krk-0")
        self.assertEqual(d["freqHz"], 5800000000)
        self.assertEqual(d["i"], [0.0, 1.0, -0.5])
        self.assertEqual(d["q"], [1.0, 0.0, 0.5])
        self.assertEqual(d["ts"], 1719100000000)

    def test_decimate_iq_subsamples_complex_stream(self):
        iq = [complex(n, -n) for n in range(1024)]
        w = decimate_iq(iq, "rtl", 100e6, n=128)
        self.assertEqual(w.track_id, "rtl")
        self.assertEqual(w.freq_hz, 100_000_000)
        self.assertEqual(len(w.i), 128)
        self.assertEqual(len(w.q), 128)
        # First sample preserved; real/imag split correctly.
        self.assertEqual(w.i[0], 0.0)
        self.assertEqual(w.q[0], 0.0)
        self.assertEqual(w.i[1], 8.0)  # stride = 1024 // 128 = 8
        self.assertEqual(w.q[1], -8.0)

    def test_simulator_produces_spectrum(self):
        s = SimulatorBackend().spectrum()
        self.assertEqual(len(s.powers_dbm), 128)
        # The two synthetic peaks rise above the noise floor.
        self.assertTrue(max(s.powers_dbm) > -80.0)

    def test_simulator_emits_requested_count(self):
        sim = SimulatorBackend(count=3)
        first = sim.poll()
        second = sim.poll()
        self.assertEqual(len(first), 3)
        # Emitters orbit, so azimuth advances between polls.
        self.assertNotEqual(first[0].azimuth_deg, second[0].azimuth_deg)


if __name__ == "__main__":
    unittest.main()
