"""Standardized balance and vestibular tasks, from the head IMU (Tier S).

Five tasks, recorded in the Android head-motion lab through MWDAT Motion, with
the glasses camera on for the last two:

1. Modified Romberg: feet together, eyes open and eyes closed, 30 s each.
2. Tandem stance: heel to toe, either foot in front, eyes open and closed, 30 s.
3. Single-leg stance: dominant and non-dominant leg, eyes open and closed, 20 s.
4. Head impulse test: ten brief, rapid head turns on unpredictable tones, seated.
5. Gaze stabilization: reading while the head moves horizontally, then
   vertically, at a paced 1 Hz, 30 s each.

What is measured, and what is not claimed:

* **Sway** is the head's angular sway: anterior-posterior (AP, + forward) and
  medio-lateral (ML, + right) tilt, in the trial's mean pose levelled by gravity
  (:func:`pose.initial_alignment`). It is integrated from the gyroscope, whose
  bias is fitted as the steady slope between that integral and the
  accelerometer's tilt. The pose module's attitude filter is deliberately not
  used here: its accelerometer correction reads the head's own sway
  acceleration as tilt (a standing body is an inverted pendulum, so the two are
  in phase) and overestimated quiet-stance sway by 7-28% on synthetic ground
  truth. This is head sway, not centre-of-pressure sway; the two agree only as
  far as the head moves with the body.
* **Sway acceleration** is the accelerometer's horizontal part in that same
  levelled frame, about its mean: tilt and translation together, as in
  accelerometer-based posturography (ISway-style RMS sway).
* **Hold time** is the tester's stopwatch. A stance ends at the planned
  duration, or when the tester presses "Balance lost".
* **Head impulses** are the head's kinematics: direction, peak velocity,
  amplitude. A clinical head impulse test also tracks the eyes, which the
  glasses cannot, so nothing here is a VOR gain.
* **Gaze stabilization** is the head movement achieved against the pacing,
  with the camera's view of it from :mod:`vor`.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import numpy as np
import pandas as pd
from scipy import signal
from scipy.integrate import cumulative_trapezoid

from . import pose
from .loader import Session
from .metrics import GYRO_AXES, YAW_AXIS, GYRO_HEAD_AXIS, estimate_fs

STANCE_TASKS = ("romberg", "tandem", "single_leg")
TASK_TITLES = {
    "romberg": "Modified Romberg",
    "tandem": "Tandem stance",
    "single_leg": "Single-leg stance",
    "head_impulse": "Head impulse test",
    "gaze_stabilization": "Gaze stabilization",
}

# The sway window skips the settling after the start tones (eyes closing, a
# foot lifting), and, when balance was lost, the second before the tester's
# press, which holds the step itself. The phone's quick look uses the same window.
SKIP_START_S = 2.0
SKIP_BEFORE_LOSS_S = 1.0
MIN_WINDOW_S = 5.0
SWAY_LOWPASS_HZ = 5.0
ACCEL_LOWPASS_HZ = 3.5
# The ellipse holding 95% of sway samples: chi-square with 2 degrees of freedom.
CHI2_95_2DOF = -2.0 * np.log(0.05)

# Head impulses. The impulse for a tone is the first run of yaw rate above
# IMPULSE_DETECT_DPS that starts after it (within IMPULSE_WINDOW_S, before the
# next tone); its peak is that run's maximum, and its edges are where the rate
# falls below IMPULSE_EDGE_DPS. The phone's quick look uses the same rule.
# Clinical head impulses run at roughly 150-300 °/s; slower ones are counted.
IMPULSE_DETECT_DPS = 60.0
IMPULSE_EDGE_DPS = 20.0
IMPULSE_RAPID_DPS = 150.0
IMPULSE_WINDOW_S = 2.0

PITCH_AXIS = next(c for c, axis in GYRO_HEAD_AXIS.items() if axis == "pitch")


def condition(session: Session) -> dict[str, Any]:
    return dict(session.meta.get("condition") or {})


def task(session: Session) -> str | None:
    return condition(session).get("task")


def _mark(session: Session, label: str) -> dict[str, Any] | None:
    return next((m for m in session.marks if m.get("label") == label), None)


def hold(session: Session) -> dict[str, Any]:
    """Hold time (s), from the trial's start and end marks."""
    start = _mark(session, "trial_start") or {}
    end = _mark(session, "trial_end") or {}
    t0 = float(start.get("t") or 0.0) / 1000.0
    t1 = float(end.get("t")) / 1000.0 if end.get("t") is not None else t0 + session.duration_s
    lost = (end.get("extra") or {}).get("outcome") == "balance_lost" or \
        session.meta.get("outcome") == "balance_lost"
    return {"start_s": t0, "end_s": t1, "hold_s": t1 - t0, "balance_lost": bool(lost),
            "planned_s": session.meta.get("plannedDurationSec")}


