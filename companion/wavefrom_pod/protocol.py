"""WaveFrom SDR wire protocol (producer side).

Newline-delimited JSON, matching the Android decoder in
``app/.../signal/source/sdr/WireProtocol.kt``. The Pi pod and any vendor
adapter (QuadRF / KrakenSDR) emit exactly these shapes so the phone can treat
every external sensor identically.
"""
from __future__ import annotations

import json
import time
from dataclasses import dataclass


def _now_ms() -> int:
    return int(time.time() * 1000)


@dataclass
class Bearing:
    """A located emitter with a true direction-of-arrival."""

    track_id: str
    freq_hz: int
    power_dbm: float
    azimuth_deg: float
    elevation_deg: float | None = None
    confidence: float = 0.8
    label: str | None = None

    def to_json(self, ts_ms: int | None = None) -> str:
        return json.dumps(
            {
                "type": "bearing",
                "trackId": self.track_id,
                "freqHz": self.freq_hz,
                "powerDbm": round(self.power_dbm, 1),
                "azimuthDeg": round(self.azimuth_deg, 2),
                "elevationDeg": (
                    None if self.elevation_deg is None else round(self.elevation_deg, 2)
                ),
                "confidence": round(self.confidence, 3),
                "label": self.label,
                "ts": ts_ms if ts_ms is not None else _now_ms(),
            }
        )


@dataclass
class Spectrum:
    """A power-per-bin snapshot for the phone's waterfall view."""

    start_hz: int
    bin_hz: int
    powers_dbm: list[float]

    def to_json(self, ts_ms: int | None = None) -> str:
        return json.dumps(
            {
                "type": "spectrum",
                "startHz": self.start_hz,
                "binHz": self.bin_hz,
                "powersDbm": [round(p, 1) for p in self.powers_dbm],
                "ts": ts_ms if ts_ms is not None else _now_ms(),
            }
        )


@dataclass
class Waveform:
    """A window of real captured IQ samples for the phone's 3D IQ-helix viewer.

    ``track_id`` matches the corresponding :class:`Bearing` when the SDR resolves
    direction, so the phone attaches the helix to that emitter; single-antenna
    sources use a standalone id. Decimate to ~128 samples before sending — this is a
    visual, not a recording, and it shares the link with everything else.
    """

    track_id: str
    freq_hz: int
    i: list[float]
    q: list[float]

    def to_json(self, ts_ms: int | None = None) -> str:
        return json.dumps(
            {
                "type": "waveform",
                "trackId": self.track_id,
                "freqHz": self.freq_hz,
                "i": [round(v, 4) for v in self.i],
                "q": [round(v, 4) for v in self.q],
                "ts": ts_ms if ts_ms is not None else _now_ms(),
            }
        )


@dataclass
class Occupancy:
    """A room-level presence/vitals reading for one sensing zone.

    Unlike :class:`Bearing`, this describes a *zone* (whatever area the source
    NIC/array covers), not a located emitter — no frequency, power, or
    direction. Every rate is paired with its own confidence and is ``None``
    rather than a fabricated number when there isn't enough signal to trust
    one; ``synthetic`` marks readings from a simulator so they can never be
    mistaken for a live measurement. See ADR/README notes on the CSI vitals
    pipeline before surfacing these numbers as anything but a labeled
    experiment — this is not a medical device.
    """

    zone_id: str
    present: bool
    presence_confidence: float
    breathing_bpm: float | None = None
    breathing_confidence: float = 0.0
    heart_bpm: float | None = None
    heart_confidence: float = 0.0
    synthetic: bool = False

    def to_json(self, ts_ms: int | None = None) -> str:
        return json.dumps(
            {
                "type": "occupancy",
                "zoneId": self.zone_id,
                "present": self.present,
                "presenceConfidence": round(self.presence_confidence, 3),
                "breathingBpm": (
                    None if self.breathing_bpm is None else round(self.breathing_bpm, 1)
                ),
                "breathingConfidence": round(self.breathing_confidence, 3),
                "heartBpm": None if self.heart_bpm is None else round(self.heart_bpm, 1),
                "heartConfidence": round(self.heart_confidence, 3),
                "synthetic": self.synthetic,
                "ts": ts_ms if ts_ms is not None else _now_ms(),
            }
        )


def heartbeat(pod_id: str, antenna_count: int, ts_ms: int | None = None) -> str:
    return json.dumps(
        {
            "type": "heartbeat",
            "podId": pod_id,
            "antennaCount": antenna_count,
            "ts": ts_ms if ts_ms is not None else _now_ms(),
        }
    )
