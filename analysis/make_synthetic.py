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
# MotionSamples, plus per-frame image shifts on camera trials. Head motion is an
# orientation trajectory (yaw about world up, then pitch about the head's x,
# then roll about its z), and the gyroscope, accelerometer, fused quaternion and
# magnetometer are all derived from it exactly. So the pose analysis can check
# them against each other, and vor.py (lag, scale, axis mapping, residual) and
# balance.py (sway, hold time, impulses) can be checked against the truth.

NATIVE_TRUTH = {
    "motion_rate_hz": 60.0,
    "video_fps": 30.0,
    "scale_px_per_deg": 6.0,
    # Image content of a frame stamped t shows the head as it was at t - latency.
    "sensor_to_image_latency_ms": 35.0,
    # Turning left (+yaw about +Y) slides the scene right (+x); nodding up
    # (+pitch about +X) slides it down (+y is down the image).
    "axis_mapping": "image x = +scale*yaw rate (glasses Y); image y = +scale*pitch rate (glasses X)",
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
    # The fused quaternion maps body to world, whose up is +Y (the frame the
    # head starts in): one of the twelve readings pose.quaternion_convention tests.
    "quaternion_convention": "body→world, up = +Y",
    "magnetic_field_ut": 48.0,
    "magnetic_dip_deg": 60.0,
}

# The generator's world is the head's starting frame (x right, y up, z backward)
# with magnetic north straight ahead; Earth's field dips 60° into the ground.
_FIELD_WORLD_UT = 48.0 * np.array([0.0, -np.sin(np.radians(60)), -np.cos(np.radians(60))])

# Ellipse holding 95% of sway samples: chi-square, 2 degrees of freedom.
CHI2_95_2DOF = -2.0 * np.log(0.05)


def _qmul(a: np.ndarray, b: np.ndarray) -> np.ndarray:
    """Hamilton product of (..., 4) quaternion arrays, w first."""
    w1, x1, y1, z1 = np.moveaxis(a, -1, 0)
    w2, x2, y2, z2 = np.moveaxis(b, -1, 0)
    return np.stack([w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2,
                     w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
                     w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2,
                     w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2], axis=-1)


def _qconj(q: np.ndarray) -> np.ndarray:
    return q * np.array([1.0, -1.0, -1.0, -1.0])


def _about(axis: int, angle_rad: np.ndarray) -> np.ndarray:
    q = np.zeros(np.shape(angle_rad) + (4,))
    q[..., 0] = np.cos(angle_rad / 2)
    q[..., 1 + axis] = np.sin(angle_rad / 2)
    return q


def _to_body(q: np.ndarray, v_world: np.ndarray) -> np.ndarray:
    """World vectors in the body frame of body→world quaternions ``q``."""
    v = np.broadcast_to(v_world, q.shape[:-1] + (3,))
    vq = np.concatenate([np.zeros(v.shape[:-1] + (1,)), v], axis=-1)
    return _qmul(_qmul(_qconj(q), vq), q)[..., 1:]


def _orientation(t, yaw_deg, pitch_deg, roll_deg) -> np.ndarray:
    rad = np.pi / 180
    return _qmul(_qmul(_about(1, yaw_deg(t) * rad), _about(0, pitch_deg(t) * rad)),
                 _about(2, roll_deg(t) * rad))


def _body_rates(t, yaw_deg, pitch_deg, roll_deg, h: float = 1e-4) -> np.ndarray:
    """Body-frame angular velocity (rad/s), from q̇ = ½ q ⊗ (0, ω)."""
    q = _orientation(t, yaw_deg, pitch_deg, roll_deg)
    q_dot = (_orientation(t + h, yaw_deg, pitch_deg, roll_deg)
             - _orientation(t - h, yaw_deg, pitch_deg, roll_deg)) / (2 * h)
    return 2.0 * _qmul(_qconj(q), q_dot)[..., 1:]


def _zero(t):
    return np.zeros_like(np.asarray(t, dtype="float64"))


def _constant(value: float):
    return lambda t: np.full_like(np.asarray(t, dtype="float64"), value)


