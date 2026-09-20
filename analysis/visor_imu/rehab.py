"""Rehabilitation-relevant derived measures.

Turns raw inertial signal into the quantities VISOR would actually act on:
whether the head is still enough to capture a frame, whether the wearer is
performing a compensatory scan, and whether they are walking.

Each function reports the evidence it used, so a negative result ("cadence not
recoverable") is distinguishable from a missing input ("no usable vertical
axis"). For grant reporting that distinction matters: an unmeasured capability
must not be written up as an absent one.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import numpy as np
import pandas as pd
from scipy import signal

from .loader import Session
from .metrics import GYRO_AXES, estimate_fs, _finite


# ============================ primitives ============================


def angular_speed(df: pd.DataFrame) -> np.ndarray:
    """Magnitude of the rotation-rate vector, deg/s.

    Direction-agnostic, so it works as a single "how much is the head moving"
    scalar regardless of which axis the motion is about.
    """
    cols = [c for c in GYRO_AXES if c in df]
    if len(cols) != 3:
        return np.array([])
    arr = df[list(GYRO_AXES)].to_numpy(dtype="float64")
    mask = np.all(np.isfinite(arr), axis=1)
    out = np.full(arr.shape[0], np.nan)
    out[mask] = np.linalg.norm(arr[mask], axis=1)
    return out


def integrate_angle(t_ms: np.ndarray, rate_dps: np.ndarray) -> np.ndarray:
    """Trapezoidal integration of rate to relative angle, degrees.

    Valid only over short windows: with no absolute reference the result drifts
    at the gyro's bias-instability rate.
    """
    t = np.asarray(t_ms, dtype="float64") / 1000.0
    r = np.asarray(rate_dps, dtype="float64")
    mask = np.isfinite(t) & np.isfinite(r)
    if mask.sum() < 2:
        return np.array([])
    out = np.zeros(mask.sum())
    tt, rr = t[mask], r[mask]
    out[1:] = np.cumsum(0.5 * (rr[1:] + rr[:-1]) * np.diff(tt))
    return out


def bandpass(values: np.ndarray, fs: float, low: float, high: float) -> np.ndarray:
    """Zero-phase Butterworth bandpass. Returns empty if the band is invalid."""
    v = _finite(values)
    nyq = fs / 2.0
    if v.size < 32 or fs <= 0 or high >= nyq or low <= 0 or low >= high:
        return np.array([])
    sos = signal.butter(4, [low / nyq, high / nyq], btype="band", output="sos")
    return signal.sosfiltfilt(sos, v - np.mean(v))


# ============================ stillness / dwell gating ============================


@dataclass
class StillnessProfile:
    """Head-stability statistics and the capture threshold they imply."""

    n: int
    fs: float
    median_dps: float
    p95_dps: float
    p99_dps: float
    max_dps: float
    recommended_threshold_dps: float
    basis: str

    def as_dict(self) -> dict[str, Any]:
        return self.__dict__.copy()


def stillness_profile(session: Session) -> StillnessProfile | None:
    """Characterize "wearer holding still" from a stillness-hold trial.

    The recommended threshold is the 95th percentile of angular speed during a
    deliberate hold. Gating capture above this value means a genuine attempt to
    hold still passes ~95% of the time, while ordinary head motion does not.
    """
    df = session.motion
    if df.empty:
        return None
    speed = _finite(angular_speed(df))
    if speed.size < 32:
        return None

    p95 = float(np.percentile(speed, 95))
    return StillnessProfile(
        n=int(speed.size),
        fs=estimate_fs(df),
        median_dps=float(np.median(speed)),
        p95_dps=p95,
        p99_dps=float(np.percentile(speed, 99)),
        max_dps=float(np.max(speed)),
        recommended_threshold_dps=round(p95, 2),
        basis="95th percentile of angular speed during a deliberate stillness hold",
    )


def detect_dwells(df: pd.DataFrame, threshold_dps: float, min_duration_s: float = 0.5
                  ) -> pd.DataFrame:
    """Find intervals where the head stays below ``threshold_dps``.

    This is the primitive VISOR would use to trigger OCR/VLM capture only when
    the wearer has settled, instead of burning inference on motion-blurred
    frames.
    """
    if df.empty or "tPerf" not in df:
        return pd.DataFrame(columns=["start_s", "end_s", "duration_s", "mean_dps"])

    speed = angular_speed(df)
    t = np.asarray(df["tPerf"], dtype="float64") / 1000.0
    mask = np.isfinite(speed) & np.isfinite(t)
    if mask.sum() < 2:
        return pd.DataFrame(columns=["start_s", "end_s", "duration_s", "mean_dps"])

    speed, t = speed[mask], t[mask]
    still = speed < threshold_dps

    rows = []
    idx = 0
    while idx < still.size:
        if not still[idx]:
            idx += 1
            continue
        start = idx
        while idx < still.size and still[idx]:
            idx += 1
        duration = t[idx - 1] - t[start]
        if duration >= min_duration_s:
            rows.append({
                "start_s": float(t[start]),
                "end_s": float(t[idx - 1]),
                "duration_s": float(duration),
                "mean_dps": float(np.mean(speed[start:idx])),
            })
    return pd.DataFrame(rows)


# ============================ compensatory scanning ============================


def scan_metrics(session: Session, min_peak_dps: float = 20.0) -> dict[str, Any] | None:
    """Quantify horizontal scanning from yaw rate.

    Relevant to hemianopia and neglect rehabilitation, where training targets
    scan amplitude and left/right symmetry. An asymmetry index far from zero
    means the wearer is under-scanning one side — the behaviour such training
    is designed to correct.
    """
    df = session.motion
    if df.empty or "rrAlpha" not in df:
        return None

    fs = estimate_fs(df)
    yaw_rate = np.asarray(df["rrAlpha"], dtype="float64")
    t = np.asarray(df["tPerf"], dtype="float64")
    mask = np.isfinite(yaw_rate) & np.isfinite(t)
    if mask.sum() < 32:
        return None
    yaw_rate, t = yaw_rate[mask], t[mask]

    # Smooth lightly so a single noisy sample cannot create a spurious sweep.
    if fs > 4:
        sos = signal.butter(4, min(3.0, fs / 2.5) / (fs / 2.0), btype="low", output="sos")
        smooth = signal.sosfiltfilt(sos, yaw_rate)
    else:
        smooth = yaw_rate

    # A sweep is a run of samples on one side of zero containing a real peak.
    sign = np.sign(smooth)
    boundaries = np.flatnonzero(np.diff(sign) != 0) + 1
    segments = np.split(np.arange(smooth.size), boundaries)

    sweeps = []
    for seg in segments:
        if seg.size < 3:
            continue
        chunk = smooth[seg]
        peak = float(np.max(np.abs(chunk)))
        if peak < min_peak_dps:
            continue
        amplitude = float(abs(np.trapezoid(chunk, t[seg] / 1000.0)))
        sweeps.append({
            "direction": "left" if np.mean(chunk) > 0 else "right",
            "peak_dps": peak,
            "amplitude_deg": amplitude,
            "duration_s": float((t[seg][-1] - t[seg][0]) / 1000.0),
        })

    if not sweeps:
        return {"sweeps": 0, "note": "no sweeps exceeded the peak-rate threshold"}

    frame = pd.DataFrame(sweeps)
    left = frame[frame["direction"] == "left"]
    right = frame[frame["direction"] == "right"]
    left_amp = float(left["amplitude_deg"].sum())
    right_amp = float(right["amplitude_deg"].sum())
    total = left_amp + right_amp

    return {
        "sweeps": int(len(frame)),
        "duration_s": session.duration_s,
        "sweep_rate_per_min": float(len(frame) / session.duration_s * 60) if session.duration_s else None,
        "mean_amplitude_deg": float(frame["amplitude_deg"].mean()),
        "median_amplitude_deg": float(frame["amplitude_deg"].median()),
        "max_amplitude_deg": float(frame["amplitude_deg"].max()),
        "mean_peak_dps": float(frame["peak_dps"].mean()),
        "max_peak_dps": float(frame["peak_dps"].max()),
        "left_sweeps": int(len(left)),
        "right_sweeps": int(len(right)),
        # 0 = symmetric; positive = more leftward excursion than rightward.
        "asymmetry_index": float((left_amp - right_amp) / total) if total > 0 else None,
    }


# ============================ gait ============================


def gait_cadence(session: Session) -> dict[str, Any] | None:
    """Estimate step cadence from head-mounted vertical acceleration.

    Walking produces a periodic vertical head oscillation at step frequency.
    The dominant spectral peak in the 0.5–4 Hz band is taken as cadence; the
    peak's prominence relative to the band is reported so a weak, ambiguous
    peak is not mistaken for a confident detection.
    """
    df = session.motion
    if df.empty:
        return None
    fs = estimate_fs(df)
    if fs < 8:
        return {"detected": False, "reason": f"sample rate {fs:.1f} Hz too low for gait analysis"}

    # The gravity-aligned axis carries the vertical bob; pick it by largest mean.
    candidates = {ax: df[ax] for ax in ("agx", "agy", "agz") if ax in df}
    usable = {k: v for k, v in candidates.items() if np.isfinite(v).any()}
    if not usable:
        return {"detected": False, "reason": "no usable acceleration axis"}
    vertical_axis = max(usable, key=lambda k: abs(np.nanmean(usable[k])))

    filtered = bandpass(usable[vertical_axis].to_numpy(dtype="float64"), fs, 0.5, 4.0)
    if filtered.size < 64:
        return {"detected": False, "reason": "insufficient samples after filtering"}

    nperseg = int(min(filtered.size, max(128, fs * 8)))
    freqs, power = signal.welch(filtered, fs=fs, nperseg=nperseg)
    band = (freqs >= 0.5) & (freqs <= 4.0)
    if not band.any():
        return {"detected": False, "reason": "gait band empty"}

    band_freqs, band_power = freqs[band], power[band]
    peak_idx = int(np.argmax(band_power))
    peak_freq = float(band_freqs[peak_idx])
    prominence = float(band_power[peak_idx] / np.median(band_power))

    return {
        "detected": bool(prominence > 4.0),
        "vertical_axis": vertical_axis,
        "step_frequency_hz": peak_freq,
        "cadence_steps_per_min": peak_freq * 60.0,
        "peak_prominence_ratio": prominence,
        "confidence": "high" if prominence > 10 else ("moderate" if prominence > 4 else "low"),
        "bob_amplitude_rms": float(np.sqrt(np.mean(filtered**2))),
    }


# ============================ cue-aligned segmentation ============================


def segment_by_cues(session: Session) -> pd.DataFrame:
    """Split a trial into the intervals between commanded cues.

    Each row is one commanded movement, with the response actually measured
    during it. Reaction latency is the gap between the cue and the first
    sample exceeding the movement threshold.
    """
    marks = session.cue_marks()
    cues = marks[marks["kind"] == "cue"] if not marks.empty else marks
    df = session.motion
    if cues.empty or df.empty:
        return pd.DataFrame()

    speed = angular_speed(df)
    t = np.asarray(df["tPerf"], dtype="float64") / 1000.0
    valid = np.isfinite(speed) & np.isfinite(t)
    speed, t = speed[valid], t[valid]
    if speed.size == 0:
        return pd.DataFrame()

    # Movement onset threshold: well above resting noise, below a real turn.
    onset_threshold = max(10.0, float(np.percentile(speed, 20)) * 3)

    rows = []
    times = cues["t_s"].to_numpy()
    for i, start in enumerate(times):
        end = times[i + 1] if i + 1 < times.size else float(t[-1])
        window = (t >= start) & (t < end)
        if window.sum() < 2:
            continue
        seg_speed, seg_t = speed[window], t[window]
        above = np.flatnonzero(seg_speed > onset_threshold)
        rows.append({
            "cue_index": int(cues.iloc[i]["index"]) if pd.notna(cues.iloc[i]["index"]) else i,
            "label": cues.iloc[i]["label"],
            "start_s": float(start),
            "end_s": float(end),
            "n_samples": int(window.sum()),
            "peak_dps": float(np.max(seg_speed)),
            "mean_dps": float(np.mean(seg_speed)),
            "onset_latency_s": float(seg_t[above[0]] - start) if above.size else None,
        })
    return pd.DataFrame(rows)
