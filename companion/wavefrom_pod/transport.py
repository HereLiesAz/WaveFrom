"""Network transport for the pod: ships newline-delimited JSON to the phone.

UDP by default (matches ``NetworkSdrSource`` on the phone, which binds a
``DatagramSocket``). One JSON message per datagram, newline-terminated so a
single packet may also carry several concatenated messages.
"""
from __future__ import annotations

import socket


class UdpTransport:
    def __init__(self, host: str, port: int) -> None:
        self._addr = (host, port)
        self._sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        # Allow broadcast so a pod can fan out to every phone on the subnet.
        self._sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

    def send_line(self, line: str) -> None:
        self._sock.sendto((line + "\n").encode("utf-8"), self._addr)

    def close(self) -> None:
        self._sock.close()