def _min_jerk(s):
    """0 → 1 minimum-jerk step over s ∈ [0, 1]; peak rate 1.875 per unit time."""
    s = np.clip(np.asarray(s, dtype="float64"), 0.0, 1.0)
    return s ** 3 * (10 - 15 * s + 6 * s ** 2)


def native_session(rng, trial_id: str, title: str, purpose: str, duration_s: float,
                   yaw_deg, pitch_deg, cue: dict | None, shared_clock: bool = True, *,
                   roll_deg=None, tier: str = "V", camera: bool = True, worn: bool = True,
                   magnetometer: bool = False, near_factor: float = 1.0, head_offset_m=None,
                   gyro_bias=(0.0, 0.0, 0.0), stop_at_s: float | None = None,
                   cue_times_ms=None, blur_above_dps: float | None = None,
                   condition: dict | None = None, extra_truth: dict | None = None) -> dict:
    """One synthetic head-motion lab trial.

    ``yaw_deg``, ``pitch_deg`` and ``roll_deg`` give the head's orientation over
    time; ``head_offset_m(t)`` its displacement in the world frame, whose
    acceleration the accelerometer adds to gravity. ``near_factor`` scales the
    image shift of a near target: the camera sits in front of the neck's
    rotation axis, so a near scene moves (1 + r/d) times as far as a distant
    one. ``stop_at_s`` ends the recording early, as the tester's "Balance lost"
    does.
    """
    truth = NATIVE_TRUTH
    roll_deg = roll_deg or _zero
    fs, fps = truth["motion_rate_hz"], truth["video_fps"]
    scale = truth["scale_px_per_deg"]
    latency = truth["sensor_to_image_latency_ms"] / 1000.0

    # --- motion: sampled on the glasses clock, with ~0.4 ms timing jitter ----
    t_m = np.arange(0.0, duration_s, 1.0 / fs)
    t_m = np.sort(t_m + rng.normal(0, 0.0004, t_m.size))
    n = t_m.size
    quat = _orientation(t_m, yaw_deg, pitch_deg, roll_deg)
    rates = _body_rates(t_m, yaw_deg, pitch_deg, roll_deg) + np.asarray(gyro_bias)
    gyro = rates.T + np.array([rng.normal(0, 0.003, n) for _ in range(3)])
    specific_world = np.array([0.0, GRAVITY, 0.0])
    if head_offset_m is not None:
        h = 1e-3
        specific_world = specific_world + (
            head_offset_m(t_m + h) - 2 * head_offset_m(t_m) + head_offset_m(t_m - h)) / h ** 2
    accel = _to_body(quat, specific_world).T + np.array([rng.normal(0, 0.05, n) for _ in range(3)])
    m_delay = rng.uniform(*truth["motion_delivery_ms"], n)
    motion_origin_ns = 5_000_000_000_000  # ~83 min of glasses uptime
    t_device_ms = t_m * 1000.0

    # --- video: frames stamped on either the same clock or their own --------
    t_f = np.arange(0.0, duration_s, 1.0 / fps) if camera else np.empty(0)
    nf = t_f.size
    shift_x = np.full(nf, np.nan)
    shift_y = np.full(nf, np.nan)
    peak = np.full(nf, np.nan)
    texture = np.full(nf, np.nan)
    ref = np.full(nf, np.nan)
    v_delay = np.empty(0)
    if camera:
        content_t = t_f - latency
        q_content = _orientation(content_t, yaw_deg, pitch_deg, roll_deg)
        speed_dps = np.degrees(np.linalg.norm(
            _body_rates(content_t, yaw_deg, pitch_deg, roll_deg), axis=1))
        v_delay = rng.uniform(*truth["video_delivery_ms"], nf)
        last = 0
        for k in range(1, nf):
            if rng.random() < truth["skipped_analysis_fraction"]:
                continue  # recorded, not analyzed; the next frame spans the gap
            # Head rotation between the two frames, in the head's own axes.
            dq = _qmul(_qconj(q_content[last]), q_content[k])
            turn_deg = np.degrees(2.0 * dq[1:] * np.sign(dq[0]))
            shift_x[k] = scale * near_factor * turn_deg[1] + rng.normal(0, truth["image_noise_px_sd"])
            shift_y[k] = scale * near_factor * turn_deg[0] + rng.normal(0, truth["image_noise_px_sd"])
            peak[k] = rng.uniform(0.35, 0.9)
            texture[k] = rng.uniform(18, 40)
            if rng.random() < truth["blurred_frame_fraction"]:
                peak[k] = rng.uniform(0.02, 0.1)  # below the gate: must be ignored
                shift_x[k] += rng.normal(0, 25)
            elif blur_above_dps is not None and speed_dps[k] > blur_above_dps and rng.random() < 0.6:
                peak[k] = rng.uniform(0.02, 0.12)  # motion blur at impulse speed
                shift_x[k] += rng.normal(0, 15)
            ref[k] = last
            last = k

    mag = np.full((3, n), np.nan)
    if magnetometer:
        mag = _to_body(quat, _FIELD_WORLD_UT).T + rng.normal(0, 0.3, (3, n))

    # --- an early stop keeps what came before it ------------------------------
    end_s = duration_s if stop_at_s is None else stop_at_s
    keep = t_m <= end_s
    t_m, t_device_ms, m_delay = t_m[keep], t_device_ms[keep], m_delay[keep]
    gyro, accel, mag, quat = gyro[:, keep], accel[:, keep], mag[:, keep], quat[keep]
    n = t_m.size
    if camera:
        keep_f = t_f <= end_s
        t_f, v_delay = t_f[keep_f], v_delay[keep_f]
        shift_x, shift_y, peak, texture, ref = (a[keep_f] for a in (shift_x, shift_y, peak, texture, ref))
        nf = t_f.size

    # Phone clock: recording starts at t = 0 on both streams' true time.
    t_phone_m = t_device_ms + m_delay
    t_phone_v = t_f * 1000.0 + v_delay
    if shared_clock:
        video_origin_us = motion_origin_ns // 1000  # same monotonic clock
    else:
        video_origin_us = 123_456  # a media clock of its own

    marks = [{"t": 0.0, "label": "trial_start", "extra": {"trialId": trial_id}}]
    if cue:
        times = (cue_times_ms if cue_times_ms is not None else
                 [i * cue["intervalMs"] for i in range(int(duration_s * 1000 / cue["intervalMs"]))])
        for i, tc in enumerate(times):
            if tc > end_s * 1000.0:
                break
            marks.append({"t": float(tc), "label": "cue",
                          "extra": {"index": i, "label": cue["steps"][i % len(cue["steps"])]}})
    outcome = "completed" if stop_at_s is None else "balance_lost"
    marks.append({"t": end_s * 1000.0, "label": "trial_end",
                  "extra": {"outcome": outcome, "reason": None if stop_at_s is None else "Balance lost"}})

    def col(a, p=6):
        return _round(np.asarray(a, dtype="float64"), p)

    session_id = (f"SYNTH_{trial_id}_{'shared' if shared_clock else 'separate'}" if tier == "V"
                  else f"SYNTH_{trial_id}_native")
    return {
        "schema": "visor.imu.session/2",
        "startedAt": "2026-09-25T12:00:00.000Z",
        "t0Epoch": 1790340000000,
        "durationMs": end_s * 1000.0,
        "meta": {
            "sessionId": session_id,
            "participant": "SYNTHETIC",
            "trialId": trial_id,
            "trialTitle": title,
            "tier": tier,
            "worn": worn,
            "plannedDurationSec": duration_s,
            "purpose": purpose,
            "camera": camera,
            "cue": cue,
            "condition": condition,
            "platform": "android-mwdat",
            "sdk": {"name": "Meta Wearables Device Access Toolkit", "version": "1.0.0"},
            "glassesAtStart": {"model": "Meta Ray-Ban Display" if magnetometer or tier == "S"
                               else "Ray-Ban Meta"},
            "outcome": outcome,
            "synthetic": True,
            "consent": "synthetic-not-human-data",
            "groundTruth": {**truth, "shared_clock": shared_clock, **(extra_truth or {})},
            "clocks": {
                "phoneClock": "SystemClock.elapsedRealtimeNanos",
                "phoneOriginNs": "900000000000",
                "motionDeviceOriginNs": str(motion_origin_ns),
                "videoPtsOriginUs": str(video_origin_us) if camera else None,
            },
            "motionSourceCodes": {"0": "GLASSES", "1": "NEURAL_BAND", "2": "UNKNOWN"},
        },
        "marks": marks,
        "streams": {
            "dat_motion": {
                "n": n,
                "nullCounts": {} if magnetometer else {"mx": n, "my": n, "mz": n},
                "columns": {
                    "tPhone": col(t_phone_m, 4), "tDevice": col(t_device_ms, 4),
                    "ax": col(accel[0]), "ay": col(accel[1]), "az": col(accel[2]),
                    "gx": col(gyro[0]), "gy": col(gyro[1]), "gz": col(gyro[2]),
                    "mx": col(mag[0]), "my": col(mag[1]), "mz": col(mag[2]),
                    "qw": col(quat[:, 0]), "qx": col(quat[:, 1]), "qy": col(quat[:, 2]),
                    "qz": col(quat[:, 3]),
                    "source": col(np.zeros(n), 0),
                },
            },
            "dat_video": {
                "n": nf,
                "nullCounts": {"shiftX": int(np.isnan(shift_x).sum())} if camera else {},
                "columns": {
                    "tPhone": col(t_phone_v, 4), "tPts": col(t_f * 1000.0, 4),
                    "width": col(np.full(nf, 360), 0), "height": col(np.full(nf, 640), 0),
                    "shiftX": col(shift_x, 4), "shiftY": col(shift_y, 4),
                    "peak": col(peak, 4), "textureSd": col(texture, 3),
                    "refIndex": col(ref, 0), "analysisMs": col(np.full(nf, 8.0), 3),
                } if camera else {},
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


# ---- the web battery's pose trials, recorded natively (IMU only) ----

NATIVE_GYRO_BIAS_RAD_S = (0.0004, -0.0006, 0.0003)


def native_static_rest(rng) -> dict:
    # Folded glasses on a table: a fixed tilt, a constant gyro bias, no motion.
    return native_session(
        rng, "A1_static_rest", "Static rest", "Noise floor, bias, Allan deviation.", 120.0,
        yaw_deg=_zero, pitch_deg=_constant(-75.0), roll_deg=_constant(8.0), cue=None,
        tier="A", camera=False, worn=False, magnetometer=True, gyro_bias=NATIVE_GYRO_BIAS_RAD_S,
        extra_truth={"gyro_bias_rad_s": list(NATIVE_GYRO_BIAS_RAD_S),
                     "yaw_bias_dps": float(np.degrees(NATIVE_GYRO_BIAS_RAD_S[1]))})


def _native_paced(rng, trial_id, title, steps, yaw, pitch, roll) -> dict:
    # Paced sweeps with off-axis wobble, so gravity moves on every axis and a
    # quaternion cannot be confused with its conjugate.
    return native_session(
        rng, trial_id, title, "Paced head sweeps recorded through MWDAT Motion.", 60.0,
        yaw_deg=yaw, pitch_deg=pitch, roll_deg=roll,
        cue={"intervalMs": 2000, "steps": steps, "style": "SPOKEN"},
        tier="B", camera=False, magnetometer=True)


def native_yaw_paced(rng) -> dict:
    return _native_paced(rng, "B1_yaw_paced", "Yaw sweeps (paced)", ["LEFT", "RIGHT"],
                         yaw=lambda t: 70.0 * np.sin(2 * np.pi * 0.25 * t),
                         pitch=lambda t: 8.0 * np.sin(2 * np.pi * 0.13 * t + 1.0),
                         roll=lambda t: 5.0 * np.sin(2 * np.pi * 0.21 * t + 2.0))


def native_pitch_paced(rng) -> dict:
    return _native_paced(rng, "B2_pitch_paced", "Pitch sweeps (paced)", ["UP", "DOWN"],
                         yaw=lambda t: 6.0 * np.sin(2 * np.pi * 0.11 * t),
                         pitch=lambda t: 30.0 * np.sin(2 * np.pi * 0.25 * t),
                         roll=lambda t: 4.0 * np.sin(2 * np.pi * 0.17 * t + 1.0))


def native_roll_paced(rng) -> dict:
    return _native_paced(rng, "B3_roll_paced", "Roll sweeps (paced)", ["TILT LEFT", "TILT RIGHT"],
                         yaw=lambda t: 5.0 * np.sin(2 * np.pi * 0.09 * t),
                         pitch=lambda t: 5.0 * np.sin(2 * np.pi * 0.15 * t + 0.5),
                         roll=lambda t: 25.0 * np.sin(2 * np.pi * 0.25 * t))


# ---- Tier S: standardized balance and vestibular tasks ----

# Head sway, RMS degrees (anterior-posterior, medio-lateral), per stance and
# eyes condition. Eyes closed sways more, as a Romberg sign would show.
SWAY_TRUTH_DEG = {
    "S1_romberg_eyes_open": (0.30, 0.20),
    "S1_romberg_eyes_closed": (0.55, 0.35),
    "S2_tandem_left_front_eyes_open": (0.45, 0.70),
    "S2_tandem_right_front_eyes_open": (0.40, 0.65),
    "S2_tandem_left_front_eyes_closed": (0.80, 1.30),
    "S2_tandem_right_front_eyes_closed": (0.75, 1.20),
    "S3_single_leg_dominant_eyes_open": (0.60, 0.90),
    "S3_single_leg_non_dominant_eyes_open": (0.70, 1.00),
    "S3_single_leg_dominant_eyes_closed": (1.10, 1.60),
    "S3_single_leg_non_dominant_eyes_closed": (1.30, 1.90),
}
# The tester pressed "Balance lost" on this one.
BALANCE_LOST_AT_S = {"S3_single_leg_non_dominant_eyes_closed": 9.2}
# Analysis window, as balance.py defines it: settling after the start tones,
# and the step that ends a lost hold, which precedes the tester's button.
SWAY_SKIP_START_S = 2.0
SWAY_SKIP_BEFORE_LOSS_S = 1.0
HEAD_HEIGHT_M = 1.5


def _sway_signal(rng, rms_deg: float, f_lo: float = 0.1, f_hi: float = 0.8, k: int = 12):
    """Smooth random sway with a falling spectrum, scaled to ``rms_deg``."""
    freqs = rng.uniform(f_lo, f_hi, k)
    phases = rng.uniform(0, 2 * np.pi, k)
    amps = 1.0 / freqs

    def raw(t):
        t = np.asarray(t, dtype="float64")
        return np.sum(amps * np.sin(2 * np.pi * freqs * t[..., None] + phases), axis=-1)

    grid = np.arange(0.0, 60.0, 0.01)
    gain = rms_deg / np.std(raw(grid))
    return lambda t: gain * raw(t)


def _stance(rng, trial_id: str, title: str, condition: dict, duration_s: float) -> dict:
    ap_rms, ml_rms = SWAY_TRUTH_DEG[trial_id]
    ap, ml, yaw = _sway_signal(rng, ap_rms), _sway_signal(rng, ml_rms), _sway_signal(rng, 0.4)
    stop = BALANCE_LOST_AT_S.get(trial_id)

    def ml_total(t):
        # Losing balance: a sideways lurch over the last 0.6 s before the stop.
        lurch = 0.0 if stop is None else 4.0 * _min_jerk((np.asarray(t) - (stop - 0.6)) / 0.6)
        return ml(t) + lurch

    def offset(t):
        # Inverted pendulum about the ankles: the head moves with its tilt.
        rad = np.pi / 180
        return np.stack([HEAD_HEIGHT_M * np.sin(ml_total(t) * rad), _zero(t),
                         -HEAD_HEIGHT_M * np.sin(ap(t) * rad)], axis=-1)

    end = stop if stop is not None else duration_s
    window = np.arange(SWAY_SKIP_START_S, end - (SWAY_SKIP_BEFORE_LOSS_S if stop else 0.0), 1 / 60)
    a, m = ap(window), ml_total(window)
    cov = np.cov(np.vstack([m - m.mean(), a - a.mean()]))
    # Sway acceleration, noise-free: the specific force in the trial's mean
    # levelled frame, horizontal part, about its mean (balance.py's definition).
    pitch, roll = (lambda t: -ap(t)), (lambda t: -ml_total(t))
    h = 1e-3
    force = _to_body(_orientation(window, yaw, pitch, roll), np.array([0.0, GRAVITY, 0.0])
                     + (offset(window + h) - 2 * offset(window) + offset(window - h)) / h ** 2)
    up = force.mean(axis=0) / np.linalg.norm(force.mean(axis=0))
    horizontal = force - np.outer(force @ up, up)
    truth = {
        "sway_ap_rms_deg": float(np.std(a)), "sway_ml_rms_deg": float(np.std(m)),
        "sway_ellipse_area_deg2": float(np.pi * CHI2_95_2DOF * np.sqrt(np.linalg.det(cov))),
        "sway_accel_rms_ms2": float(np.sqrt(np.mean(np.sum((horizontal - horizontal.mean(axis=0)) ** 2, axis=1)))),
        "hold_s": float(end), "balance_lost": stop is not None,
        "gyro_bias_rad_s": list(NATIVE_GYRO_BIAS_RAD_S),
    }
    # Forward lean tips the nose down (-pitch); leaning right drops the right
    # ear (-roll). The analysis reports AP = -pitch and ML = -roll.
    return native_session(
        rng, trial_id, title, "Standardized balance task; head sway from the IMU.", duration_s,
        yaw_deg=yaw, pitch_deg=pitch, roll_deg=roll, cue=None, tier="S", camera=False,
        head_offset_m=offset, gyro_bias=NATIVE_GYRO_BIAS_RAD_S, stop_at_s=stop,
        condition=condition, extra_truth=truth)


def balance_stances(rng) -> list[dict]:
    out = []
    for eyes in ("open", "closed"):
        out.append(_stance(rng, f"S1_romberg_eyes_{eyes}", f"Romberg, eyes {eyes}",
                           {"task": "romberg", "eyes": eyes}, 30.0))
    for eyes in ("open", "closed"):
        for front in ("left", "right"):
            out.append(_stance(rng, f"S2_tandem_{front}_front_eyes_{eyes}",
                               f"Tandem stance, {front} foot in front, eyes {eyes}",
                               {"task": "tandem", "eyes": eyes, "front": front}, 30.0))
    for eyes in ("open", "closed"):
        for leg in ("dominant", "non_dominant"):
            out.append(_stance(rng, f"S3_single_leg_{leg}_eyes_{eyes}",
                               f"Single-leg stance, {leg.replace('_', '-')} leg, eyes {eyes}",
                               {"task": "single_leg", "eyes": eyes, "leg": leg}, 20.0))
    return out


# Ten head impulses, + = left. Peak velocity of a minimum-jerk turn is
# 1.875 x amplitude / duration; the fourth is deliberately slow (131 °/s).
IMPULSES = {
    "direction": [1, -1, -1, 1, -1, 1, 1, -1, 1, -1],
    "amplitude_deg": [18, 16, 20, 14, 17, 19, 15, 18, 16, 20],
    "duration_s": [0.15, 0.14, 0.16, 0.20, 0.15, 0.13, 0.15, 0.17, 0.14, 0.16],
}
IMPULSE_CUE = {"intervalMs": 3000, "steps": ["IMPULSE"], "style": "TONE",
               "jitterMs": 750, "firstAtMs": 3000, "count": 10}


def head_impulses(rng) -> dict:
    cue = IMPULSE_CUE
    gaps = cue["intervalMs"] + rng.uniform(-cue["jitterMs"], cue["jitterMs"], cue["count"] - 1)
    cue_ms = cue["firstAtMs"] + np.concatenate([[0.0], np.cumsum(gaps)])
    onsets = cue_ms / 1000.0 + rng.uniform(0.15, 0.25, cue["count"])
    d, amp, dur = (np.asarray(IMPULSES[k], dtype="float64")
                   for k in ("direction", "amplitude_deg", "duration_s"))
    hold_s, return_s = 0.3, 1.2

    def yaw(t):
        t = np.asarray(t, dtype="float64")[..., None]
        out = _min_jerk((t - onsets) / dur) - _min_jerk((t - (onsets + dur + hold_s)) / return_s)
        return np.sum(d * amp * out, axis=-1)

    truth = {"impulses": [{"onset_s": float(o), "direction": "left" if s > 0 else "right",
                           "amplitude_deg": float(a), "peak_dps": float(1.875 * a / du)}
                          for o, s, a, du in zip(onsets, d, amp, dur)]}
    return native_session(
        rng, "S4_head_impulse", "Head impulse test (seated)",
        "Ten brief, rapid head turns on unpredictable tones, eyes on a target.", 40.0,
        yaw_deg=yaw, pitch_deg=lambda t: 1.5 * np.sin(2 * np.pi * 0.07 * t),
        roll_deg=lambda t: 1.0 * np.sin(2 * np.pi * 0.05 * t + 1.0),
        cue=cue, cue_times_ms=cue_ms.tolist(), tier="S", blur_above_dps=180.0,
        condition={"task": "head_impulse", "plane": "horizontal"}, extra_truth=truth)


# Reading text at 40 cm with the camera ~10 cm in front of the rotation axis.
GAZE_NEAR_FACTOR = 1.25


def gaze_stabilization(rng, plane: str) -> dict:
    horizontal = plane == "horizontal"
    big = lambda t: (12.0 if horizontal else 10.0) * np.sin(2 * np.pi * 1.0 * t)  # noqa: E731
    small = lambda t: 1.0 * np.sin(2 * np.pi * 0.15 * t)  # noqa: E731
    return native_session(
        rng, f"S5_gaze_stabilization_{plane}", f"Gaze stabilization, {plane}, reading",
        "Reading while the head moves at a paced 1 Hz (VOR x1, near target).", 30.0,
        yaw_deg=big if horizontal else small, pitch_deg=small if horizontal else big,
        cue={"intervalMs": 500, "steps": ["LEFT", "RIGHT"] if horizontal else ["UP", "DOWN"],
             "style": "METRONOME"},
        tier="S", near_factor=GAZE_NEAR_FACTOR,
        condition={"task": "gaze_stabilization", "plane": plane},
        extra_truth={"frequency_hz": 1.0, "amplitude_deg": 12.0 if horizontal else 10.0,
                     "near_factor": GAZE_NEAR_FACTOR})

BUILDERS = {
    "A1_static_rest": static_rest,
    "B1_yaw_paced": yaw_paced,
    "C1_stillness_hold": stillness_hold,
    "C2_scanning_pattern": scanning,
    "C3_walk_straight": walking,
}

# Native builders, keyed by the file name they write; a builder that returns
# several sessions names each file after its trial. Order matters: each draws
# from the same random stream, so new builders go at the end.
NATIVE_BUILDERS = {
    "V1_vor_yaw_paced_shared": lambda rng: vor_yaw(rng, shared_clock=True),
    "V1_vor_yaw_paced_separate": lambda rng: vor_yaw(rng, shared_clock=False),
    "V2_vor_pitch_paced": vor_pitch,
    "V4_slow_pan": slow_pan,
    "A1_static_rest_native": native_static_rest,
    "B1_yaw_paced_native": native_yaw_paced,
    "B2_pitch_paced_native": native_pitch_paced,
    "B3_roll_paced_native": native_roll_paced,
    "S1_S3_stances": balance_stances,
    "S4_head_impulse": head_impulses,
    "S5_gaze_stabilization_horizontal": lambda rng: gaze_stabilization(rng, "horizontal"),
    "S5_gaze_stabilization_vertical": lambda rng: gaze_stabilization(rng, "vertical"),
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

    print(f"Native ground truth: {json.dumps(NATIVE_TRUTH, ensure_ascii=False)}")
    written = len(BUILDERS)
    for name, build in NATIVE_BUILDERS.items():
        built = build(rng)
        for session in built if isinstance(built, list) else [built]:
            trial_id = session["meta"]["trialId"]
            stem = trial_id if isinstance(built, list) else name
            dest = out_root / "SYNTHETIC" / trial_id / f"SYNTH_{stem}.json.gz"
            dest.parent.mkdir(parents=True, exist_ok=True)
            with gzip.open(dest, "wt", encoding="utf-8") as fh:
                json.dump(session, fh)
            written += 1
            print(f"  + {dest.relative_to(out_root)}  "
                  f"({dest.stat().st_size/1024:.0f} KB, {session['streams']['dat_motion']['n']} motion "
                  f"samples, {session['streams']['dat_video']['n']} frames)")

    print(f"\nWrote {written} synthetic sessions -> {out_root}")
    print("These are NOT measurements. Never cite them as results.")


if __name__ == "__main__":
    main()
