# VISOR IMU Analysis Toolkit

Analysis pipeline for inertial data captured from Meta Ray-Ban Display glasses,
by either of two capture paths:

| Path | Writes | Adds |
|---|---|---|
| **IMU Lab** web app (`frontend/visor-webapp`), Web Apps DeviceMotion API | `visor.imu.session/1` | — |
| **Head-motion lab** in the Android app (`frontend/visor`, Settings → Research), MWDAT 1.0 Motion | `visor.imu.session/2` | Glasses-clock sample times, unrounded values, fused quaternion, raw magnetometer, motion source; for Tier V and S camera trials, the camera's per-frame **image shift**; the Tier S balance and vestibular tasks |

Both load through `visor_imu/loader.py`. `/2` sessions also get a derived
`devicemotion` frame in the `/1` vocabulary, so every existing analysis runs on
them unchanged. Both paths report the same glasses body frame (x right, y up,
z backward), so `rrAlpha`/`rrBeta`/`rrGamma` mean pitch/yaw/roll on both
(`metrics.GYRO_HEAD_AXIS`). `visor_imu/vor.py` adds the analysis only `/2` can
support: head rotation against the head-fixed camera's image shift. See
[mwdat-1.0-upgrade.md](../documentation/developer/mwdat-1.0-upgrade.md).

Native recordings are shared from the phone (the lab's **Share recording**
button) or pulled with
`adb pull /sdcard/Android/data/edu.ucf.visor/files/imu-sessions`; drop them
under `data/imu-sessions/` next to the web sessions.

Methodology lives in
[documentation/testing/imu-test-protocol.md](../documentation/testing/imu-test-protocol.md);
interpretation and use-case gating in
[documentation/research/imu-capability-analysis.md](../documentation/research/imu-capability-analysis.md).

---

## Setup

```bash
pip install -r analysis/requirements.txt
```

> On Windows, the first import of a freshly installed compiled package can fail
> with `DLL load failed … An Application Control policy has blocked this file`.
> This is a first-touch scan, not a permanent block — re-run the command and it
> resolves.

---

## Pipeline

```bash
# 1. Pull recorded sessions out of the private Vercel Blob store
python analysis/fetch_sessions.py

# 2. Analyze everything and write figures + report
python analysis/analyze.py

# 3. Optional: IMU-only 6-DOF head-pose analysis (orientation filter, drift,
#    zero-velocity updates, step dead reckoning). Native recordings, when
#    present, add a web-vs-native comparison with MWDAT's fused quaternion and
#    raw magnetometer as heading references.
python analysis/pose_analysis.py

# 4. Balance and vestibular tasks (head-motion lab Tier S): sway, hold time,
#    head impulses, gaze stabilization; one figure per task, per participant
python analysis/balance_analysis.py
```

Outputs:

| Path | Contents |
|---|---|
| `documentation/testing/imu-characterization-report.md` | Generated results report |
| `documentation/assets/imu/*.png` | Figures referenced by the report |
| `data/imu-analysis/summary.json` | Machine-readable results for every session |
| `documentation/testing/imu-pose-tracking-report.md` | Generated 6-DOF pose report (`pose_analysis.py`) |
| `documentation/assets/imu/pose/*.png` | Figures referenced by the pose report |
| `data/imu-analysis/pose-summary.json` | Machine-readable pose results (native under `native`) |
| `documentation/testing/imu-balance-report.md` | Generated balance and vestibular report (`balance_analysis.py`) |
| `documentation/assets/imu/balance/*.png` | One figure per task and participant, plus an overview |
| `data/imu-analysis/balance-summary.json` | Machine-readable balance results |
| `data/imu-sessions/` | Downloaded raw sessions (gitignored) |

`fetch_sessions.py` reads `BLOB_READ_WRITE_TOKEN` from
`frontend/visor-webapp/.env.local`. If that file is missing:

```bash
cd frontend/visor-webapp && vercel env pull .env.local
```

