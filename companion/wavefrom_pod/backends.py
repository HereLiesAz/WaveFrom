"""Pluggable sensor backends for the WaveFrom Raspberry Pi pod.

A backend turns whatever RF hardware is attached to the Pi into a list of
:class:`~wavefrom_pod.protocol.Bearing` objects each poll. The Pi has full Linux
drivers, so this is where dongles that Android can't talk to (RTL-SDR arrays,
monitor-mode Wi-Fi NICs with Nexmon CSI, BlueZ) are actually used — including
true multi-antenna phase processing for real direction-of-arrival.

Only :class:`SimulatorBackend` is implemented; the hardware backends are
documented stubs that describe what each would do.
"""
from __future__ import annotations

import math
from abc import ABC, abstractmethod

from .protocol import Bearing


class SensorBackend(ABC):
    """Base class for all pod backends."""

    name: str = "base"
    #: Number of coherent antennas this backend processes (drives DF quality).
    antenna_count: int = 1

    def start(self) -> None:
        """Acquire hardware / start streaming. Override as needed."""

    def stop(self) -> None:
        """Release hardware. Override as needed."""

    @abstractmethod
    def poll(self) -> list[Bearing]:
        """Return the currently detected emitters as bearings."""


class SimulatorBackend(SensorBackend):
    """Emits synthetic emitters orbiting the pod — no RF hardware required.

    Lets you exercise the whole phone pipeline (NetworkSdrSource → AR overlay)
    end-to-end from a laptop or Pi with nothing attached.
    """

    name = "simulator"
    antenna_count = 4

    def __init__(self, count: int = 3) -> None:
        self._count = count
        self._t = 0

    def poll(self) -> list[Bearing]:
        out: list[Bearing] = []
        for i in range(self._count):
            azimuth = (self._t * 1.5 + i * (360.0 / self._count)) % 360.0
            elevation = 8.0 * math.sin(math.radians(self._t * 2 + i * 40))
            power = -55.0 + 5.0 * math.sin(math.radians(self._t * 3))
            out.append(
                Bearing(
                    track_id=f"sim-{i}",
                    freq_hz=int(5.8e9),
                    power_dbm=power,
                    azimuth_deg=azimuth,
                    elevation_deg=elevation,
                    confidence=0.85,
                    label=f"Sim emitter {i}",
                )
            )
        self._t += 1
        return out


class RtlSdrBackend(SensorBackend):
    """A coherent RTL-SDR pair/array (e.g. KrakenSDR) doing correlation DF.

    With ``pyrtlsdr`` + numpy this reads IQ from coherent receivers, computes the
    inter-element phase via cross-correlation and converts it to a bearing. The
    IQ→bearing math ([bearing_from_iq]) is testable without hardware; [poll]
    needs the radios and raises until they're wired up.
    """

    name = "rtlsdr"
    antenna_count = 2

    def __init__(self, spacing_m: float = 0.5, freq_hz: float = 433_000_000.0) -> None:
        self.spacing_m = spacing_m
        self.freq_hz = freq_hz

    @staticmethod
    def bearing_from_iq(iq_a, iq_b, freq_hz: float, spacing_m: float) -> tuple[float, float]:
        """(theta, mirror) bearing from two coherent IQ streams. No hardware needed."""
        from .dsp import doa_from_phase, interferometric_phase

        phase = interferometric_phase(iq_a, iq_b)
        return doa_from_phase(phase, spacing_m, freq_hz)

    def poll(self) -> list[Bearing]:  # pragma: no cover - hardware stub
        try:
            import rtlsdr  # noqa: F401  (pyrtlsdr) - optional hardware dep
        except ImportError as e:
            raise NotImplementedError(
                "RtlSdrBackend needs pyrtlsdr + a coherent RTL-SDR array."
            ) from e
        # TODO: read coherent IQ buffers, detect signals per bin, and for each
        # call bearing_from_iq(...) to emit a Bearing.
        raise NotImplementedError("Coherent RTL-SDR capture loop not yet implemented.")


class WifiCsiBackend(SensorBackend):
    """Stub: Wi-Fi direction finding from Channel State Information.

    Would use a Nexmon-CSI capable NIC to extract per-subcarrier CSI and apply
    MUSIC over the antenna array (or synthetic aperture across motion) to bearing
    Wi-Fi emitters. This is the case Android cannot do without special firmware —
    the Pi can.
    """

    name = "wificsi"

    def poll(self) -> list[Bearing]:  # pragma: no cover - hardware stub
        raise NotImplementedError(
            "WifiCsiBackend requires a Nexmon-CSI capable NIC; not yet implemented."
        )


class BleBackend(SensorBackend):
    """Stub: BLE angle-of-arrival via a Bluetooth 5.1 AoA-capable radio + BlueZ."""

    name = "ble"

    def poll(self) -> list[Bearing]:  # pragma: no cover - hardware stub
        raise NotImplementedError(
            "BleBackend requires Bluetooth 5.1 AoA hardware; not yet implemented."
        )


#: Registry used by the CLI to select a backend by name.
BACKENDS: dict[str, type[SensorBackend]] = {
    SimulatorBackend.name: SimulatorBackend,
    RtlSdrBackend.name: RtlSdrBackend,
    WifiCsiBackend.name: WifiCsiBackend,
    BleBackend.name: BleBackend,
}
