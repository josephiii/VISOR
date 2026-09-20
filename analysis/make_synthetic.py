#!/usr/bin/env python3
"""Generate synthetic IMU sessions for pipeline validation.

These are NOT measurements. Every session produced here is stamped with
participant ``SYNTHETIC`` and ``"synthetic": true`` in its metadata so it can
never be mistaken for recorded data in a report. The purpose is to exercise the
analysis end to end — and to check derived measures against known ground truth,
since the generator knows the cadence, sweep amplitude and noise it injected.

Usage:
    python analysis/make_synthetic.py --out data/imu-sessions-synthetic
"""

from __future__ import annotations

import argparse
import gzip
import json
from pathlib import Path

import numpy as np

REPO_ROOT = Path(__file__).resolve().parent.parent
GRAVITY = 9.80665

# Ground truth injected by the generator; the pipeline should recover these.
TRUTH = {
    "sample_rate_hz": 60.0,
    "gyro_noise_dps": 0.35,
    "accel_noise_ms2": 0.02,
    "accel_bias_ms2": (0.03, -0.02, 0.05),
    "gait_step_hz": 1.8,
    "scan_peak_dps": 95.0,
}


def _timebase(duration_s: float, fs: float, rng: np.random.Generator,
              dropout_prob: float = 0.001) -> np.ndarray:
    """Sample times in ms with realistic jitter and occasional dropped frames."""
    n = int(duration_s * fs)
    nominal = 1000.0 / fs
    jitter = rng.normal(0.0, nominal * 0.06, n)
    intervals = np.clip(nominal + jitter, nominal * 0.4, nominal * 2.0)
    # A dropped frame shows up as one interval of roughly double length.
    drops = rng.random(n) < dropout_prob
    intervals[drops] *= 3.0
    return np.cumsum(intervals) - intervals[0]


def _round(arr: np.ndarray, p: int = 6) -> list:
    return [None if not np.isfinite(v) else round(float(v), p) for v in arr]


def _session(trial_id: str, title: str, tier: str, purpose: str,
             t_ms: np.ndarray, gyro: np.ndarray, accel: np.ndarray,
             marks: list[dict], fs: float) -> dict:
    n = t_ms.size
    nan = np.full(n, np.nan)
    return {
        "schema": "visor.imu.session/1",
        "startedAt": "2026-09-19T12:00:00.000Z",
        "t0Epoch": 1789800000000,
        "durationMs": float(t_ms[-1]),
        "meta": {
            "sessionId": f"SYNTH_{trial_id}",
            "participant": "SYNTHETIC",
            "trialId": trial_id,
            "trialTitle": title,
            "tier": tier,
            "purpose": purpose,
            "synthetic": True,
            "consent": "synthetic-not-human-data",
            "groundTruth": TRUTH,
        },
        "marks": marks,
        "streams": {
            "devicemotion": {
                "n": n,
                # Linear acceleration is modelled as unavailable, matching a
                # platform that only exposes the gravity-inclusive vector.
                "nullCounts": {"ax": n, "ay": n, "az": n},
                "columns": {
                    "tPerf": _round(t_ms, 3),
                    "tEvent": _round(t_ms + 0.25, 3),
                    "ax": _round(nan), "ay": _round(nan), "az": _round(nan),
                    "agx": _round(accel[0]), "agy": _round(accel[1]), "agz": _round(accel[2]),
                    "rrAlpha": _round(gyro[0]), "rrBeta": _round(gyro[1]),
                    "rrGamma": _round(gyro[2]),
                    "interval": _round(np.full(n, 1000.0 / fs), 3),
                },
            },
            # Modelled as never firing — the behaviour observed on-device.
            "deviceorientation": {"n": 0, "nullCounts": {}, "columns": {}},
            "deviceorientationabsolute": {"n": 0, "nullCounts": {}, "columns": {}},
        },
    }


