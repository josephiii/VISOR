"""Instrument characterization metrics.

Timing/jitter, static noise and bias, Allan deviation, and power spectral
density. These are the measures an engineering reviewer will expect to see
before believing any downstream claim about what the sensor can do.
"""

from __future__ import annotations

from dataclasses import dataclass, asdict
from typing import Any

import numpy as np
import pandas as pd
from scipy import signal

GRAVITY = 9.80665

ACCEL_AXES = ("agx", "agy", "agz")
GYRO_AXES = ("rrAlpha", "rrBeta", "rrGamma")


def _finite(values: pd.Series | np.ndarray) -> np.ndarray:
    arr = np.asarray(values, dtype="float64")
    return arr[np.isfinite(arr)]


# ============================ timing ============================


@dataclass
class TimingStats:
    n_samples: int
    duration_s: float
    effective_hz: float
    nominal_hz_from_interval: float | None
    dt_mean_ms: float
    dt_median_ms: float
    dt_sd_ms: float
    dt_p95_ms: float
    dt_p99_ms: float
    dt_min_ms: float
    dt_max_ms: float
    jitter_cv: float
    dropout_count: int
    dropout_fraction: float
    longest_gap_ms: float
    clock_slope: float | None
    clock_residual_sd_ms: float | None

    def as_dict(self) -> dict[str, Any]:
        return asdict(self)


def timing_stats(df: pd.DataFrame, dropout_factor: float = 3.0) -> TimingStats | None:
    """Sample-rate stability for one stream.

    ``dropout_factor`` defines a gap: any inter-sample interval more than this
    multiple of the median is counted as a dropped sample window.
    """
    if df is None or df.empty or "tPerf" not in df or len(df) < 3:
        return None

    t = _finite(df["tPerf"])
    if t.size < 3:
        return None

    dt = np.diff(t)
    dt = dt[dt > 0]
    if dt.size == 0:
        return None

    median = float(np.median(dt))
    duration_s = float(t[-1] - t[0]) / 1000.0
    threshold = dropout_factor * median
    dropouts = int(np.count_nonzero(dt > threshold))

    # The platform advertises its own sampling interval on devicemotion events.
    nominal = None
    if "interval" in df:
        iv = _finite(df["interval"])
        if iv.size and np.median(iv) > 0:
            nominal = float(1000.0 / np.median(iv))

    # Does the event clock advance at the same rate as performance.now()?
    slope = residual_sd = None
    if "tEvent" in df:
        te = np.asarray(df["tEvent"], dtype="float64")
        mask = np.isfinite(te) & np.isfinite(np.asarray(df["tPerf"], dtype="float64"))
        if mask.sum() > 10:
            tp = np.asarray(df["tPerf"], dtype="float64")[mask]
            tev = te[mask]
            if np.ptp(tp) > 0:
                coef = np.polyfit(tp, tev, 1)
                slope = float(coef[0])
                residual_sd = float(np.std(tev - np.polyval(coef, tp)))

    return TimingStats(
        n_samples=int(t.size),
        duration_s=duration_s,
        effective_hz=float(t.size / duration_s) if duration_s > 0 else 0.0,
        nominal_hz_from_interval=nominal,
        dt_mean_ms=float(np.mean(dt)),
        dt_median_ms=median,
        dt_sd_ms=float(np.std(dt)),
        dt_p95_ms=float(np.percentile(dt, 95)),
        dt_p99_ms=float(np.percentile(dt, 99)),
        dt_min_ms=float(np.min(dt)),
        dt_max_ms=float(np.max(dt)),
        jitter_cv=float(np.std(dt) / np.mean(dt)) if np.mean(dt) > 0 else float("nan"),
        dropout_count=dropouts,
        dropout_fraction=float(dropouts / dt.size),
        longest_gap_ms=float(np.max(dt)),
        clock_slope=slope,
        clock_residual_sd_ms=residual_sd,
    )


# ============================ static noise ============================


def static_axis_stats(df: pd.DataFrame, axes: tuple[str, ...]) -> dict[str, dict[str, float]]:
    """Per-axis bias and noise. Only meaningful on a stationary recording."""
    out: dict[str, dict[str, float]] = {}
    for axis in axes:
        if axis not in df:
            continue
        v = _finite(df[axis])
        if v.size < 2:
            out[axis] = {"n": int(v.size)}
            continue
        out[axis] = {
            "n": int(v.size),
            "mean": float(np.mean(v)),
            "sd": float(np.std(v, ddof=1)),
            "rms": float(np.sqrt(np.mean(v**2))),
            "min": float(np.min(v)),
            "max": float(np.max(v)),
            "peak_to_peak": float(np.ptp(v)),
        }
    return out


