"""Pod run loop: poll a backend and stream bearings + heartbeats to the phone."""
from __future__ import annotations

import time

from .backends import SensorBackend
from .protocol import heartbeat
from .transport import UdpTransport


def run(
    backend: SensorBackend,
    transport: UdpTransport,
    pod_id: str,
    rate_hz: float = 10.0,
    heartbeat_secs: float = 2.0,
) -> None:
    """Stream until interrupted. Sends one batch of bearings per tick."""
    interval = 1.0 / rate_hz
    last_hb = 0.0
    backend.start()
    try:
        while True:
            start = time.time()
            try:
                for bearing in backend.poll():
                    transport.send_line(bearing.to_json())
                spectrum = backend.spectrum()
                if spectrum is not None:
                    transport.send_line(spectrum.to_json())
                if start - last_hb >= heartbeat_secs:
                    transport.send_line(heartbeat(pod_id, backend.antenna_count))
                    last_hb = start
            except OSError as e:
                # Transient network error (e.g. Wi-Fi reconnect) — fail soft.
                print(f"send error: {e}")
            # Subtract work time so the loop holds rate_hz instead of drifting slower.
            time.sleep(max(0.0, interval - (time.time() - start)))
    finally:
        backend.stop()
