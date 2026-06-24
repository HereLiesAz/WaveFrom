# WaveFrom companion — Raspberry Pi RF sensor pod

A small Python service that turns a Raspberry Pi (or any Linux box) into a
**secondary-antenna RF sensor** for the WaveFrom Android app.

## Why a Pi?

A phone can't talk to most USB Wi-Fi/Bluetooth/SDR dongles — Android lacks the
drivers, and raw phase/CSI is locked down. A Pi has none of those limits: full
Linux drivers, `rtl-sdr`, monitor-mode NICs with Nexmon CSI, BlueZ, and enough
compute to run real **multi-antenna direction finding** across several coherent
dongles. The pod does the DSP and streams *bearings* to the phone, which just
renders them in AR. This is the same topology as QuadRF (a Pi + an RF front-end).

```
  dongles / SDR array ──► Raspberry Pi (this pod) ──► UDP/JSON ──► phone (NetworkSdrSource ──► AR overlay)
```

The phone also supports plugging dongles in **directly** over USB-OTG
(`DualRadioSource` / `UsbSdrSource`); the Pi pod is the complementary path for
hardware Android can't drive.

## Wire protocol

Newline-delimited JSON over UDP (default port **50505**, matching
`NetworkSdrSource.DEFAULT_PORT` on the phone). One message per line:

```json
{"type":"bearing","trackId":"krk-7","freqHz":5800000000,"powerDbm":-61.2,
 "azimuthDeg":134.5,"elevationDeg":3.0,"confidence":0.82,"label":"5.8GHz FPV","ts":1719100000000}
{"type":"heartbeat","podId":"pi-roof","antennaCount":4,"ts":1719100000000}
```

The Android decoder is `app/.../signal/source/sdr/WireProtocol.kt`; this package
is the reference producer of the same contract.

## Run it

No hardware required for the simulator backend:

```bash
cd companion
# Broadcast synthetic bearings to every phone on the LAN:
python3 -m wavefrom_pod --backend simulator --rate 10
# …or target one phone directly:
python3 -m wavefrom_pod --backend simulator --host 192.168.1.50
```

Open WaveFrom on the phone with a `NetworkSdrSource` registered and the
synthetic emitters appear as true-bearing markers in the AR view.

Run as a service with `systemd/wavefrom-pod.service`.

## Backends

| name        | status      | what it does |
|-------------|-------------|--------------|
| `simulator` | ✅ working   | synthetic orbiting emitters + spectrum, no RF hardware |
| `rtlsdr`    | ✅ working   | single RTL-SDR dongle → real power spectrum (waterfall); no DoA (1 antenna) |
| `krakensdr` | ✅ working   | coherent 5-ch KrakenSDR via Heimdall DAQ → beamforming DoA → true bearings |
| `wificsi`   | ✅ working   | Nexmon-CSI NIC → per-frame antenna×subcarrier matrix → beamforming DoA |
| `ble`       | 🚧 stub     | Bluetooth 5.1 AoA |

The DSP (`wavefrom_pod/dsp.py`) is pure-Python (Bartlett beamformer, FFT) and runs
without numpy; install **numpy** for fast covariance + high-resolution MUSIC, and
**pyrtlsdr** for the RTL-SDR/Heimdall paths (`pip install -r requirements.txt`).

### Hardware setup
- **RTL-SDR:** `python3 -m wavefrom_pod --backend rtlsdr --center-freq 433e6 --sample-rate 2.4e6`
- **KrakenSDR:** run the [Heimdall DAQ](https://github.com/krakenrf/heimdall_daq_fw)
  (serves coherent IQ on TCP :5000), then `--backend krakensdr` (`--radius-m` to match
  your array). Sanity-check against [krakensdr_doa](https://github.com/krakenrf/krakensdr_doa) :8080.
- **WiFi CSI:** flash [nexmon_csi](https://github.com/seemoo-lab/nexmon_csi) on a supported
  NIC streaming CSI UDP to :5500, then `--backend wificsi`.

> The Heimdall IQ-frame and Nexmon CSI packet layouts vary by firmware version —
> see the notes in `heimdall.py` / `nexmon.py` and validate against your install.
> Antenna geometry (`--radius-m`, CSI `spacing_m`) must match your hardware.

Add a backend by subclassing `SensorBackend` in `wavefrom_pod/backends.py` and
registering it in `BACKENDS`.

## Tests

```bash
cd companion && python3 -m unittest discover -s tests -v
```
DSP tests (spectrum, Bartlett DoA for ULA/UCA, Heimdall/CSI parsing) run without
numpy or hardware.