---

## Validating without hardware

The generator produces sessions with **known** injected noise, bias, cadence and
scan asymmetry, so the pipeline can be checked against ground truth:

```bash
python analysis/make_synthetic.py
python analysis/analyze.py --in data/imu-sessions-synthetic \
    --report /tmp/synth-report.md --assets /tmp/synth-assets \
    --summary /tmp/synth-summary.json
```

Recovery verified at the current revision:

| Quantity | Injected | Recovered |
|---|---|---|
| Sample rate | 60.0 Hz | 59.79 – 59.99 Hz |
| Accelerometer bias X / Y / Z | +0.03 / −0.02 / +0.05 m/s² | +0.0302 / −0.0202 / +0.0504 |
| Accelerometer noise SD | 0.020 m/s² | 0.0197 – 0.0201 |
| Gyro random walk at τ=1 s | 0.04518 °/s (analytic) | 0.04518 |
| Scan asymmetry index | +0.25 | +0.250 |
| Gait cadence | 1.80 Hz | 1.751 Hz |

Native (`/2`) camera sessions, generated with a known camera model — 6.00 px
per degree, 35 ms sensor-to-image latency, image noise 0.4 px/frame, 3% blurred
frames that must be rejected, 2% frames skipped under load, and both a shared
and a separate video clock:

| Quantity | Injected | Recovered by `vor.py` |
|---|---|---|
| Image scale | 6.00 px/° | 5.99 – 6.00 px/° |
| Lag, shared clock (device-clock basis) | 35 ms | 35 / 35 / 36 ms |
| Lag, separate clocks (arrival basis) | 35 ms + (45 − 20) ms delivery = 60 ms | 60.0 ms |
| Clock basis detection | shared / separate | correct both ways |
| Axis mapping (image x ← yaw, image y ← pitch; nodding up slides the scene down, +y) | +343.8 px/rad | +343.3 – 344.0 / +343.9 px/rad |
| Residual discrepancy | 2.0 °/s | 1.94 – 2.07 °/s |
| Phase lag at the paced frequency | = lag | 35.1 – 36.0 ms (shared), 60.2 ms (separate) |
| Gain vs. V4 calibration | 1.000 | 1.000 – 1.003 |

Native head motion is generated as an orientation trajectory, and the
gyroscope, accelerometer, fused quaternion and magnetometer are all derived
from it exactly, so they can be checked against each other. Native pose
(`pose_analysis.py`), on the A1/B1–B3 native sessions:

| Quantity | Injected | Recovered |
|---|---|---|
| Gyro axes (gravity consistency) | identity: `gx, gy, gz` → `rrAlpha, rrBeta, rrGamma` | adopted, residual 0.07 m/s² vs 1.01 for the best alternative |
| Fused quaternion convention (12 candidates) | body→world, up = +Y | identified, 0.09 m/s² vs 2.13 for the next best |
| Fused heading vs gyro yaw | tracks: gain +1, r +1 | +1.00 / +1.00 |
| Magnetometer heading vs gyro yaw | tracks; 48 µT field | gain +1.00 – +1.01, r +0.88 – +1.00; 48.0 µT |
| Yaw (`rrBeta`) turn-on bias | −0.0344 °/s | −0.0328 °/s (within one standard error) |

Balance and vestibular tasks (`balance_analysis.py`, Tier S): head sway as an
inverted pendulum (1.5 m) with a gyroscope bias, one hold lost at 9.2 s with a
sideways lurch before the press, ten minimum-jerk head impulses (one slow), and
reading at a near target that moves the image 1.25 times as far as head
rotation:

