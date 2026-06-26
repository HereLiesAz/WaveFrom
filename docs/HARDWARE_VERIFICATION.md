# Hardware verification checklist

Three WaveFrom features depend on inputs that don't exist on CI or an emulator — a
trustworthy magnetometer, a live ARCore session, and a real serving cell with a GPS
fix. Their pure logic is unit-tested (`FrameMathTest`, `CalibrationActionsTest`,
`NorthSeedTest`, `CellularUpgradeTest`, `GeoBearingTest`, `HudControlsComposeTest`) and
the panel renders under an emulator smoke test (`SmokeInstrumentedTest`), but the
end-to-end behavior must be confirmed by hand on a device.

Install a **debug** build (`./gradlew installDebug`) so the on-screen **diagnostics
overlay** (bottom-left, debug-only) is visible. It shows the live calibration state used
by every step below: heading→true conversion, compass accuracy, the SDR/north/declination
offsets, the ARCore seed flag, the centered SDR pick, and the last cell→tower resolve.

## 1. Align to crosshair

1. Identify a known external-SDR emitter and connect the SDR/pod so its bearing appears
   as a marker (`SourceType.EXTERNAL_SDR`).
2. Point the phone's crosshair (screen center) directly at the real emitter.
3. Confirm the overlay lists that emitter as **centered SDR** with a non-zero raw azimuth,
   and open the **Calibrate** panel — the button reads `Align "<label>" to crosshair`.
4. Tap it. **Pass:** the marker snaps onto the crosshair, and the overlay's `SDR off`
   value updates to the solved offset. Rotate the phone — the marker tracks the emitter as
   you turn.

## 2. ARCore session → north

1. Go somewhere with a clean magnetic environment; wave the phone in a figure-8 until the
   red **"Compass unreliable"** warning disappears (accuracy ≥ MEDIUM).
2. Open the ARCore view and let tracking stabilize.
3. **Pass:** the overlay shows `ARCore seeded true` with a non-zero `sess→N` offset, and AR
   markers point at true north — cross-check one against a known landmark bearing (e.g. a
   compass app or a map). Walk around: markers hold their true-north heading instead of
   drifting with the session frame.
4. Background/foreground the AR view (session reset) and confirm it **re-seeds** (`seeded`
   briefly returns to false, then true again) once the compass is trustworthy.

## 3. Cellular OpenCellID bearing

1. Build with a real key — `opencellid.api.key` in `local.properties`, or the
   `OPENCELLID_API_KEY` env var. Use a device with a SIM in service, location permission
   granted, and a recent GPS fix.
2. Open the app and wait one poll (~5 s).
3. **Pass:** a cell that started as a range-only marker gains a tower bearing; the overlay's
   `cell … → <deg>` line shows the resolved tower and bearing, and that bearing matches
   `GeoBearing.azimuthDeg(yourFix, tower)` (point a compass from your location toward the
   tower's database coordinates to sanity-check).
4. **Opt-in check:** build with a blank key and confirm cells stay range-only (no bearing,
   no network calls) — the feature must never be a hard dependency.

## 4. 3D IQ-helix waveform

The geometry/projection is unit-tested (`HelixGeometryTest`, `OrbitProjectionTest`,
`AnchoredHelixProjectionTest`, `WireProtocolWaveformTest`); these checks confirm the live
rendering and the real-IQ paths on a device.

1. **Parametric (any signal):** with WiFi/BLE/cellular markers on screen, tap one. The 3D
   viewer opens with a spinning band-colored helix and a **PARAMETRIC** badge; drag orbits,
   pinch zooms. Tapping a nearby-strip chip works too. Close returns to the camera view.
2. **AR-anchored:** toggle **AR helix** — each bearing track grows a small foreshortened
   helix pinned at its marker, pointing along the line of sight; confirm it tracks as you
   move and doesn't open the full viewer.
3. **Real on-phone IQ:** attach an RTL-SDR over OTG and start `rtl_tcp_andro`. A **⌁ USB
   SDR** button appears; tap it — the viewer shows a **REAL** badge and the helix reacts to
   the live signal (twist = true baseband modulation), not a synthetic spin.
4. **Real network IQ:** run the Pi pod with `--backend krakensdr` (or `rtlsdr`). A Kraken
   bearing marker, when tapped, shows a REAL helix for that located emitter; a single-antenna
   `rtlsdr` pod surfaces via the **⌁ USB SDR**-style Live IQ button. Confirm the helix
   updates at roughly the pod's `waveform` rate (~2 Hz) and the link stays light.
