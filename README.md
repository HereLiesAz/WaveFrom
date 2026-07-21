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
- `ar/` — the overlay layer: a sensor renderer (compass + gyro + gnomonic
  projection) and an ARCore world-tracking renderer, picked at runtime; plus the
  `frame/` bearing-frame calibration that reconciles SDR-array, magnetic and
  ARCore-session azimuths onto true north.
- `ui/` — Jetpack Compose camera view, HUD, 3D IQ-helix viewer, spectrum
  waterfall, calibration controls, and the permission flow.
- `companion/` — a Raspberry Pi sensor pod (Python) that hosts dongles/SDRs the
  phone can't drive and streams bearings over the WaveFrom wire protocol. See
  [`companion/README.md`](companion/README.md).

## Status

The full pipeline is built end to end. On a bare phone it runs with no extra
hardware; every additional radio raises the localization tier it can reach.

- **Overlay** — live camera with floating, band-colored markers; a sensor
  renderer (compass + gyro + gnomonic projection) and an ARCore world-tracking
  renderer are picked at runtime, with bearing-frame calibration reconciling
  SDR-array, magnetic and ARCore azimuths onto true north.
- **Sources** — the phone's own Wi-Fi / BLE / cellular radios (RSSI-only, with
  optional OpenCellID cell-tower geolocation), network/USB SDR ingestion,
  on-phone USB SDR including **HackRF** (vendored `hackrf_android`), and
  dual-radio interferometry.
- **Localization** — on the ARCore path the motion-aided localizer is live:
  the cm-level VIO pose stream, fused with Wi-Fi-RTT / RSSI ranges, feeds a
  synthetic-aperture trilateration solver that upgrades RSSI-only emitters to
  motion-estimated 3D positions. On devices without ARCore the same solver runs
  off a GPS aperture instead — each fix is a pose in a local ENU frame — but GPS's
  several-metre noise caps those solves to low confidence, so they mainly surface
  when paired with accurate Wi-Fi-RTT ranges. The interferometer seam and the
  companion pod's real DSP direction-of-arrival round out the higher tiers.
- **Visualization** — a 3D IQ-helix viewer and a spectrum waterfall fed over the
  WaveFrom wire protocol.
- **Record & replay** — capture the live detection stream (post-localizer) to a
  newline-delimited `.wfrec` file and replay it back through the same pipeline
  with original timing, so the overlay can be developed and demoed without live
  hardware. A replay banner keeps recorded data from being mistaken for live.
- **Top-down map** — a north-up radar view of the same live tracks from a second
  vantage point: located emitters as dots with breadcrumb trails, bearing-only
  emitters as rays, RSSI-only emitters as range rings — each by honesty tier, never
  faking a position. Located emitters get real lat/lon when a GPS origin is known.
- **Companion pod** — a Raspberry Pi host (Python) that drives dongles/SDRs the
  phone can't, including HackRF via SoapySDR, and streams bearings + spectrum.
- **Release** — signed release builds (AAB + APK) with programmatic per-build
  versioning, published to GitHub releases and the Google Play internal track
  from CI.

## Build

```bash
./gradlew assembleDebug      # APK
./gradlew testDebugUnitTest  # JVM unit tests (domain logic)
```

## License

WaveFrom is licensed under the **GNU General Public License, version 2 or later**
(see [LICENSE](LICENSE)). On-phone HackRF support vendors Dennis Mantz's GPL
[`hackrf_android`](https://github.com/demantz/hackrf_android) library, so the app as
a whole is GPL. See [NOTICE](NOTICE) for third-party attribution.
