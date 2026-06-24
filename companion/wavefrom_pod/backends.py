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

from .protocol import Bearing, Spectrum


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

    def spectrum(self) -> Spectrum | None:
        """Optional power-per-bin snapshot for the waterfall. None if unsupported."""
        return None


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

    def spectrum(self) -> Spectrum:
        """Synthetic 128-bin spectrum: noise floor plus two drifting peaks."""
        bins = 128
        start_hz = int(2.4e9)
        bin_hz = int(1e6)
        peaks = ((30 + (self._t % 40), 45.0), (90 - (self._t % 30), 35.0))
        powers = []
        for i in range(bins):
            p = -108.0 + 4.0 * math.sin(i * 0.35 + self._t * 0.1)
            for center, amp in peaks:
                p = max(p, -108.0 + amp * math.exp(-((i - center) ** 2) / 8.0))
            powers.append(p)
        return Spectrum(start_hz, bin_hz, powers)


class RtlSdrBackend(SensorBackend):
    """Single RTL-SDR dongle: real IQ capture → power spectrum for the waterfall.

    One antenna can't resolve direction, so this emits no bearings — just a live
    [Spectrum]. The phase-DF math ([bearing_from_iq]) is kept for the optional
    two-coherent-dongle case and is unit-tested without hardware.
    """

    name = "rtlsdr"
    antenna_count = 1

    def __init__(
        self,
        center_freq: float = 433_000_000.0,
        sample_rate: float = 2_400_000.0,
        gain: object = "auto",
        nfft: int = 1024,
        **_: object,
    ) -> None:
        self.center_freq = float(center_freq)
        self.sample_rate = float(sample_rate)
        self.gain = gain
        self.nfft = int(nfft)
        self._sdr = None
        self._last_spectrum: Spectrum | None = None

    @staticmethod
    def bearing_from_iq(iq_a, iq_b, freq_hz: float, spacing_m: float) -> tuple[float, float]:
        """(theta, mirror) bearing from two coherent IQ streams. No hardware needed."""
        from .dsp import doa_from_phase, interferometric_phase

        phase = interferometric_phase(iq_a, iq_b)
        return doa_from_phase(phase, spacing_m, freq_hz)

    def start(self) -> None:
        try:
            from rtlsdr import RtlSdr
        except ImportError as e:
            raise NotImplementedError("RtlSdrBackend needs pyrtlsdr (pip install pyrtlsdr).") from e
        self._sdr = RtlSdr()
        self._sdr.sample_rate = self.sample_rate
        self._sdr.center_freq = self.center_freq
        self._sdr.gain = self.gain

    def stop(self) -> None:
        if self._sdr is not None:
            self._sdr.close()
            self._sdr = None

    def poll(self) -> list[Bearing]:
        # Capture one block per cycle and cache the spectrum; no bearings.
        if self._sdr is None:
            return []
        from .dsp import detect_peaks, power_spectrum

        iq = self._sdr.read_samples(self.nfft)
        freqs, powers = power_spectrum(iq, self.sample_rate, self.center_freq, self.nfft)
        self._last_spectrum = Spectrum(
            start_hz=int(self.center_freq - self.sample_rate / 2),
            bin_hz=int(self.sample_rate / self.nfft),
            powers_dbm=powers,
        )
        # Peaks are logged for now; single-antenna can't bearing them.
        detect_peaks(powers, freqs)
        return []

    def spectrum(self) -> Spectrum | None:
        return self._last_spectrum