def usable(session: Session) -> bool:
    """A recording whose data can be trusted: a valid ending and no holes."""
    if session.meta.get("outcome") not in (None, "completed", "balance_lost"):
        return False
    for m in session.marks:
        if m.get("label") in ("visibility_hidden", "motion_revive"):
            return False
        if (m.get("extra") or {}).get("state") == "PAUSED":
            return False
    return not session.motion.empty


# ============================ sway ============================


@dataclass
class Sway:
    t: np.ndarray        # s, from the trial's start
    ap: np.ndarray       # deg, + forward, about the window mean
    ml: np.ndarray       # deg, + right, about the window mean
    window: tuple[float, float]


def sway_window(session: Session) -> tuple[float, float] | None:
    h = hold(session)
    start = h["start_s"] + SKIP_START_S
    end = h["end_s"] - (SKIP_BEFORE_LOSS_S if h["balance_lost"] else 0.0)
    return (start, end) if end - start >= MIN_WINDOW_S else None


def _levelled(session: Session) -> tuple[np.ndarray, np.ndarray, np.ndarray, float] | None:
    """Window samples in the trial's mean pose, levelled: time (s), rates (rad/s) and
    specific force (m/s²) about world X forward, Y left, Z up."""
    window = sway_window(session)
    imu = pose.imu_arrays(session)
    if window is None or imu is None:
        return None
    sel = (imu.t >= window[0]) & (imu.t <= window[1])
    if sel.sum() < 32:
        return None
    level = pose.quat_to_matrix(pose.initial_alignment(imu.accel[sel].mean(axis=0)))[0]
    return imu.t[sel], imu.gyro[sel] @ level.T, imu.accel[sel] @ level.T, imu.fs


def sway_series(session: Session) -> Sway | None:
    found = _levelled(session)
    if found is None:
        return None
    t, rates, force, fs = found
    # Rotation about the left axis tips the head forward; about the forward
    # axis, to the right. The gyroscope integrates both exactly, but for its bias.
    gyro_tilt = cumulative_trapezoid(rates[:, [1, 0]], t, axis=0, initial=0.0)
    # The accelerometer sees the same tilt plus the head's sway acceleration,
    # which oscillates. A bias is a steady slope between the two, so it is fitted
    # as one, per axis; the sway acceleration hardly moves a regression slope.
    g = float(np.linalg.norm(force.mean(axis=0)))
    accel_tilt = np.column_stack([-force[:, 0] / g, force[:, 1] / g])
    slope = np.polyfit(t - t[0], gyro_tilt - accel_tilt, 1)[0]
    tilt = np.degrees(gyro_tilt - np.outer(t - t[0], slope))
    sos = signal.butter(2, SWAY_LOWPASS_HZ / (fs / 2.0), output="sos")
    tilt = signal.sosfiltfilt(sos, tilt, axis=0)
    ap, ml = tilt[:, 0], tilt[:, 1]
    t0 = hold(session)["start_s"]
    return Sway(t=t - t0, ap=ap - ap.mean(), ml=ml - ml.mean(), window=(t[0] - t0, t[-1] - t0))


def ellipse(ml: np.ndarray, ap: np.ndarray) -> dict[str, float]:
    """95% prediction ellipse of the sway path: area, semi-axes and tilt."""
    cov = np.cov(np.vstack([ml, ap]))
    values, vectors = np.linalg.eigh(cov)
    semi = np.sqrt(CHI2_95_2DOF * np.clip(values, 0.0, None))
    return {
        "area_deg2": float(np.pi * semi[0] * semi[1]),
        "major_deg": float(semi[1]), "minor_deg": float(semi[0]),
        "angle_deg": float(np.degrees(np.arctan2(vectors[1, 1], vectors[0, 1]))),
    }


