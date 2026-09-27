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

# Axes follow the glasses' body frame as measured, not the W3C phone frame:
# x = wearer's right, y = up, z = backward. Gravity therefore sits on agy, and
# the gyro channels carry rrAlpha = pitch, rrBeta = yaw, rrGamma = roll (see
# visor_imu.metrics.GYRO_HEAD_AXIS). Random draws are kept in a fixed order so
# the noise realisation does not depend on which axis a signal is placed on.

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
        rng.normal(GRAVITY + bias[1], TRUTH["accel_noise_ms2"], n),
        rng.normal(bias[2], TRUTH["accel_noise_ms2"], n),
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
    pitch_rate = rng.normal(0, TRUTH["gyro_noise_dps"], n)
    roll_rate = rng.normal(0, TRUTH["gyro_noise_dps"], n)
    gyro = np.array([pitch_rate, yaw_rate, roll_rate])
    accel = np.array([rng.normal(0, 0.15, n), rng.normal(GRAVITY, 0.15, n),
                      rng.normal(0, 0.15, n)])

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
    accel = np.array([rng.normal(0.1, 0.05, n), rng.normal(GRAVITY, 0.05, n),
                      rng.normal(-0.05, 0.05, n)])
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
    yaw_rate = yaw + rng.normal(0, 0.5, n)
    pitch_rate = rng.normal(0, 0.5, n)
    roll_rate = rng.normal(0, 0.5, n)
    gyro = np.array([pitch_rate, yaw_rate, roll_rate])
    accel = np.array([rng.normal(0, 0.2, n), rng.normal(GRAVITY, 0.2, n),
                      rng.normal(0, 0.2, n)])
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
    lateral = rng.normal(0, 0.3, n) + 0.4 * np.sin(2 * np.pi * step * ts + 0.7)
    fore_aft = rng.normal(0, 0.3, n)
    vertical = GRAVITY + bob + rng.normal(0, 0.25, n)
    accel = np.array([lateral, vertical, fore_aft])
    # Head yaw sways once per stride (half the step rate); pitch nods every step.
    yaw_rate = 6.0 * np.sin(2 * np.pi * step * ts * 0.5) + rng.normal(0, 1.5, n)
    pitch_rate = 8.0 * np.sin(2 * np.pi * step * ts) + rng.normal(0, 1.5, n)
    roll_rate = rng.normal(0, 1.5, n)
    gyro = np.array([pitch_rate, yaw_rate, roll_rate])
    marks = [{"t": 0.0, "label": "trial_start", "extra": {"trialId": "C3_walk_straight"}},
             {"t": float(t[-1]), "label": "trial_end", "extra": {"outcome": "completed"}}]
    return _session("C3_walk_straight", "Walking gait", "C",
                    "Step cadence from head-mounted IMU.", t, gyro, accel, marks, fs)


# ============================ native (MWDAT) sessions ============================
#
# visor.imu.session/2, as the Android head-motion lab writes it: raw MWDAT
# MotionSamples plus per-frame image shifts. The camera sees the head's own
# rotation through a known scale and latency, so vor.py's recovery of lag,
# scale, axis mapping and residual can be checked against the truth.

NATIVE_TRUTH = {
    "motion_rate_hz": 60.0,
    "video_fps": 30.0,
    "scale_px_per_deg": 6.0,
    # Image content of a frame stamped t shows the head as it was at t - latency.
    "sensor_to_image_latency_ms": 35.0,
    # Turning left (+yaw about +Y) slides the scene right (+x); nodding up
    # (+pitch about +X) slides it down (+y is down the image).
    "axis_mapping": "image x = +scale*yaw rate (glasses Y); image y = -scale*pitch rate (glasses X)",
    "image_noise_px_sd": 0.4,
    "motion_delivery_ms": [20, 60],
    "video_delivery_ms": [45, 95],
    "blurred_frame_fraction": 0.03,
    "skipped_analysis_fraction": 0.02,
    # With a shared clock the lag is measured directly; with separate clocks it
    # is measured on the phone clock, offset by min video delay - min motion delay.
    "expected_lag_ms_shared_clock": 35.0,
    "expected_lag_ms_separate_clocks": 35.0 + 45 - 20,
    # Image-shift noise of 0.4 px per frame at 30 fps, over 6 px/deg.
    "expected_residual_dps": 0.4 * 30 / 6.0,
}


def _quat_yaw_pitch(yaw: np.ndarray, pitch: np.ndarray) -> np.ndarray:
    """(w, x, y, z) for a yaw about +Y followed by a pitch about +X."""
    cy, sy = np.cos(yaw / 2), np.sin(yaw / 2)
    cp, sp = np.cos(pitch / 2), np.sin(pitch / 2)
    # q_yaw = (cy, 0, sy, 0); q_pitch = (cp, sp, 0, 0); q = q_yaw * q_pitch
    return np.array([cy * cp, cy * sp, sy * cp, -sy * sp])


