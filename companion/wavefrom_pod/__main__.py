"""CLI entrypoint: ``python -m wavefrom_pod --host <phone-ip> --backend simulator``."""
from __future__ import annotations

import argparse
import sys

from .backends import BACKENDS
from .pod import run
from .transport import UdpTransport

DEFAULT_PORT = 50505  # must match NetworkSdrSource.DEFAULT_PORT on the phone


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="wavefrom_pod", description="WaveFrom RF sensor pod")
    parser.add_argument("--host", default="255.255.255.255",
                        help="phone IP (or broadcast address, the default)")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--backend", choices=sorted(BACKENDS), default="simulator")
    parser.add_argument("--pod-id", default="pi-pod")
    parser.add_argument("--rate", type=float, default=10.0, help="updates per second")
    # Radio params (used by the relevant backends; others ignore via **kwargs).
    parser.add_argument("--center-freq", type=float, default=433_000_000.0)
    parser.add_argument("--sample-rate", type=float, default=2_400_000.0)
    parser.add_argument("--gain", default="auto")
    parser.add_argument("--nfft", type=int, default=1024)
    parser.add_argument("--radius-m", type=float, default=0.17, help="KrakenSDR UCA radius")
    parser.add_argument("--heimdall-host", default="127.0.0.1")
    parser.add_argument("--heimdall-port", type=int, default=5000)
    parser.add_argument("--csi-port", type=int, default=5500, help="Nexmon CSI UDP port")
    parser.add_argument("--quadrf-host", default="127.0.0.1", help="QuadRF DoA daemon host")
    parser.add_argument("--quadrf-port", type=int, default=8000, help="QuadRF DoA daemon port")
    args = parser.parse_args(argv)

    radio_kwargs = dict(
        center_freq=args.center_freq,
        sample_rate=args.sample_rate,
        gain=args.gain,
        nfft=args.nfft,
        radius_m=args.radius_m,
        heimdall_host=args.heimdall_host,
        heimdall_port=args.heimdall_port,
        udp_port=args.csi_port,
        freq_hz=args.center_freq,
        quadrf_host=args.quadrf_host,
        quadrf_port=args.quadrf_port,
    )
    try:
        backend = BACKENDS[args.backend](**radio_kwargs)
    except TypeError:
        backend = BACKENDS[args.backend]()
    transport = UdpTransport(args.host, args.port)
    print(f"WaveFrom pod '{args.pod_id}' [{backend.name}] -> {args.host}:{args.port} @ {args.rate} Hz")
    try:
        run(backend, transport, pod_id=args.pod_id, rate_hz=args.rate)
    except KeyboardInterrupt:
        print("\nstopped")
    except NotImplementedError as e:
        print(f"backend not available: {e}", file=sys.stderr)
        return 2
    finally:
        transport.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