def static_rest(rng, duration_s=120.0, fs=60.0) -> dict:
    t = _timebase(duration_s, fs, rng)
    n = t.size
    bias = TRUTH["accel_bias_ms2"]
    accel = np.array([
        rng.normal(bias[0], TRUTH["accel_noise_ms2"], n),
        rng.normal(bias[1], TRUTH["accel_noise_ms2"], n),
        rng.normal(GRAVITY + bias[2], TRUTH["accel_noise_ms2"], n),
    ])
    # White noise plus a slow bias drift, so the Allan curve has a real minimum.
    drift = np.cumsum(rng.normal(0, 0.0008, n))
    gyro = np.array([
        rng.normal(0.02, TRUTH["gyro_noise_dps"], n) + drift,
        rng.normal(-0.01, TRUTH["gyro_noise_dps"], n) + drift * 0.7,
        rng.normal(0.00, TRUTH["gyro_noise_dps"], n) + drift * 0.5,
    ])
    marks = [{"t": 0.0, "label": "trial_start", "extra": {"trialId": "A1_static_rest"}},
             {"t": float(t[-1]), "label": "trial_end", "extra": {"outcome": "completed"}}]
    return _session("A1_static_rest", "Static rest", "A",
                    "Noise floor, bias, Allan deviation.", t, gyro, accel, marks, fs)


def yaw_paced(rng, duration_s=60.0, fs=60.0, interval_s=2.0) -> dict:
    t = _timebase(duration_s, fs, rng)
    ts = t / 1000.0
    n = t.size
    # Square-ish alternating yaw, smoothed into realistic turn profiles.
    phase = np.sin(2 * np.pi * ts / (2 * interval_s))
    yaw_rate = 80.0 * phase + rng.normal(0, TRUTH["gyro_noise_dps"], n)
    gyro = np.array([yaw_rate,
                     rng.normal(0, TRUTH["gyro_noise_dps"], n),
                     rng.normal(0, TRUTH["gyro_noise_dps"], n)])
    accel = np.array([rng.normal(0, 0.15, n), rng.normal(0, 0.15, n),
                      rng.normal(GRAVITY, 0.15, n)])

    marks = [{"t": 0.0, "label": "trial_start", "extra": {"trialId": "B1_yaw_paced"}}]
    for i in range(int(duration_s / interval_s)):
        marks.append({"t": i * interval_s * 1000.0, "label": "cue",
                      "extra": {"index": i, "label": "LEFT" if i % 2 == 0 else "RIGHT"}})
    marks.append({"t": float(t[-1]), "label": "trial_end", "extra": {"outcome": "completed"}})
    return _session("B1_yaw_paced", "Yaw sweeps (paced)", "B",
                    "Yaw-axis response and repeatability.", t, gyro, accel, marks, fs)


def stillness_hold(rng, duration_s=60.0, fs=60.0) -> dict:
    t = _timebase(duration_s, fs, rng)
    n = t.size
    # Worn-but-still: micro-tremor plus occasional small drifts.
    tremor = rng.normal(0, 0.9, (3, n))
    slow = np.array([np.sin(2 * np.pi * t / 1000.0 / 11) * 0.6,
                     np.sin(2 * np.pi * t / 1000.0 / 7) * 0.4,
                     np.sin(2 * np.pi * t / 1000.0 / 13) * 0.3])
    gyro = tremor + slow
    accel = np.array([rng.normal(0.1, 0.05, n), rng.normal(-0.05, 0.05, n),
                      rng.normal(GRAVITY, 0.05, n)])
    marks = [{"t": 0.0, "label": "trial_start", "extra": {"trialId": "C1_stillness_hold"}},
             {"t": float(t[-1]), "label": "trial_end", "extra": {"outcome": "completed"}}]
    return _session("C1_stillness_hold", "Stillness hold (worn)", "C",
                    "Dwell threshold for capture gating.", t, gyro, accel, marks, fs)