def gravity_check(df: pd.DataFrame) -> dict[str, float] | None:
    """Magnitude of the gravity vector while static.

    A stationary accelerometer should read |a| = 9.807 m/s². Deviation is a
    combined scale-factor and bias error; the spread is a noise estimate that
    is independent of how the glasses happened to be oriented.
    """
    cols = [c for c in ACCEL_AXES if c in df]
    if len(cols) != 3:
        return None
    arr = df[list(ACCEL_AXES)].to_numpy(dtype="float64")
    mask = np.all(np.isfinite(arr), axis=1)
    if mask.sum() < 2:
        return None
    mag = np.linalg.norm(arr[mask], axis=1)
    mean = float(np.mean(mag))
    return {
        "n": int(mag.size),
        "mean_magnitude": mean,
        "sd_magnitude": float(np.std(mag, ddof=1)),
        "expected": GRAVITY,
        "error": mean - GRAVITY,
        "error_pct": 100.0 * (mean - GRAVITY) / GRAVITY,
    }


# ============================ Allan deviation ============================


def allan_deviation(rate: np.ndarray, fs: float, n_taus: int = 60
                    ) -> tuple[np.ndarray, np.ndarray]:
    """Overlapping Allan deviation of a rate signal.

    Computed on the integrated signal θ (angle for a gyro, velocity for an
    accelerometer):

        σ²(τ) = 1 / (2τ²(N−2m)) · Σ (θ[k+2m] − 2θ[k+m] + θ[k])²

    The overlapping estimator is used rather than the non-overlapping one
    because it has substantially better confidence at long averaging times,
    which is exactly where bias instability is read off.
    """
    rate = _finite(rate)
    n = rate.size
    if n < 16 or fs <= 0:
        return np.array([]), np.array([])

    tau0 = 1.0 / fs
    theta = np.cumsum(rate) * tau0

    max_m = (n - 1) // 2
    if max_m < 1:
        return np.array([]), np.array([])

    ms = np.unique(np.floor(np.logspace(0, np.log10(max_m), n_taus)).astype(int))
    ms = ms[(ms >= 1) & (ms <= max_m)]

    taus, adevs = [], []
    for m in ms:
        k = n - 2 * m
        if k < 1:
            continue
        diff = theta[2 * m:] - 2.0 * theta[m:n - m] + theta[:n - 2 * m]
        tau = m * tau0
        var = np.sum(diff**2) / (2.0 * tau**2 * k)
        if var > 0:
            taus.append(tau)
            adevs.append(np.sqrt(var))

    return np.asarray(taus), np.asarray(adevs)


def allan_params(taus: np.ndarray, adev: np.ndarray) -> dict[str, float | None]:
    """Extract the standard noise coefficients from an Allan deviation curve.

    Random walk is read at τ = 1 s on the −1/2 slope; bias instability is the
    curve minimum divided by 0.664 (the standard scaling for the flat region).
    Units follow the input: deg/s in gives deg/√hr random walk.
    """
    if taus.size == 0:
        return {"random_walk": None, "bias_instability": None, "bias_instability_tau_s": None}

    # Random walk: interpolate the curve at tau = 1 s where possible.
    random_walk = None
    if taus.min() <= 1.0 <= taus.max():
        random_walk = float(np.interp(1.0, taus, adev))
    elif taus.min() > 1.0:
        # Extrapolate down the -1/2 slope from the shortest available tau.
        random_walk = float(adev[0] * np.sqrt(taus[0]))

    idx = int(np.argmin(adev))
    return {
        "random_walk": random_walk,
        "random_walk_per_sqrt_hr": random_walk * 60.0 if random_walk is not None else None,
        "bias_instability": float(adev[idx] / 0.664),
        "bias_instability_tau_s": float(taus[idx]),
        "min_adev": float(adev[idx]),
    }


# ============================ spectrum ============================


def psd(values: np.ndarray, fs: float, nperseg: int | None = None
        ) -> tuple[np.ndarray, np.ndarray]:
    """Welch power spectral density. Returns (freqs, density)."""
    v = _finite(values)
    if v.size < 32 or fs <= 0:
        return np.array([]), np.array([])
    if nperseg is None:
        nperseg = int(min(1024, max(64, 2 ** int(np.log2(v.size / 4) or 6))))
    nperseg = min(nperseg, v.size)
    freqs, density = signal.welch(v - np.mean(v), fs=fs, nperseg=nperseg)
    return freqs, density


