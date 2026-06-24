"""Heimdall DAQ IQ-frame parsing for the KrakenSDR backend.

Heimdall (krakenrf/heimdall_daq_fw) streams phase-calibrated coherent IQ as
fixed 1024-byte-header frames followed by a channel-major IQ payload. This module
parses the handful of header fields the DoA needs and decodes the payload into
per-channel complex sample lists.

IMPORTANT: the exact header field offsets vary by heimdall_daq_fw version. The
layout below follows the standard IQ header; validate it against the
`iq_header.py` of the installed firmware before relying on hardware output. The
DoA pipeline that consumes [decode_channels] is version-independent and unit
tested via [build_frame].
"""
from __future__ import annotations

import struct

HEADER_SIZE = 1024
SYNC_WORD = 0x2BF7B95A

# (name, offset, struct-format) for the fields we read. Little-endian.
_FIELDS = {
    "sync_word": (0, "<I"),
    "frame_type": (4, "<I"),
    "active_ant_chs": (28, "<I"),
    "rf_center_freq": (36, "<Q"),
    "adc_sampling_freq": (44, "<Q"),
    "sampling_freq": (52, "<Q"),
    "cpi_length": (60, "<I"),
    "data_type": (88, "<I"),
    "sample_bit_depth": (92, "<I"),
}


def parse_header(buf: bytes) -> dict:
    """Parse the 1024-byte IQ header into a dict of the fields we use."""
    if len(buf) < HEADER_SIZE:
        raise ValueError(f"short header: {len(buf)} < {HEADER_SIZE}")
    out = {}
    for name, (off, fmt) in _FIELDS.items():
        (out[name],) = struct.unpack_from(fmt, buf, off)
    return out


def payload_size(header: dict) -> int:
    """Bytes of IQ payload following the header."""
    bytes_per_component = 1 if header["sample_bit_depth"] == 8 else 4
    return header["active_ant_chs"] * header["cpi_length"] * 2 * bytes_per_component


def decode_channels(header: dict, payload: bytes) -> list[list[complex]]:
    """Decode the channel-major IQ payload into [n_ch][cpi_length] complex lists."""
    n_ch = header["active_ant_chs"]
    cpi = header["cpi_length"]
    channels: list[list[complex]] = []
    if header["sample_bit_depth"] == 8:
        # CINT8, normalised to [-1, 1).
        per_ch = cpi * 2
        for c in range(n_ch):
            base = c * per_ch
            raw = payload[base : base + per_ch]
            samples = [
                complex(_i8(raw[2 * t]) / 128.0, _i8(raw[2 * t + 1]) / 128.0)
                for t in range(cpi)
            ]
            channels.append(samples)
    else:
        # CFLOAT32 interleaved I,Q per sample, channel-major.
        per_ch = cpi * 2
        floats = struct.unpack_from(f"<{n_ch * per_ch}f", payload, 0)
        for c in range(n_ch):
            base = c * per_ch
            samples = [
                complex(floats[base + 2 * t], floats[base + 2 * t + 1]) for t in range(cpi)
            ]
            channels.append(samples)
    return channels


def _i8(b: int) -> int:
    return b - 256 if b >= 128 else b


def build_frame(
    channels: list[list[complex]],
    rf_center_freq: int,
    sampling_freq: int,
) -> bytes:
    """Build a CFLOAT32 IQ frame (header + payload) — used by tests and as the
    reference for the on-wire format."""
    n_ch = len(channels)
    cpi = len(channels[0]) if channels else 0
    header = bytearray(HEADER_SIZE)
    struct.pack_into("<I", header, 0, SYNC_WORD)
    struct.pack_into("<I", header, 28, n_ch)
    struct.pack_into("<Q", header, 36, rf_center_freq)
    struct.pack_into("<Q", header, 44, sampling_freq)
    struct.pack_into("<Q", header, 52, sampling_freq)
    struct.pack_into("<I", header, 60, cpi)
    struct.pack_into("<I", header, 88, 3)   # data_type = CFLOAT
    struct.pack_into("<I", header, 92, 32)  # sample_bit_depth
    flat: list[float] = []
    for ch in channels:
        for s in ch:
            flat.append(s.real)
            flat.append(s.imag)
    payload = struct.pack(f"<{len(flat)}f", *flat)
    return bytes(header) + payload