def scanning(rng, duration_s=90.0, fs=60.0, interval_s=3.0) -> dict:
    t = _timebase(duration_s, fs, rng)
    ts = t / 1000.0
    n = t.size
    # Deliberate asymmetry: rightward sweeps are weaker, as in a left-field loss.
    base = np.sin(2 * np.pi * ts / (2 * interval_s))
    yaw = np.where(base > 0, base * TRUTH["scan_peak_dps"], base * TRUTH["scan_peak_dps"] * 0.6)
    gyro = np.array([yaw + rng.normal(0, 0.5, n),
                     rng.normal(0, 0.5, n), rng.normal(0, 0.5, n)])
    accel = np.array([rng.normal(0, 0.2, n), rng.normal(0, 0.2, n),
                      rng.normal(GRAVITY, 0.2, n)])
    marks = [{"t": 0.0, "label": "trial_start", "extra": {"trialId": "C2_scanning_pattern"}}]
    for i in range(int(duration_s / interval_s)):
        marks.append({"t": i * interval_s * 1000.0, "label": "cue",
                      "extra": {"index": i, "label": "SCAN LEFT" if i % 2 == 0 else "SCAN RIGHT"}})
    marks.append({"t": float(t[-1]), "label": "trial_end", "extra": {"outcome": "completed"}})
    return _session("C2_scanning_pattern", "Compensatory scanning", "C",
                    "Scan amplitude, rate and symmetry.", t, gyro, accel, marks, fs)


def walking(rng, duration_s=60.0, fs=60.0) -> dict:
    t = _timebase(duration_s, fs, rng)
    ts = t / 1000.0
    n = t.size
    step = TRUTH["gait_step_hz"]
    bob = 1.6 * np.sin(2 * np.pi * step * ts) + 0.5 * np.sin(2 * np.pi * 2 * step * ts)
    accel = np.array([
        rng.normal(0, 0.3, n) + 0.4 * np.sin(2 * np.pi * step * ts + 0.7),
        rng.normal(0, 0.3, n),
        GRAVITY + bob + rng.normal(0, 0.25, n),
    ])
    gyro = np.array([
        6.0 * np.sin(2 * np.pi * step * ts * 0.5) + rng.normal(0, 1.5, n),
        8.0 * np.sin(2 * np.pi * step * ts) + rng.normal(0, 1.5, n),
        rng.normal(0, 1.5, n),
    ])
    marks = [{"t": 0.0, "label": "trial_start", "extra": {"trialId": "C3_walk_straight"}},
             {"t": float(t[-1]), "label": "trial_end", "extra": {"outcome": "completed"}}]
    return _session("C3_walk_straight", "Walking gait", "C",
                    "Step cadence from head-mounted IMU.", t, gyro, accel, marks, fs)


BUILDERS = {
    "A1_static_rest": static_rest,
    "B1_yaw_paced": yaw_paced,
    "C1_stillness_hold": stillness_hold,
    "C2_scanning_pattern": scanning,
    "C3_walk_straight": walking,
}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", default=str(REPO_ROOT / "data" / "imu-sessions-synthetic"))
    parser.add_argument("--seed", type=int, default=20260919)
    args = parser.parse_args()

    out_root = Path(args.out)
    rng = np.random.default_rng(args.seed)

    print(f"Ground truth: {json.dumps(TRUTH)}")
    for trial_id, build in BUILDERS.items():
        session = build(rng)
        dest = out_root / "SYNTHETIC" / trial_id / f"SYNTH_{trial_id}.json.gz"
        dest.parent.mkdir(parents=True, exist_ok=True)
        with gzip.open(dest, "wt", encoding="utf-8") as fh:
            json.dump(session, fh)
        print(f"  + {dest.relative_to(out_root)}  "
              f"({dest.stat().st_size/1024:.0f} KB, {session['streams']['devicemotion']['n']} samples)")

    print(f"\nWrote {len(BUILDERS)} synthetic sessions -> {out_root}")
    print("These are NOT measurements. Never cite them as results.")


if __name__ == "__main__":
    main()
