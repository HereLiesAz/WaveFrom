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


def heartbeat(pod_id: str, antenna_count: int, ts_ms: int | None = None) -> str:
    return json.dumps(
        {
            "type": "heartbeat",
            "podId": pod_id,
            "antennaCount": antenna_count,
            "ts": ts_ms if ts_ms is not None else _now_ms(),
        }
    )