| Quantity | Injected | Recovered |
|---|---|---|
| AP / ML sway RMS, full holds (9) | 0.20 – 1.53° | within −1.8% to +3.2% |
| 95% sway area, full holds | 1.2 – 22.7 °² | within −2.1% to +3.1% |
| Sway acceleration RMS | 0.10 – 0.51 m/s² | within −0.3% to +0.8% |
| Lost hold (6.2 s window) | AP 1.45°, ML 1.63°, 43.8 °² | −5.3% / +5.5% / −0.1% |
| Hold time, balance-lost flag | 9.2 s, lost | 9.2 s, lost |
| Head impulses: count, sides, slow ones | 10, 5 L / 5 R, 1 | 10, 5 L / 5 R, 1 |
| Impulse peak velocity, amplitude | 131 – 274 °/s, 14 – 20° | −2.2% to 0% (60 Hz sampling), −1.1% to 0% |
| Gaze stabilization: frequency, amplitude | 1.00 Hz; ±12° / ±10° | +0.1 – 0.2%; −0.7 – −0.3% |
| Camera ÷ head at the far-scene (V4) scale | 1.25 | 1.252 – 1.254 |

The same sway, estimated with the pose module's attitude filter instead, came
out 7–28% high on every hold: its accelerometer correction reads a standing
body's sway acceleration as tilt. Hence `balance.py` integrates the gyroscope.

Synthetic sessions are stamped `participant: SYNTHETIC` and `synthetic: true`.
**They are not measurements and must never be cited as results.**

---

## Module layout

| Module | Responsibility |
|---|---|
| `visor_imu/loader.py` | Read `.json.gz` sessions (`/1` and `/2`); preserve `null` as `NaN`; derive `devicemotion` from native samples (same channel names, same body frame) |
| `visor_imu/metrics.py` | Timing/jitter, static bias and noise, Allan deviation, PSD, saturation |
| `visor_imu/rehab.py` | Stillness/dwell, compensatory scanning, gait, cue-aligned segmentation |
| `visor_imu/vor.py` | Head rotation vs. camera image shift: clock check, lag, axis mapping, scale, residual discrepancy, paced-frequency phase |
| `visor_imu/report.py` | Figures and markdown rendering |
| `visor_imu/pose.py` | Axis identification, Mahony attitude filter, gravity removal, ZUPT, step dead reckoning; native heading references (fused-quaternion convention by gravity, raw-magnetometer compass) |
| `visor_imu/pose_report.py` | Pose-analysis figures |
| `pose_analysis.py` | CLI: 6-DOF pose analysis, figures and report; web-vs-native comparison |
| `visor_imu/balance.py` | Tier S: hold time, head sway (angle, area, velocity, acceleration), head impulses, gaze-stabilization movement |
| `visor_imu/balance_report.py` | Tier S figures: one per task, plus an overview |
| `balance_analysis.py` | CLI: balance and vestibular tasks, figures and report |
| `analyze.py` | CLI: dispatch analyses per trial, emit report |
| `fetch_sessions.py` | Download sessions from the private Blob store |
| `make_synthetic.py` | Ground-truth data for pipeline validation |

---

## Design rules

Two rules the code enforces, both of which exist because breaking them would
silently corrupt results:

1. **`null` is never zero.** A reading the platform did not supply is carried as
   `NaN` end to end and reported as an all-null column. Filling it with `0`
   fabricates a measurement — and did exactly that in the first readout build,
   making "no orientation sensor" indistinguishable from "orientation reads
   zero".
2. **Static statistics only on static trials.** Bias, noise and Allan deviation
   are computed only for trials in `STATIC_TRIALS`. Running them on a worn or
   moving recording produces confident, meaningless numbers.
3. **No lag or scale without a fit.** An IMU-versus-image fit whose head
   rotation explains under half the image motion (R² < 0.5) is withheld from the
   report — the numbers would describe noise. The phone's quick look applies the
   same threshold.
4. **Not a VOR gain.** A head-fixed camera measures the image motion the head
   causes, not the eyes' response to it. `vor.py` reports the IMU/image
   discrepancy and its timing; a VOR gain additionally needs eye movement, and
   nothing here may be reported as one.
