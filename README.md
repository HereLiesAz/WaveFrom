# WaveFrom

An Android AR app that visualizes radio signals as they emanate from their
sources, overlaid on the live camera feed — inspired by QuadRF's "RF camera".

A phone alone can't do true direction-finding (its radios are single-antenna and
band-locked — they measure *strength*, not *direction*), so WaveFrom is built
around a pluggable, multi-source pipeline and is honest about how well each
signal can actually be located.

## Localization tiers

Every detected emitter carries a `Direction` describing how well its position is
known, so the overlay never fakes precision it doesn't have:

| Tier | Direction | Source |
|------|-----------|--------|
| 1 | **True bearing** (azimuth/elevation) | external phased-array SDR (QuadRF / KrakenSDR) |
| 1b | **Interferometric bearing** (coarse, front/back-ambiguous) | phone radio + USB dongle, or a multi-dongle Pi |
| 2 | **Motion-estimated 3D position** | mono-antenna depth-from-motion: ARCore pose fused with Wi-Fi-RTT / RSSI samples |
| 3 | **RSSI-only** (fuzzy distance, no direction) | the phone's own Wi-Fi / BLE / cellular radios |

## Architecture

- `signal/` — pure-Kotlin domain: the `Detection`/`Direction`/`Track` model,
  `SignalSource` implementations (Wi-Fi, BLE, cellular, network/USB SDR,
  dual-radio), the `SignalRepository` aggregator, path-loss physics, and the
  `MotionAidedLocalizer` / `Interferometer` seams.
- `ar/` — the overlay layer: a sensor-based renderer (compass + gyro + gnomonic
  projection) today, with an ARCore seam (`ArRendererFactory`) for Phase 2.
- `ui/` — Jetpack Compose camera view, HUD, and permission flow.
- `companion/` — a Raspberry Pi sensor pod (Python) that hosts dongles/SDRs the
  phone can't drive and streams bearings over the WaveFrom wire protocol. See
  [`companion/README.md`](companion/README.md).

## Status — Phase 1

Runs on any phone with no extra hardware: live camera with floating, band-colored
markers for nearby Wi-Fi APs, BLE devices and cellular cells (RSSI-only), a
debug synthetic bearing source that validates the azimuth/elevation projection,
and the full source/aggregator/overlay pipeline. ARCore rendering, the
motion-aided localizer, network/USB SDR ingestion and dual-radio interferometry
are scaffolded for later phases.

## Build

```bash
./gradlew assembleDebug      # APK
./gradlew testDebugUnitTest  # JVM unit tests (domain logic)
```