def estimate_fs(df: pd.DataFrame) -> float:
    """Median-based sample-rate estimate, robust to dropouts."""
    if df is None or df.empty or "tPerf" not in df or len(df) < 3:
        return 0.0
    t = _finite(df["tPerf"])
    if t.size < 3:
        return 0.0
    dt = np.diff(t)
    dt = dt[dt > 0]
    if dt.size == 0:
        return 0.0
    return float(1000.0 / np.median(dt))


# ============================ saturation ============================


def stationarity_check(df: pd.DataFrame, gyro_sd_limit: float = 2.0,
                       accel_p2p_limit: float = 1.0) -> dict[str, Any]:
    """Decide whether a recording is actually stationary.

    Bias, noise and Allan statistics are only meaningful on a genuinely
    stationary device. Computing them on a handled or worn recording yields
    confident numbers that describe the operator's movement, not the sensor —
    and those numbers look exactly like hardware specifications in a report.

    Thresholds are deliberately loose: a device resting on a table sits far
    below them, while anything held or worn sits far above.
    """
    result: dict[str, Any] = {"checked": False, "stationary": None, "reasons": []}

    gyro_sds = {}
    for axis in GYRO_AXES:
        if axis not in df:
            continue
        v = _finite(df[axis])
        if v.size > 2:
            gyro_sds[axis] = float(np.std(v, ddof=1))

    accel_p2p = {}
    for axis in ACCEL_AXES:
        if axis not in df:
            continue
        v = _finite(df[axis])
        if v.size > 2:
            accel_p2p[axis] = float(np.ptp(v))

    if not gyro_sds and not accel_p2p:
        return result

    result["checked"] = True
    result["gyro_sd_dps"] = gyro_sds
    result["accel_peak_to_peak"] = accel_p2p
    result["gyro_sd_limit"] = gyro_sd_limit
    result["accel_p2p_limit"] = accel_p2p_limit

    worst_gyro = max(gyro_sds.values()) if gyro_sds else 0.0
    worst_accel = max(accel_p2p.values()) if accel_p2p else 0.0

    if worst_gyro > gyro_sd_limit:
        result["reasons"].append(
            f"gyro SD {worst_gyro:.2f} °/s exceeds {gyro_sd_limit} °/s")
    if worst_accel > accel_p2p_limit:
        result["reasons"].append(
            f"accel peak-to-peak {worst_accel:.2f} m/s² exceeds {accel_p2p_limit} m/s²")

    result["stationary"] = not result["reasons"]
    return result


def duration_check(df: pd.DataFrame, planned_sec: float | None,
                   duration_s: float, tolerance: float = 0.02) -> dict[str, Any]:
    """Compare achieved duration against the trial's planned duration."""
    if not planned_sec:
        return {"checked": False}
    ratio = duration_s / planned_sec if planned_sec else 0.0
    return {
        "checked": True,
        "planned_s": planned_sec,
        "actual_s": duration_s,
        "completion_fraction": ratio,
        "within_tolerance": abs(1.0 - ratio) <= tolerance,
    }


def saturation_check(df: pd.DataFrame, axes: tuple[str, ...]) -> dict[str, dict[str, Any]]:
    """Look for clipping: repeated samples sitting exactly at the extreme value.

    A gyro driven past its full-scale range flat-tops. If the maximum value
    recurs many times, the trial exceeded the sensor's range and any peak-rate
    figure from it is a lower bound, not a measurement.
    """
    out: dict[str, dict[str, Any]] = {}
    for axis in axes:
        if axis not in df:
            continue
        v = _finite(df[axis])
        if v.size < 10:
            continue
        vmax, vmin = float(np.max(v)), float(np.min(v))
        at_max = int(np.count_nonzero(np.isclose(v, vmax, rtol=1e-9, atol=1e-9)))
        at_min = int(np.count_nonzero(np.isclose(v, vmin, rtol=1e-9, atol=1e-9)))
        out[axis] = {
            "max": vmax,
            "min": vmin,
            "samples_at_max": at_max,
            "samples_at_min": at_min,
            # A handful of repeats is chance; a sustained run indicates clipping.
            "suspected_clipping": bool(at_max > 5 or at_min > 5),
        }
    return out