def native_session(rng, trial_id: str, title: str, purpose: str, duration_s: float,
                   yaw_deg, pitch_deg, cue: dict | None, shared_clock: bool) -> dict:
    """One synthetic head-motion lab trial with a known camera model."""
    truth = NATIVE_TRUTH
    fs, fps = truth["motion_rate_hz"], truth["video_fps"]
    scale = truth["scale_px_per_deg"]
    latency = truth["sensor_to_image_latency_ms"] / 1000.0
    rad = np.pi / 180

    # --- motion: sampled on the glasses clock, with ~0.4 ms timing jitter ----
    t_m = np.arange(0.0, duration_s, 1.0 / fs)
    t_m = np.sort(t_m + rng.normal(0, 0.0004, t_m.size))
    h = 1e-4
    yaw = yaw_deg(t_m) * rad
    pitch = pitch_deg(t_m) * rad
    yaw_rate = (yaw_deg(t_m + h) - yaw_deg(t_m - h)) / (2 * h) * rad
    pitch_rate = (pitch_deg(t_m + h) - pitch_deg(t_m - h)) / (2 * h) * rad
    n = t_m.size
    gyro = np.array([pitch_rate + rng.normal(0, 0.003, n),
                     yaw_rate + rng.normal(0, 0.003, n),
                     rng.normal(0, 0.003, n)])
    accel = np.array([rng.normal(0, 0.05, n),
                      GRAVITY * np.cos(pitch) + rng.normal(0, 0.05, n),
                      -GRAVITY * np.sin(pitch) + rng.normal(0, 0.05, n)])
    quat = _quat_yaw_pitch(yaw, pitch)
    m_delay = rng.uniform(*truth["motion_delivery_ms"], n)
    motion_origin_ns = 5_000_000_000_000  # ~83 min of glasses uptime
    t_device_ms = t_m * 1000.0

    # --- video: frames stamped on either the same clock or their own --------
    t_f = np.arange(0.0, duration_s, 1.0 / fps)
    nf = t_f.size
    content_t = t_f - latency
    yaw_img = yaw_deg(content_t)
    pitch_img = pitch_deg(content_t)
    v_delay = rng.uniform(*truth["video_delivery_ms"], nf)
    shift_x = np.full(nf, np.nan)
    shift_y = np.full(nf, np.nan)
    peak = np.full(nf, np.nan)
    texture = np.full(nf, np.nan)
    ref = np.full(nf, np.nan)
    last = 0
    for k in range(1, nf):
        if rng.random() < truth["skipped_analysis_fraction"]:
            continue  # recorded, not analyzed; the next frame spans the gap
        shift_x[k] = scale * (yaw_img[k] - yaw_img[last]) + rng.normal(0, truth["image_noise_px_sd"])
        shift_y[k] = -scale * (pitch_img[k] - pitch_img[last]) + rng.normal(0, truth["image_noise_px_sd"])
        peak[k] = rng.uniform(0.35, 0.9)
        texture[k] = rng.uniform(18, 40)
        if rng.random() < truth["blurred_frame_fraction"]:
            peak[k] = rng.uniform(0.02, 0.1)  # below the gate: must be ignored
            shift_x[k] += rng.normal(0, 25)
        ref[k] = last
        last = k

    # Phone clock: recording starts at t = 0 on both streams' true time.
    t_phone_m = t_device_ms + m_delay
    t_phone_v = t_f * 1000.0 + v_delay
    if shared_clock:
        video_origin_us = motion_origin_ns // 1000  # same monotonic clock
    else:
        video_origin_us = 123_456  # a media clock of its own

    marks = [{"t": 0.0, "label": "trial_start", "extra": {"trialId": trial_id}}]
    if cue:
        for i in range(int(duration_s * 1000 / cue["intervalMs"])):
            marks.append({"t": i * cue["intervalMs"], "label": "cue",
                          "extra": {"index": i, "label": cue["steps"][i % 2]}})
    marks.append({"t": duration_s * 1000.0, "label": "trial_end",
                  "extra": {"outcome": "completed", "reason": None}})

    def col(a, p=6):
        return _round(np.asarray(a, dtype="float64"), p)

    nan_m = np.full(n, np.nan)
    return {
        "schema": "visor.imu.session/2",
        "startedAt": "2026-09-25T12:00:00.000Z",
        "t0Epoch": 1790340000000,
        "durationMs": duration_s * 1000.0,
        "meta": {
            "sessionId": f"SYNTH_{trial_id}_{'shared' if shared_clock else 'separate'}",
            "participant": "SYNTHETIC",
            "trialId": trial_id,
            "trialTitle": title,
            "tier": "V",
            "worn": True,
            "purpose": purpose,
            "camera": True,
            "cue": cue,
            "platform": "android-mwdat",
            "sdk": {"name": "Meta Wearables Device Access Toolkit", "version": "1.0.0"},
            "glassesAtStart": {"model": "Ray-Ban Meta"},
            "outcome": "completed",
            "synthetic": True,
            "consent": "synthetic-not-human-data",
            "groundTruth": {**truth, "shared_clock": shared_clock},
            "clocks": {
                "phoneClock": "SystemClock.elapsedRealtimeNanos",
                "phoneOriginNs": "900000000000",
                "motionDeviceOriginNs": str(motion_origin_ns),
                "videoPtsOriginUs": str(video_origin_us),
            },
            "motionSourceCodes": {"0": "GLASSES", "1": "NEURAL_BAND", "2": "UNKNOWN"},
        },
        "marks": marks,
        "streams": {
            "dat_motion": {
                "n": n,
                "nullCounts": {"mx": n, "my": n, "mz": n},
                "columns": {
                    "tPhone": col(t_phone_m, 4), "tDevice": col(t_device_ms, 4),
                    "ax": col(accel[0]), "ay": col(accel[1]), "az": col(accel[2]),
                    "gx": col(gyro[0]), "gy": col(gyro[1]), "gz": col(gyro[2]),
                    "mx": col(nan_m), "my": col(nan_m), "mz": col(nan_m),
                    "qw": col(quat[0]), "qx": col(quat[1]), "qy": col(quat[2]), "qz": col(quat[3]),
                    "source": col(np.zeros(n), 0),
                },
            },
            "dat_video": {
                "n": nf,
                "nullCounts": {"shiftX": int(np.isnan(shift_x).sum())},
                "columns": {
                    "tPhone": col(t_phone_v, 4), "tPts": col(t_f * 1000.0, 4),
                    "width": col(np.full(nf, 360), 0), "height": col(np.full(nf, 640), 0),
                    "shiftX": col(shift_x, 4), "shiftY": col(shift_y, 4),
                    "peak": col(peak, 4), "textureSd": col(texture, 3),
                    "refIndex": col(ref, 0), "analysisMs": col(np.full(nf, 8.0), 3),
                },
            },
        },
    }


