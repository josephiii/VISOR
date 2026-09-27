# Upgrading VISOR to MWDAT 1.0

*Meta Wearables Device Access Toolkit 0.9.0 → 1.0.0 (released 2026-09-24): the plan, what changed, what VISOR gained, and how it was verified.*

---

## In one paragraph

VISOR's Android app (`frontend/visor`) now builds against MWDAT **1.0.0** from
Maven Central. The migration itself was small: VISOR used no API that 1.0
removed. The larger work was using what 1.0 made possible. The team's IMU
research had stalled at a platform wall: the Web Apps API exposes the glasses'
IMU but not their camera, so the second half of the goal (IMU-measured head
movement **against the camera image shift it causes**) could not be measured.
MWDAT 1.0's experimental **Motion** capability streams the IMU into a native app
alongside the camera stream in the same session. VISOR now has a **head-motion
lab** that records both and measures their discrepancy, plus an analysis
pipeline that was validated against ground truth. Three smaller features follow
Meta's two new samples: **"Hey Meta, start VISOR"** voice launch
(VoiceInvocationsSample), live **glasses status** from 1.0's device-state
fields (BirdSpotter), and Meta-AI-initiated registration.

---

## The plan, and where it stands

| Phase | What | Status |
|---|---|---|
| 1. Migrate | Bump to 1.0.0; move off GitHub Packages; fix what 1.0 changed | Done |
| 2. Baseline | Build and test on 0.9 first, so regressions are attributable | Done: debug build passed; **unit and instrumentation tests did not compile on 0.9** (see below) |
| 3. Repair tests | Make the existing test suites compile and run again | Done |
| 4. New capabilities | Head-motion lab (Motion + Camera), device status, voice launch, registration intents, error handling, debug tools | Done |
| 5. Analysis | Read native sessions; IMU-versus-image discrepancy module; ground-truth synthetic data | Done |
| 6. Verify | JVM unit tests, MockDeviceKit instrumentation tests on an emulator, synthetic ground truth, release build | Done (see [Verification](#verification)) |
| 7. Hardware | Run on real Meta Ray-Ban Display glasses | **Not done; needs the team's glasses.** Checklist below |

---

## What changed in the SDK, and what it meant for VISOR

| 1.0 change | Effect on VISOR | What was done |
|---|---|---|
| Published to **Maven Central** (`com.meta.wearable`) | The GitHub Packages repo and the `github_token` PAT are no longer needed | Removed from `settings.gradle.kts`, the CI workflow and the README cold start |
| Inline `DatResult` helpers compiled for **JVM 11** | Could not be inlined into VISOR's Java 1.8 bytecode (13 compile errors) | Java/Kotlin target raised to 17, as in Meta's VoiceInvocationsSample |
| `Wearables.getDeviceState` removed; battery, charging, don, hinge and thermal state moved onto `Device` | Not used by VISOR. The new fields are now used for the glasses status | `wearables/GlassesStatus.kt` |
| New `DeviceSessionError.INSUFFICIENT_SDK_VERSION` (terminal) and `DWA_OUT_OF_STU_RANGE` (warning, not an ending) | A warning must not end a session | `wearables/SessionErrors.kt`: plain-language, speakable messages; the warning is logged and ignored |
| `Wearables.handleIntent(...)` for registrations **started in the Meta AI app** | New entry point | Handled in `MainActivity` (onCreate and onNewIntent) |
| `DAM_ENABLED` manifest key ignored (App Model always on since 0.9) | Dead config | Removed |
| Crash reporting on by default | VISOR already opts out of analytics | Added `CRASH_REPORTING_OPT_OUT` |
| MockDeviceKit gains Motion, Input, Speech, Voice-invocation and Camera-capture kits, battery/thermal simulation and a mock Ray-Ban Display | VISOR's debug menu still called `pairRaybanMeta()`, which was removed in 0.8, through a cast workaround | Debug menu rebuilt on `pairGlasses(model)` with the new kits |
| Version floor: **Meta AI app V290, glasses firmware V128** (all models) | Older firmware or app fails | Documented for testers |

**Found while baselining (pre-existing on 0.9):** the unit tests did not compile
(no JUnit dependency), and the instrumentation tests did not compile (they
called `pairRaybanMeta()`, referenced a string resource that no longer exists,
and loaded video assets that were never committed). They were written for
Meta's CameraAccess sample screens, which VISOR no longer routes to. Both
suites now compile and pass. The old `InstrumentationTest.kt` was replaced by
`MwdatMockDeviceTest.kt`, which tests VISOR's real flows. The AndroidX Test
libraries were also raised (Espresso 3.5 → 3.7): Espresso < 3.6 reflects on a
hidden Android API that current Android removed, which failed every Compose UI
test before it started.

**Two pre-existing crashes, found by the new tests and fixed** (neither was
caused by the SDK change):

- **Wake-word listener use-after-free.** `stt/Listener.stop()` released the
  sherpa-onnx native stream on the main thread while the `visor-kws` audio
  thread could still be decoding with it, causing a native SIGSEGV. It fired on
  activity teardown, and can fire whenever "VISOR GO" pauses wake listening.
  `stop()` now waits for the audio thread (bounded) before releasing, and leaks
  rather than frees a stream that is still in use.
- **Every minified release crashed on launch.** R8 renamed the sherpa-onnx
  config fields its native code reads by name (`NoSuchFieldError:
  maxActivePaths`). The local AAR ships no keep rules; `proguard-rules.pro` now
  keeps `com.k2fsa.sherpa.onnx.**`. This affected the release APKs the CI
  workflow publishes.

---

## VISOR's features vs. what 1.0 offers

| MWDAT 1.0 capability | Before | Decision |
|---|---|---|
| **Motion** (IMU: accel, gyro, magnetometer, fused quaternion; 5–60 Hz) *(experimental)* | IMU only through the Web Apps API; no camera | **Adopted**: head-motion lab |
| **Camera + Motion in one session** | Impossible on the web path | **Adopted**: the lab's VOR tier (V1–V4) |
| **Device state on `Device`** (battery, charging, worn, hinge, thermal) | Only firmware compatibility was read | **Adopted**: Pair Glasses status card (read aloud on request) and every recording's metadata |
| **Voice invocations** ("Hey Meta, start VISOR") *(experimental)* | None; VISOR had to be opened on the phone | **Adopted**: opens VISOR, goes Home, starts a session, answers the glasses |
| **Registration requests from Meta AI** (`handleIntent`) | Not handled | **Adopted** |
| Display (`mwdat-display`) | Glasses tap-navigation | **Kept**. It now hands the glasses' single session to the lab during a trial (`GlassesLease`) and reconnects after |
| Camera stream + in-stream photo | `StreamViewModel` (from the CameraAccess sample, not routed) | Kept compiling; the lab uses the stream |
| **Inputs** (capture/action button, touchpad, Neural Band) *(experimental)* | — | **Deferred.** Real glasses do not deliver `Back` yet; subscribing to touchpad or Neural Band would take those gestures away from the Display navigation that already uses them; the action button is "not validated on all hardware". Recommended next: capture button → "read this" |
| **Camera Capture** (standalone high-res photo) *(experimental)* | OCR reads a bundled test photo (`FakePhotoSource`) | **Deferred**, and paired with Inputs above: capture button + standalone photo would give VISOR's core reading feature real glasses images |
| **Speech** (on-glasses ASR) *(experimental)* | Phone mic: sherpa-onnx wake words + Android SpeechRecognizer | **Deferred.** Valuable for a phone in a pocket, but it replaces the core voice UX and deserves its own change |
| Audio streaming (PCM with the camera stream) *(experimental)* | — | Deferred; no current consumer |
| Mock Ray-Ban Display preview (`MockDisplayKit`) | — | Partly: the debug menu pairs a mock Ray-Ban Display; the preview view is not embedded |

**Experimental means dev/beta only.** Motion, Voice invocations, Inputs, Speech,
Camera Capture and Audio streaming *"can be built and tested against, though
apps using them cannot be published yet"* (MWDAT changelog). The lab and voice
launch work in Developer Mode and beta release channels; a production release
channel will not get them until Meta graduates the APIs.

---

## What was added, and why

### 1. Head-motion lab (Settings → Research → Head-motion lab)

**Why:** this is the measurement the web path could not make. It records the
glasses' IMU through MWDAT Motion and, in the VOR tier, the image shift the
head-fixed camera sees at the same time. That is the raw material for the
IMU-versus-image discrepancy the team wants as a signal for later VOR studies.

**Trials.** The web IMU Lab's battery (A1–D1) carried over with the same ids,
timings and cues, so native and web recordings of the same trial can be
compared. There is also a new **Tier V** (camera on):

| Trial | Movement | Pacing |
|---|---|---|
| V1 | Side-to-side head turns, eyes on a target (VOR×1 exercise movement) | 1 Hz metronome |
| V2 | Up-and-down nods, eyes on a target | 1 Hz metronome |
| V3 | Faster side-to-side turns (safety text: seated, stop if dizzy) | 2 Hz metronome |
| V4 | Slow look-around. Camera calibration: little motion blur | Spoken cue every 3 s |

**How it works** (`app/src/main/java/ucf/visor/motionlab/`):

- `LabGlassesLink` opens one DAT session. For camera trials it attaches the
  camera first and attaches Motion **only once frames are streaming**. That
  lesson comes from BirdSpotter: a capability attached while the device is
  still arriving fails with `DEVICE_DISCONNECTED` and never retries. Motion is
  subscribed before `start()` because samples don't replay. It runs at 60 Hz.
  A Motion stop nobody asked for is restarted and written into the recording.
- Frames are decoded I420 at the lowest resolution and highest frame rate
  (360×640 at 30 fps), because timing matters more than pixels here.
  `FrameAnalyzer` box-averages the luma plane into a 128×256 grid, and a worker
  thread measures the frame-to-frame shift by **phase correlation** (Hann
  window, Gaussian spectral weighting, sub-pixel Gaussian peak fit). Pure
  Kotlin, never blocking the SDK's stream, about 8 ms a frame on the emulator.
- `SessionRecording` writes **`visor.imu.session/2`**: the web schema's
  envelope plus `dat_motion` (every MotionSample in SI units) and `dat_video`
  (frame timing, shift, correlation peak, texture). Absent readings are `null`,
  never 0, as in the web capture engine. Every row keeps **both** the glasses'
  timestamp and the phone's arrival time: the SDK does not say whether video
  timestamps share the motion clock, so the analysis tests it rather than
  assuming.
- `QuickLook` gives the tester an immediate spoken and on-screen verdict:
  rates, dropouts, share of frames tracked, and the IMU-to-image lag and
  pixels-per-degree. When head rotation explains less than half the image
  motion (R² < 0.5), it says to re-run rather than reporting numbers.
- `GlassesLease`: MWDAT allows one session per device. The lab takes the lease
  for a trial; the glasses tap-navigation steps aside and reconnects afterwards.

**Privacy by construction.** Camera frames are reduced to a luma grid in memory,
correlated, and discarded. **No image is ever written to disk or sent
anywhere.** Only the measured shift, one number pair per frame, is recorded.
Participant codes only, never names (same rule as the web lab).

**Accessibility.** Every phase is announced by speech and distinct tones (the
web lab's palette), since during a VOR trial the wearer's eyes are on a target
and during a static trial the glasses are on a table. Controls are full width
and 56–64 dp tall. The phase is a polite live region for TalkBack (the ticking
timer deliberately is not). The screen stays on through a trial, and Back stops
the trial rather than leaving it running unseen.

**Output.** Recordings are saved under
`Android/data/edu.ucf.visor/files/imu-sessions/<participant>/<trial>/`, with a
**Share recording** button. Copy them into `data/imu-sessions/` and run
`python analysis/analyze.py`.

### 2. The discrepancy analysis (`analysis/visor_imu/vor.py`)

It fits image velocity **v ≈ K·ω(t − τ) + b** over quality-gated frame intervals:

- **τ**, the IMU-to-image lag, by maximizing explained variance over ±400 ms.
  If motion and video timestamps are consistent with one shared clock, τ is
  measured on the device clock, i.e. a true sensor-to-image latency. If not,
  both streams are mapped onto the phone clock by their lower envelopes, and
  the report says the lag then includes the streams' delivery difference.
- **K**, the IMU-to-camera axis mapping and the **pixels-per-degree** scale, is
  recovered, not assumed. Columns for axes the trial did not rotate about are
  reported as null.
- **The discrepancy**: the residual angular velocity (°/s RMS) and its share of
  head speed. Paced trials also get amplitude and phase at the paced frequency.
  Trials are scaled against the participant's V4 calibration, so an image that
  stops keeping up at higher speed shows as a gain below 1.
- **What it does not claim:** a head-fixed camera sees the retinal slip an eye
  *without* a VOR would see. This is the head-motion and image half of a VOR
  measurement, with its timing. It is **not a VOR gain**; that also needs eye
  movement. Head translation (parallax), roll and motion blur land in the
  residual. Fits with R² < 0.5 are withheld from the report.

### 3. Glasses status (Pair Glasses screen)

**Why:** "how much battery do my glasses have?" should not require reading a
charging case's light. This uses 1.0's `Device` fields: model, connection,
battery and charging, worn, folded, temperature (collapsed to
normal/warm/hot/too hot), with **Read glasses status aloud**. The same status is
stamped into every lab recording at start and end, and mid-trial changes (a
doff, heat, battery steps) are marked in the data.

### 4. "Hey Meta, start VISOR"

**Why:** for a low-vision user, finding and unlocking the phone is the hardest
step of using VISOR. Following VoiceInvocationsSample: a process-scoped stream
(`wearables/VoiceLaunches.kt`) so a cold launch is not missed;
`launchMode="singleTask"`; `isVoiceInvocationsIntent` in `onCreate` and
`onNewIntent`. The action is **idempotent** (start a session if none, never
toggle), because a launch can arrive both as an intent and on the stream. Every
launch is answered exactly once: success with "VISOR is ready. Session started.",
or failure with "Please log in on your phone" when logged out.

### 5. Debug tools (debug builds, bug button)

Pair a mock **Ray-Ban Meta** or **Ray-Ban Display**. Replay a synthetic 1 Hz
head shake on the mock IMU. Set battery to 12%/95%, make hot or cool. Simulate
"Hey Meta, start VISOR". Open the head-motion lab directly.

---

## Setup the team needs to do (Wearables Developer Center)

1. **Enable Motion** for the VISOR app. It is experimental, and without it the
   lab reports "Motion has not been enabled for VISOR in the Wearables
   Developer Center".
2. **Voice Invocation permission** for package `edu.ucf.visor`, with a spoken
   app name **without "AI"** (Meta: it is intercepted by the assistant).
   "VISOR" is fine.
3. **Credentials**: `mwdat_application_id` / `mwdat_client_token` in
   `local.properties`. Unset, they now fall back to `"0"`, MWDAT 1.0's
   documented Developer Mode placeholder (an *empty* value, the previous
   fallback, ended every session). **Voice launch does not work with Developer
   Mode on** (Meta known issue), so testing it needs real credentials, Developer
   Mode off, and a beta release channel.
4. **Versions**: Meta AI app ≥ V290, glasses firmware ≥ V128.
5. The lab's camera trials ask for the glasses **camera permission** through the
   Meta AI app on first use.

---

## Verification

| Check | How | Result |
|---|---|---|
| Builds | `assembleDebug`, `assembleRelease` (R8 minify) | Pass. The release build logs R8 warnings that it cannot parse Kotlin 2.2 metadata: MWDAT 1.0 brings Kotlin stdlib 2.2.20, newer than AGP 8.6's R8 (only matters for Kotlin reflection; upgrading AGP clears it) |
| JVM unit tests (new) | FFT against a direct DFT; phase correlation on a synthetic scene (integer, sub-pixel and noisy shifts, correct sign); luma sampling on all three stream sizes; session writer read back by a real JSON parser; trial runner on virtual time (cue schedule, abort/fail exactly once); quick look recovering an injected lag and scale, rejecting Neural Band samples, withholding unreliable fits; the synthetic IMU feed integrating to its own quaternion | **38/38 pass** |
| Instrumentation on MockDeviceKit (emulator, Android 37, 16 KB pages) | Device state → glasses status; lab records the mock IMU through Motion at 60 Hz and writes a readable session; a video panning a known **6 px/frame**, encoded on device and played through the mock camera, measured by the lab on the SDK's own decoded frames; "Hey Meta" launch → session started and answered once; register button does not crash | **6/6 pass, twice in a row** (54.8 s, 53.1 s). Measured shift **−6.01 px/frame** (truth −6.00, 10th–90th pct −6.01 to −6.00), correlation peak 0.88, 149/149 frame pairs analyzed, 8 ms per frame |
| Bugs caught by those tests | `link?.awaitData() ?: "not running"` turned every *successful* start into a failure (null meant "ready"); the wake-word listener's native use-after-free (above) | Both fixed |
| Release APK on the emulator | Install the minified release build and launch it | Crashed at launch before the sherpa-onnx fix (above). After it: launches and stays up; the wake-word listener and MWDAT initialize and the glasses-nav gate runs |
| Analysis ground truth | `make_synthetic.py` native sessions: 6.00 px/°, 35 ms latency, shared and separate clocks, blurred and skipped frames | Scale 5.99–6.01; lag 35/35/36 ms (shared), 60 ms (separate, expected 60); residual 1.94–2.07 °/s (expected 2.0); calibrated gain 0.999–1.003; clock basis detected correctly both ways |
| No regression in the web analysis | Existing synthetic web sessions | Scan asymmetry +0.250, gait 1.751 Hz: identical to the documented values |
| Loader ↔ Kotlin writer | Python analysis of the files the app wrote on the emulator | Parsed; the mock pan (unrelated to the mock head shake) correctly produced **R² 0.01 → fit withheld** |

### What only real glasses can answer

Run these on the team's Meta Ray-Ban Display before relying on any number:

1. **Axis convention.** V1 should report dominant axis **Y** (yaw) and image
   **x**; V2 axis **X** and image **y**. The loader's yaw/pitch/roll naming
   follows BirdSpotter's measurement on worn glasses (+Y up, −Z forward).
2. **Clock sharing.** Does the report say "device clock" or "phone arrival
   envelope"? That settles whether the lag is an absolute latency.
3. **Motion with the glasses off** (A1/A2). The SDK may pause a session when
   the glasses are doffed; the recording will show `motion_state PAUSED` marks
   and QC will flag it. If it pauses, the static trials stay on the web lab.
4. **Magnetometer.** Null on Ray-Ban Meta (Meta known issue); check whether
   MRBD reports one.
5. **Camera + Motion bandwidth.** Motion at 60 Hz alongside 30 fps video over
   Bluetooth: check the report's arrival-timing and dropout rows.
6. **Heat.** Camera streaming warms the glasses; watch thermal marks on V3/V4.
7. **Voice launch** end to end (Developer Mode off, WDC approval).

---

## Known limitations

- Experimental APIs may change before general availability; the lab and voice
  launch cannot ship to a production release channel yet.
- Phase correlation measures translation. Roll and head translation (parallax)
  are not modelled and appear as residual.
- The glasses' auto-exposure and compression can reduce tracking on dim or
  plain scenes. The per-frame correlation peak and texture gates exclude those
  frames, and the tracked share is reported.
- Recordings stay on the phone until shared; the web lab's Blob upload path is
  not wired into the app (adding a client-side key to the APK would not be real
  access control).

## Next steps (recommended)

1. **Hardware verification**: the checklist above, starting with V4 then V1.
2. **Capture button → "read this"**: Inputs (capture button) plus Camera Capture
   (standalone high-res photo) replacing `FakePhotoSource`, giving VISOR's core
   reading feature real glasses images with a physical trigger.
3. **Head-stability gated capture**: use the Motion stream live to take the OCR
   photo once the head has settled (web use case #1, now native).
4. **On-glasses speech** for voice navigation when the phone is pocketed.
5. **Toolchain**: AGP 8.6 → a release whose R8 reads Kotlin 2.2 metadata (Meta's
   samples use AGP 8.11 and 9.3), then Kotlin 2.1 → 2.2 to match MWDAT.
