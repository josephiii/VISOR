# VISOR IMU Analysis Toolkit

Analysis pipeline for inertial data captured from Meta Ray-Ban Display glasses,
by either of two capture paths:

| Path | Writes | Adds |
|---|---|---|
| **IMU Lab** web app (`frontend/visor-webapp`), Web Apps DeviceMotion API | `visor.imu.session/1` | — |
| **Head-motion lab** in the Android app (`frontend/visor`, Settings → Research), MWDAT 1.0 Motion | `visor.imu.session/2` | Glasses-clock sample times, gyroscope in rad/s, fused quaternion, motion source; for Tier V trials, the camera's per-frame **image shift** |

Both load through `visor_imu/loader.py`. `/2` sessions also get a derived
`devicemotion` frame in the `/1` vocabulary, so every existing analysis runs on
them unchanged; `visor_imu/vor.py` adds the one only `/2` can support: head
rotation against the head-fixed camera's image shift. See
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
#    zero-velocity updates, step dead reckoning)
python analysis/pose_analysis.py
```

Outputs:

| Path | Contents |
|---|---|
| `documentation/testing/imu-characterization-report.md` | Generated results report |
| `documentation/assets/imu/*.png` | Figures referenced by the report |
| `data/imu-analysis/summary.json` | Machine-readable results for every session |
| `documentation/testing/imu-pose-tracking-report.md` | Generated 6-DOF pose report (`pose_analysis.py`) |
| `documentation/assets/imu/pose/*.png` | Figures referenced by the pose report |
| `data/imu-analysis/pose-summary.json` | Machine-readable pose results |
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
| Image scale | 6.00 px/° | 5.99 – 6.01 px/° |
| Lag, shared clock (device-clock basis) | 35 ms | 35 / 35 / 36 ms |
| Lag, separate clocks (arrival basis) | 35 ms + (45 − 20) ms delivery = 60 ms | 60.0 ms |
| Clock basis detection | shared / separate | correct both ways |
| Axis mapping (image x ← yaw, image y ← −pitch) | ±343.8 px/rad | +343.4 / −344.6 px/rad |
| Residual discrepancy | 2.0 °/s | 1.94 – 2.07 °/s |
| Phase lag at the paced frequency | = lag | 34.9 – 36.0 ms (shared), 60.2 ms (separate) |
| Gain vs. V4 calibration | 1.000 | 0.999 – 1.003 |

Synthetic sessions are stamped `participant: SYNTHETIC` and `synthetic: true`.
**They are not measurements and must never be cited as results.**

---

## Module layout

| Module | Responsibility |
|---|---|
| `visor_imu/loader.py` | Read `.json.gz` sessions (`/1` and `/2`); preserve `null` as `NaN`; derive `devicemotion` from native samples |
| `visor_imu/metrics.py` | Timing/jitter, static bias and noise, Allan deviation, PSD, saturation |
| `visor_imu/rehab.py` | Stillness/dwell, compensatory scanning, gait, cue-aligned segmentation |
| `visor_imu/vor.py` | Head rotation vs. camera image shift: clock check, lag, axis mapping, scale, residual discrepancy, paced-frequency phase |
| `visor_imu/report.py` | Figures and markdown rendering |
| `visor_imu/pose.py` | Axis identification, Mahony attitude filter, gravity removal, ZUPT, step dead reckoning |
| `visor_imu/pose_report.py` | Pose-analysis figures |
| `pose_analysis.py` | CLI: 6-DOF pose analysis, figures and report |
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
