"""Nexmon CSI UDP packet parsing for the Wi-Fi CSI backend.

`nexmon_csi` (github.com/seemoo-lab/nexmon_csi) turns a supported broadcom NIC
(e.g. the Pi's bcm43455) into a CSI extractor that broadcasts one UDP packet per
captured frame *per RX core (antenna)*. This module parses those packets and
provides a synthetic builder for tests.

IMPORTANT: the precise CSI sample encoding is chip/firmware specific (bcm43455
packs CSI differently from bcm4366, and 80 MHz vs 20 MHz changes the subcarrier
count). The header layout here follows the common UDP format; the per-subcarrier
value is treated as an int16 I/Q pair (one decoded form). Validate against the
installed nexmon_csi before trusting hardware output. The DoA pipeline that
consumes the parsed matrix is encoding-independent and unit tested.
"""
from __future__ import annotations

import struct

MAGIC = 0x1111
HEADER_SIZE = 18


def parse_packet(data: bytes) -> dict:
    """Parse one Nexmon CSI UDP packet into its header fields + CSI samples."""
    if len(data) < HEADER_SIZE:
        raise ValueError("short CSI packet")
    magic = struct.unpack_from(">H", data, 0)[0]
    rssi = struct.unpack_from("b", data, 2)[0]
    src_mac = data[4:10]
    seq = struct.unpack_from("<H", data, 10)[0]
    core_ss = struct.unpack_from("<H", data, 12)[0]
    chanspec = struct.unpack_from("<H", data, 14)[0]
    n = (len(data) - HEADER_SIZE) // 4
    csi = [
        complex(*struct.unpack_from("<hh", data, HEADER_SIZE + 4 * i)) for i in range(n)
    ]
    return {
        "magic": magic,
        "rssi": rssi,
        "src_mac": src_mac,
        "seq": seq,
        "core": core_ss & 0x7,           # RX core = antenna index
        "spatial_stream": (core_ss >> 3) & 0x7,
        "chanspec": chanspec,
        "csi": csi,
    }


def build_packet(
    rssi: int, src_mac: bytes, core: int, seq: int, chanspec: int, csi: list[complex]
) -> bytes:
    """Build a CSI UDP packet (used by tests and as the reference format)."""
    out = bytearray(HEADER_SIZE)
    struct.pack_into(">H", out, 0, MAGIC)
    struct.pack_into("b", out, 2, rssi)
    out[4:10] = src_mac
    struct.pack_into("<H", out, 10, seq)
    struct.pack_into("<H", out, 12, core & 0x7)
    struct.pack_into("<H", out, 14, chanspec)
    for s in csi:
        out += struct.pack("<hh", int(round(s.real)), int(round(s.imag)))
    return bytes(out)