def vor_yaw(rng, shared_clock=True) -> dict:
    return native_session(
        rng, "V1_vor_yaw_paced", "Side-to-side head turns, eyes on a target",
        "Yaw rotation vs. image shift at a paced 1 Hz.", 45.0,
        yaw_deg=lambda t: 15.0 * np.sin(2 * np.pi * 1.0 * t),
        pitch_deg=lambda t: 1.0 * np.sin(2 * np.pi * 0.2 * t),
        cue={"intervalMs": 500, "steps": ["LEFT", "RIGHT"], "style": "METRONOME"},
        shared_clock=shared_clock)


def vor_pitch(rng) -> dict:
    return native_session(
        rng, "V2_vor_pitch_paced", "Up-and-down nods, eyes on a target",
        "Pitch rotation vs. vertical image shift at 1 Hz.", 45.0,
        yaw_deg=lambda t: 1.0 * np.sin(2 * np.pi * 0.15 * t),
        pitch_deg=lambda t: 10.0 * np.sin(2 * np.pi * 1.0 * t),
        cue={"intervalMs": 500, "steps": ["UP", "DOWN"], "style": "METRONOME"},
        shared_clock=True)


def slow_pan(rng) -> dict:
    return native_session(
        rng, "V4_slow_pan", "Slow look-around (camera calibration)",
        "Slow yaw for pixels-per-degree calibration.", 45.0,
        yaw_deg=lambda t: 30.0 * np.sin(2 * np.pi * t / 6.0),
        pitch_deg=lambda t: 1.5 * np.sin(2 * np.pi * 0.1 * t),
        cue={"intervalMs": 3000, "steps": ["LEFT", "RIGHT"], "style": "SPOKEN"},
        shared_clock=True)


BUILDERS = {
    "A1_static_rest": static_rest,
    "B1_yaw_paced": yaw_paced,
    "C1_stillness_hold": stillness_hold,
    "C2_scanning_pattern": scanning,
    "C3_walk_straight": walking,
}

# Native builders, keyed by the file name they write.
NATIVE_BUILDERS = {
    "V1_vor_yaw_paced_shared": lambda rng: vor_yaw(rng, shared_clock=True),
    "V1_vor_yaw_paced_separate": lambda rng: vor_yaw(rng, shared_clock=False),
    "V2_vor_pitch_paced": vor_pitch,
    "V4_slow_pan": slow_pan,
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

    print(f"Native ground truth: {json.dumps(NATIVE_TRUTH)}")
    for name, build in NATIVE_BUILDERS.items():
        session = build(rng)
        trial_id = session["meta"]["trialId"]
        dest = out_root / "SYNTHETIC" / trial_id / f"SYNTH_{name}.json.gz"
        dest.parent.mkdir(parents=True, exist_ok=True)
        with gzip.open(dest, "wt", encoding="utf-8") as fh:
            json.dump(session, fh)
        print(f"  + {dest.relative_to(out_root)}  "
              f"({dest.stat().st_size/1024:.0f} KB, {session['streams']['dat_motion']['n']} motion samples, "
              f"{session['streams']['dat_video']['n']} frames)")

    print(f"\nWrote {len(BUILDERS) + len(NATIVE_BUILDERS)} synthetic sessions -> {out_root}")
    print("These are NOT measurements. Never cite them as results.")


if __name__ == "__main__":
    main()