def angular_speed_rms(session: Session, window: tuple[float, float]) -> float:
    """RMS head angular speed (°/s) over a window, all three axes; as on the phone."""
    df = session.motion
    t = df["tPerf"].to_numpy(dtype="float64") / 1000.0
    rates = df[list(GYRO_AXES)].to_numpy(dtype="float64")
    sel = (t >= window[0]) & (t <= window[1]) & np.all(np.isfinite(rates), axis=1)
    return float(np.sqrt(np.mean(np.sum(rates[sel] ** 2, axis=1)))) if sel.any() else float("nan")


def sway_acceleration_rms(session: Session) -> float | None:
    """RMS horizontal specific force (m/s²) in the levelled frame, about its mean."""
    found = _levelled(session)
    if found is None:
        return None
    _, _, force, fs = found
    sos = signal.butter(2, ACCEL_LOWPASS_HZ / (fs / 2.0), output="sos")
    horizontal = signal.sosfiltfilt(sos, force[:, :2], axis=0)
    horizontal -= horizontal.mean(axis=0)
    return float(np.sqrt(np.mean(np.sum(horizontal ** 2, axis=1))))


def sway_metrics(session: Session) -> dict[str, Any] | None:
    sway = sway_series(session)
    if sway is None:
        return None
    t0 = hold(session)["start_s"]
    window = (sway.window[0] + t0, sway.window[1] + t0)
    duration = float(sway.t[-1] - sway.t[0])
    path = float(np.sum(np.hypot(np.diff(sway.ap), np.diff(sway.ml))))
    return {
        "window_s": list(sway.window),
        "ap_rms_deg": float(np.sqrt(np.mean(sway.ap ** 2))),
        "ml_rms_deg": float(np.sqrt(np.mean(sway.ml ** 2))),
        "ellipse": ellipse(sway.ml, sway.ap),
        "mean_velocity_dps": path / duration if duration > 0 else float("nan"),
        "angular_speed_rms_dps": angular_speed_rms(session, window),
        "accel_rms_ms2": sway_acceleration_rms(session),
    }


def stance_row(session: Session) -> dict[str, Any]:
    c = condition(session)
    row: dict[str, Any] = {
        "trial_id": session.trial_id, "participant": session.participant, "task": c.get("task"),
        "eyes": c.get("eyes"), "variant": c.get("front") or c.get("leg"),
        **{k: v for k, v in hold(session).items() if k in ("hold_s", "balance_lost", "planned_s")},
    }
    row["sway"] = sway_metrics(session)
    return row


