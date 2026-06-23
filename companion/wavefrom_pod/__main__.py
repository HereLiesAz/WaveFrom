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
    args = parser.parse_args(argv)

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