def _covariance(channels: list[list[complex]], max_snapshots: int = 512):
    """Spatial covariance across channels. numpy fast path; pure-Python (sub-
    sampled) fallback so it works without numpy."""
    if not channels or not channels[0]:
        return []
    try:
        import numpy as np

        x = np.asarray(channels, dtype=np.complex128)  # (n_ch, T)
        r = (x @ x.conj().T) / x.shape[1]
        return r.tolist()
    except ImportError:
        from .dsp import covariance

        n_ch = len(channels)
        t = len(channels[0]) if channels else 0
        step = max(1, t // max_snapshots)
        snapshots = [[channels[c][i] for c in range(n_ch)] for i in range(0, t, step)]
        return covariance(snapshots)


class KrakenSdrBackend(SensorBackend):
    """Coherent 5-channel DoA from a KrakenSDR via the Heimdall DAQ server.

    Heimdall handles clock/phase calibration and serves coherent IQ over TCP; we
    read frames ([heimdall]), build the spatial covariance, and run beamforming
    DoA over the 5-element circular array → true bearings. The DoA itself
    ([doa_from_channels]) is unit tested with synthetic frames; only the socket
    needs hardware.
    """

    name = "krakensdr"

    def __init__(
        self,
        heimdall_host: str = "127.0.0.1",
        heimdall_port: int = 5000,
        radius_m: float = 0.17,
        n_sources: int = 1,
        **_: object,
    ) -> None:
        self.host = heimdall_host
        self.port = heimdall_port
        self.radius_m = radius_m
        self.n_sources = n_sources
        self.antenna_count = 5
        self._sock = None
        self._last_spectrum: Spectrum | None = None

    @staticmethod
    def doa_from_channels(
        channels: list[list[complex]], radius_m: float, freq_hz: float, n_peaks: int = 1
    ) -> list[tuple[float, float]]:
        """(azimuth_deg, power_db) peaks for coherent channels on a UCA."""
        from .dsp import bartlett_doa, music_doa, uca_positions

        positions = uca_positions(len(channels), radius_m)
        r = _covariance(channels)
        try:
            return music_doa(r, positions, freq_hz, range(0, 360), n_sources=n_peaks, n_peaks=n_peaks)
        except NotImplementedError:
            return bartlett_doa(r, positions, freq_hz, range(0, 360), n_peaks=n_peaks)

    def start(self) -> None:
        import socket

        self._sock = socket.create_connection((self.host, self.port), timeout=5)

    def stop(self) -> None:
        if self._sock is not None:
            self._sock.close()
            self._sock = None

    def _recv_exact(self, n: int) -> bytes:
        buf = b""
        while len(buf) < n:
            chunk = self._sock.recv(n - len(buf))
            if not chunk:
                raise ConnectionError("Heimdall stream closed")
            buf += chunk
        return buf

    def poll(self) -> list[Bearing]:
        from . import heimdall
        from .dsp import power_spectrum

        if self._sock is None:
            return []
        header = heimdall.parse_header(self._recv_exact(heimdall.HEADER_SIZE))
        if header["sync_word"] != heimdall.SYNC_WORD:
            # Stream is out of sync; fail fast so the service can reconnect clean.
            raise ConnectionError("Heimdall stream out of sync (invalid sync word)")
        channels = heimdall.decode_channels(header, self._recv_exact(heimdall.payload_size(header)))
        freq = float(header["rf_center_freq"])
        fs = float(header["sampling_freq"]) or 2_400_000.0

        peaks = self.doa_from_channels(channels, self.radius_m, freq, self.n_sources)
        bearings = [
            Bearing(
                track_id=f"krk-{i}",
                freq_hz=int(freq),
                power_dbm=power,
                azimuth_deg=az,
                elevation_deg=None,
                confidence=0.8,
                label="KrakenSDR DoA",
            )
            for i, (az, power) in enumerate(peaks)
        ]
        nfft = min(len(channels[0]), 1024)
        _, powers = power_spectrum(channels[0], fs, freq, nfft)
        self._last_spectrum = Spectrum(
            start_hz=int(freq - fs / 2), bin_hz=int(fs / nfft), powers_dbm=powers
        )
        return bearings

    def spectrum(self) -> Spectrum | None:
        return self._last_spectrum


class WifiCsiBackend(SensorBackend):
    """Wi-Fi direction finding from Nexmon CSI.

    Consumes the Nexmon CSI UDP stream ([nexmon]); one packet arrives per RX core
    (antenna) per frame. Packets are grouped by sequence number into a per-frame
    antenna×subcarrier matrix; treating each subcarrier as a snapshot across
    antennas gives a covariance → beamforming DoA over the NIC's antenna array.
    The DoA core ([doa_from_csi]) is unit tested; only the UDP socket needs the
    NIC. Antenna geometry is uncalibrated on consumer NICs — [spacing_m] is a
    best-effort assumption (see module note in nexmon.py).
    """

    name = "wificsi"

    def __init__(
        self,
        udp_port: int = 5500,
        n_antennas: int = 3,
        spacing_m: float = 0.03,
        freq_hz: float = 2_437_000_000.0,
        **_: object,
    ) -> None:
        self.udp_port = udp_port
        self.n_antennas = n_antennas
        self.spacing_m = spacing_m
        self.freq_hz = freq_hz
        self.antenna_count = n_antennas
        self._sock = None
        self._groups: dict[int, dict] = {}

    @staticmethod
    def doa_from_csi(
        csi_matrix: list[list[complex]], spacing_m: float, freq_hz: float, n_peaks: int = 1
    ) -> list[tuple[float, float]]:
        """(azimuth_deg, power_db) from an antenna×subcarrier CSI matrix (ULA)."""
        from .dsp import bartlett_doa, covariance, ula_positions

        n_ant = len(csi_matrix)
        n_sub = len(csi_matrix[0])
        positions = ula_positions(n_ant, spacing_m)
        snapshots = [[csi_matrix[a][s] for a in range(n_ant)] for s in range(n_sub)]
        r = covariance(snapshots)
        # ULA resolves one hemisphere; scan 0..180.
        return bartlett_doa(r, positions, freq_hz, range(0, 181), n_peaks)

    def start(self) -> None:
        import socket

        self._sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self._sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._sock.bind(("", self.udp_port))
        self._sock.settimeout(0.3)

    def stop(self) -> None:
        if self._sock is not None:
            self._sock.close()
            self._sock = None

    def poll(self) -> list[Bearing]:
        import socket

        from .nexmon import MAGIC, parse_packet

        if self._sock is None:
            return []
        for _ in range(128):  # drain what's queued this cycle
            try:
                data, _addr = self._sock.recvfrom(4096)
            except (socket.timeout, OSError):
                break
            try:
                pkt = parse_packet(data)
            except ValueError:
                continue
            if pkt["magic"] != MAGIC:
                continue
            self._groups.setdefault(pkt["seq"], {})[pkt["core"]] = pkt

        bearings: list[Bearing] = []
        for seq in [s for s, g in self._groups.items() if len(g) >= self.n_antennas]:
            group = self._groups.pop(seq)
            cores = sorted(group)[: self.n_antennas]
            matrix = [group[c]["csi"] for c in cores]
            width = min(len(x) for x in matrix)
            matrix = [x[:width] for x in matrix]
            if width == 0:
                continue
            peaks = self.doa_from_csi(matrix, self.spacing_m, self.freq_hz, n_peaks=1)
            if peaks:
                az, power = peaks[0]
                mac = group[cores[0]]["src_mac"].hex()
                bearings.append(
                    Bearing(
                        track_id=f"csi-{mac}",
                        freq_hz=int(self.freq_hz),
                        power_dbm=float(power),
                        azimuth_deg=az,
                        elevation_deg=None,
                        confidence=0.5,
                        label="WiFi CSI DoA",
                    )
                )
        # Bound memory: drop the oldest incomplete groups. Use insertion order
        # (dicts preserve it) — sorting by seq breaks on 16-bit wrap-around.
        if len(self._groups) > 256:
            for seq in list(self._groups)[:128]:
                self._groups.pop(seq, None)
        return bearings


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
    KrakenSdrBackend.name: KrakenSdrBackend,
    WifiCsiBackend.name: WifiCsiBackend,
    BleBackend.name: BleBackend,
}