def romberg_ratios(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Eyes-closed over eyes-open, per stance: how much the participant relies on vision."""
    out = []
    keyed = {(r["task"], r["variant"], r["eyes"]): r for r in rows if r.get("sway")}
    for (task_name, variant, eyes), closed in keyed.items():
        if eyes != "closed":
            continue
        opened = keyed.get((task_name, variant, "open"))
        if opened is None:
            continue
        a, b = opened["sway"], closed["sway"]
        out.append({
            "task": task_name, "variant": variant,
            "area_ratio": b["ellipse"]["area_deg2"] / a["ellipse"]["area_deg2"],
            "velocity_ratio": b["mean_velocity_dps"] / a["mean_velocity_dps"],
        })
    return out


# ============================ head impulses ============================


def _cue_times_s(session: Session) -> np.ndarray:
    return np.array([float(m["t"]) / 1000.0 for m in session.marks if m.get("label") == "cue"])


def head_impulses(session: Session) -> pd.DataFrame:
    """One row per tone: the head impulse that answered it, if any."""
    df = session.motion
    t = df["tPerf"].to_numpy(dtype="float64") / 1000.0
    yaw = df[YAW_AXIS].to_numpy(dtype="float64")
    ok = np.isfinite(t) & np.isfinite(yaw)
    t, yaw = t[ok], yaw[ok]
    if t.size < 32:
        return pd.DataFrame()
    fast = np.abs(yaw) >= IMPULSE_DETECT_DPS
    edges = np.diff(np.concatenate([[0], fast.astype(int), [0]]))
    runs = list(zip(np.flatnonzero(edges == 1), np.flatnonzero(edges == -1)))  # [start, end)
    cues = _cue_times_s(session)
    rows = []
    for index, cue in enumerate(cues):
        later = cues[index + 1] if index + 1 < len(cues) else np.inf
        run = next(((s, e) for s, e in runs if cue <= t[s] < min(cue + IMPULSE_WINDOW_S, later)), None)
        if run is None:
            rows.append({"cue": index, "cue_s": cue, "detected": False})
            continue
        p = int(run[0] + np.argmax(np.abs(yaw[run[0]:run[1]])))
        sign = np.sign(yaw[p])
        below = np.flatnonzero(np.abs(yaw[:p]) < IMPULSE_EDGE_DPS)
        onset = int(below[-1]) if below.size else 0
        after = np.flatnonzero(np.abs(yaw[p:]) < IMPULSE_EDGE_DPS)
        end = p + int(after[0]) if after.size else len(yaw) - 1
        seg = slice(onset, end + 1)
        accel = np.gradient(yaw[onset:p + 1], t[onset:p + 1]) if p > onset else np.array([0.0])
        rows.append({
            "cue": index, "cue_s": cue, "detected": True,
            "direction": "left" if sign > 0 else "right",
            "peak_t_s": float(t[p]), "onset_s": float(t[onset]),
            "latency_s": float(t[onset] - cue),
            "peak_dps": float(abs(yaw[p])),
            "amplitude_deg": float(abs(np.trapezoid(yaw[seg], t[seg]))),
            "duration_ms": float((t[end] - t[onset]) * 1000.0),
            "peak_accel_dps2": float(np.max(np.abs(accel))),
            "rapid": bool(abs(yaw[p]) >= IMPULSE_RAPID_DPS),
        })
    return pd.DataFrame(rows)


def impulse_summary(impulses: pd.DataFrame) -> dict[str, Any]:
    if impulses.empty:
        return {"cues": 0, "detected": 0}
    found = impulses[impulses["detected"]]
    out: dict[str, Any] = {"cues": int(len(impulses)), "detected": int(len(found)),
                           "rapid": int(found["rapid"].sum()) if len(found) else 0}
    for side in ("left", "right"):
        s = found[found["direction"] == side] if len(found) else found
        out[side] = {
            "count": int(len(s)),
            "median_peak_dps": float(s["peak_dps"].median()) if len(s) else None,
            "median_amplitude_deg": float(s["amplitude_deg"].median()) if len(s) else None,
        }
    return out


# ============================ gaze stabilization ============================


def gaze_metrics(session: Session) -> dict[str, Any] | None:
    """The head movement achieved, in the commanded plane, against the pacing."""
    plane = condition(session).get("plane", "horizontal")
    axis = YAW_AXIS if plane == "horizontal" else PITCH_AXIS
    df = session.motion
    h = hold(session)
    t = df["tPerf"].to_numpy(dtype="float64") / 1000.0
    sel = (t >= h["start_s"] + SKIP_START_S) & (t <= h["end_s"]) & np.isfinite(df[axis].to_numpy())
    if sel.sum() < 64:
        return None
    rate = df[axis].to_numpy(dtype="float64")[sel]
    others = [c for c in GYRO_AXES if c != axis]
    off_axis = df[others].to_numpy(dtype="float64")[sel]
    fs = estimate_fs(df)
    freqs, density = signal.welch(rate - rate.mean(), fs=fs, nperseg=min(len(rate), int(8 * fs)))
    band = (freqs >= 0.3) & (freqs <= 4.0)
    f0 = float(freqs[band][np.argmax(density[band])])
    # Amplitude of the movement at that frequency, by least squares on the rate.
    tt = t[sel]
    design = np.column_stack([np.cos(2 * np.pi * f0 * tt), np.sin(2 * np.pi * f0 * tt), np.ones_like(tt)])
    (a, b, _), *_ = np.linalg.lstsq(design, rate, rcond=None)
    rate_amp = float(np.hypot(a, b))
    cue = session.meta.get("cue") or {}
    paced = 1000.0 / (2.0 * cue["intervalMs"]) if cue.get("intervalMs") else None
    return {
        "plane": plane, "axis": axis,
        "frequency_hz": f0, "paced_hz": paced,
        "amplitude_deg": rate_amp / (2 * np.pi * f0),
        "peak_velocity_dps": float(np.percentile(np.abs(rate), 95)),
        "off_axis_ratio": float(np.sqrt(np.mean(off_axis ** 2)) / np.sqrt(np.mean(rate ** 2))),
    }
