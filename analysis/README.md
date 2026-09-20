# VISOR IMU Analysis Toolkit

Analysis pipeline for inertial data captured from Meta Ray-Ban Display glasses
by the on-glasses **IMU Lab** web app (`frontend/visor-webapp`).

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
```

Outputs:

| Path | Contents |
|---|---|
| `documentation/testing/imu-characterization-report.md` | Generated results report |
| `documentation/assets/imu/*.png` | Figures referenced by the report |
| `data/imu-analysis/summary.json` | Machine-readable results for every session |
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

Synthetic sessions are stamped `participant: SYNTHETIC` and `synthetic: true`.
**They are not measurements and must never be cited as results.**

---

## Module layout

| Module | Responsibility |
|---|---|
| `visor_imu/loader.py` | Read `.json.gz` sessions; preserve `null` as `NaN` |
| `visor_imu/metrics.py` | Timing/jitter, static bias and noise, Allan deviation, PSD, saturation |
| `visor_imu/rehab.py` | Stillness/dwell, compensatory scanning, gait, cue-aligned segmentation |
| `visor_imu/report.py` | Figures and markdown rendering |
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
