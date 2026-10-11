"""Head motion versus camera image shift — the IMU/image discrepancy analysis.

The measurement MWDAT 1.0 made possible, and the Web Apps API could not: the
glasses' IMU and their head-fixed camera streaming in the same session. The
head-motion lab (Android, ``ucf.visor.motionlab``) records every gyroscope
sample and, for every camera frame, the global image shift measured on the
phone by phase correlation. This module relates the two:

    image velocity v(t)  ≈  K · ω(t − τ)  +  b

where ω is the head's angular velocity (gyroscope, rad/s), v the image
velocity (px/s), τ the IMU-to-image lag and K a 2x3 matrix. For a pure
rotation seen by a pinhole camera, K is the focal length in pixels times the
two rows of the IMU-to-camera rotation perpendicular to the optical axis — so
fitting K recovers the axis mapping *and* the pixels-per-degree scale without
assuming either, and what the fit cannot explain is the **discrepancy**
between IMU-measured head movement and camera image shift.

What that discrepancy contains, and what this module does not claim:

* Head *translation* (parallax) moves near objects more than far ones and is
  not a rotation; roll turns the image about its centre, which a translation
  estimate does not capture; motion blur at high head speed and compressed,
  low-texture frames degrade the image measurement. The frame-quality gates
  (correlation peak, luma texture) exclude the worst of it.
* This is the camera's view, not the eye's. The camera is fixed to the head,
  so its image shift is the retinal slip an eye *without* a vestibulo-ocular
  reflex would see. Characterizing VOR function additionally needs eye
  movement; this analysis supplies the head-motion and head-fixed-image half
  of that measurement, with its timing, as a validated signal. It is not a
  VOR gain, and nothing here should be reported as one.

Clocks. Motion samples carry the glasses' monotonic clock; frames carry video
presentation timestamps, and the SDK does not document which clock those use.
:func:`clock_check` tests whether they are consistent with one shared clock.
If they are, the lag is measured on the device clock (a sensor-to-image
latency). If not, both streams are mapped onto the phone clock by their lower
envelopes and the lag is relative to each stream's fastest delivery — still
usable for alignment, but not an absolute latency. The report says which.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass
from typing import Any

import numpy as np
import pandas as pd

from .loader import SOURCE_GLASSES, Session, lower_envelope_offset

MIN_PEAK = 0.15          # correlation-peak gate, same as the phone's QuickLook
MIN_TEXTURE_SD = 4.0     # luma SD gate (0–255), same as the phone's QuickLook
MAX_LAG_MS = 400.0
LAG_STEP_MS = 1.0
MIN_INTERVALS = 30
SHARED_CLOCK_TOLERANCE_MS = 250.0
MIN_HEAD_SPEED_DPS = 3.0
# Below this share of image motion explained by head rotation, the lag and
# scale describe noise and are withheld — the same rule the static statistics
# follow: a number in a table gets quoted, a caveat does not.
MIN_RELIABLE_R2 = 0.5
AXES = ("x", "y", "z")


@dataclass
class ClockCheck:
    motion_origin_ns: int | None
    video_origin_us: int | None
    #: min(arrival − device time) per stream, with device times made absolute.
    motion_envelope_ms: float | None
    video_envelope_ms: float | None
    #: |motion envelope − video envelope|; small if both are one clock.
    envelope_disagreement_ms: float | None
    shared_clock_consistent: bool | None
    basis: str  # "device clock" or "phone arrival envelope"

    def as_dict(self) -> dict[str, Any]:
        return asdict(self)


def _glasses_motion(session: Session) -> pd.DataFrame:
    df = session.dat_motion
    if df.empty:
        return df
    if "source" in df:
        df = df[df["source"] == SOURCE_GLASSES]
    return df[np.isfinite(df["tDevice"]) & np.isfinite(df["tPhone"])].reset_index(drop=True)


def _int_or_none(value: Any) -> int | None:
    try:
        return int(value) if value is not None else None
    except (TypeError, ValueError):
        return None


def clock_check(session: Session) -> ClockCheck | None:
    """Are motion timestamps and video PTS on one clock? Decide the time basis."""
    motion = _glasses_motion(session)
    video = session.dat_video
    if motion.empty or video.empty:
        return None
    m0 = _int_or_none(session.clocks.get("motionDeviceOriginNs"))
    v0 = _int_or_none(session.clocks.get("videoPtsOriginUs"))

    shared: bool | None = None
    disagreement = None
    env_m = env_v = None
    if m0 is not None and v0 is not None:
        # Absolute device times in ms. Doubles hold these exactly enough: a
        # monotonic clock in ns is ~1e14, i.e. ~1e8 ms.
        motion_abs = m0 / 1e6 + motion["tDevice"].to_numpy()
        video_abs = v0 / 1e3 + video["tPts"].to_numpy()
        env_m = lower_envelope_offset(motion["tPhone"].to_numpy(), motion_abs)
        env_v = lower_envelope_offset(video["tPhone"].to_numpy(), video_abs)
        if env_m is not None and env_v is not None:
            disagreement = abs(env_m - env_v)
            shared = disagreement < SHARED_CLOCK_TOLERANCE_MS
    return ClockCheck(
        motion_origin_ns=m0,
        video_origin_us=v0,
        motion_envelope_ms=env_m,
        video_envelope_ms=env_v,
        envelope_disagreement_ms=disagreement,
        shared_clock_consistent=shared,
        basis="device clock" if shared else "phone arrival envelope",
    )


def _timelines(session: Session, clocks: ClockCheck) -> tuple[np.ndarray, np.ndarray, pd.DataFrame, pd.DataFrame]:
    """Motion and video times (ms) on one common basis."""
    motion = _glasses_motion(session)
    video = session.dat_video
    if clocks.basis == "device clock":
        t_motion = clocks.motion_origin_ns / 1e6 + motion["tDevice"].to_numpy()
        t_video = clocks.video_origin_us / 1e3 + video["tPts"].to_numpy()
    else:
        om = lower_envelope_offset(motion["tPhone"].to_numpy(), motion["tDevice"].to_numpy()) or 0.0
        ov = lower_envelope_offset(video["tPhone"].to_numpy(), video["tPts"].to_numpy()) or 0.0
        t_motion = motion["tDevice"].to_numpy() + om
        t_video = video["tPts"].to_numpy() + ov
    return t_motion, t_video, motion, video


def frame_intervals(t_video: np.ndarray, video: pd.DataFrame) -> pd.DataFrame:
    """Quality-gated frame-to-frame image velocities (px/s) on the common basis."""
    rows = []
    ref = video["refIndex"].to_numpy()
    for c in range(len(video)):
        r = ref[c]
        if not np.isfinite(r):
            continue
        r = int(r)
        if r < 0 or r >= c:
            continue
        peak = video["peak"].iat[c]
        texture = video["textureSd"].iat[c]
        sx, sy = video["shiftX"].iat[c], video["shiftY"].iat[c]
        if not (peak >= MIN_PEAK and texture >= MIN_TEXTURE_SD and np.isfinite(sx) and np.isfinite(sy)):
            continue
        start, end = t_video[r], t_video[c]
        dt = (end - start) / 1000.0
        if not dt > 0:
            continue
        rows.append({"start_ms": start, "end_ms": end, "vx": sx / dt, "vy": sy / dt, "peak": peak})
    return pd.DataFrame(rows)


def interval_rates(t_ms: np.ndarray, gyro: np.ndarray, starts: np.ndarray, ends: np.ndarray) -> np.ndarray:
    """Mean angular velocity (rad/s) over each [start, end] interval, per axis.

    Integrates the gyroscope with the trapezoidal rule and differences the
    running integral, so every sample inside the interval counts — not just
    the one nearest its middle.
    """
    gyro = np.where(np.isfinite(gyro), gyro, 0.0)
    dt = np.diff(t_ms) / 1000.0
    cumulative = np.zeros_like(gyro)
    cumulative[1:] = np.cumsum(0.5 * (gyro[1:] + gyro[:-1]) * dt[:, None], axis=0)
    out = np.empty((starts.size, gyro.shape[1]))
    for axis in range(gyro.shape[1]):
        a = np.interp(starts, t_ms, cumulative[:, axis])
        b = np.interp(ends, t_ms, cumulative[:, axis])
        out[:, axis] = (b - a) / ((ends - starts) / 1000.0)
    return out


def _fit(w: np.ndarray, v: np.ndarray) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Least squares v = w·Kᵀ + b. Returns K (2x3), b (2), residuals (m x 2)."""
    design = np.column_stack([w, np.ones(len(w))])
    coef, *_ = np.linalg.lstsq(design, v, rcond=None)
    k = coef[:3].T
    b = coef[3]
    return k, b, v - design @ coef


def _r2(v: np.ndarray, residual: np.ndarray) -> np.ndarray:
    total = ((v - v.mean(axis=0)) ** 2).sum(axis=0)
    return 1.0 - (residual ** 2).sum(axis=0) / np.where(total > 0, total, np.nan)


def _stimulus_frequency(session: Session) -> float | None:
    """Paced-trial frequency in Hz: one full cycle is two alternating cue steps."""
    cue = session.meta.get("cue") or {}
    interval = cue.get("intervalMs")
    steps = cue.get("steps") or []
    if not interval or len(steps) != 2:
        return None
    return 1000.0 / (2.0 * float(interval))


def _sinusoid(t_s: np.ndarray, x: np.ndarray, f0: float) -> tuple[float, float]:
    """Amplitude and phase (rad) of the f0 component, by least squares."""
    design = np.column_stack([np.cos(2 * np.pi * f0 * t_s), np.sin(2 * np.pi * f0 * t_s), np.ones_like(t_s)])
    (a, b, _), *_ = np.linalg.lstsq(design, x, rcond=None)
    return float(np.hypot(a, b)), float(np.arctan2(-b, a))


def analyze(session: Session) -> dict[str, Any] | None:
    """Relate head rotation to image shift for one native camera session."""
    if not session.is_native or session.dat_video.empty:
        return None
    clocks = clock_check(session)
    if clocks is None:
        return {"analyzed": False, "reason": "no motion or no video"}
    if clocks.basis == "device clock" and (clocks.motion_origin_ns is None or clocks.video_origin_us is None):
        clocks.basis = "phone arrival envelope"
    t_motion, t_video, motion, video = _timelines(session, clocks)
    result: dict[str, Any] = {"analyzed": False, "clocks": clocks.as_dict()}

    gyro = motion[["gx", "gy", "gz"]].to_numpy(dtype="float64")
    head_rms_dps = float(np.sqrt(np.nanmean(np.sum(gyro ** 2, axis=1)))) * 180 / np.pi
    result["head_speed_rms_dps"] = head_rms_dps
    intervals = frame_intervals(t_video, video)
    analyzable = int(np.isfinite(video["refIndex"]).sum())
    result["frames"] = int(len(video))
    result["frame_pairs_measured"] = analyzable
    result["frame_pairs_passing_quality"] = int(len(intervals))
    result["tracked_fraction"] = float(len(intervals) / analyzable) if analyzable else 0.0
    if head_rms_dps < MIN_HEAD_SPEED_DPS:
        result["reason"] = f"too little head rotation ({head_rms_dps:.1f} °/s RMS)"
        return result
    if len(intervals) < MIN_INTERVALS:
        result["reason"] = f"only {len(intervals)} frame pairs passed the image-quality gates"
        return result

    # One fixed interval set for every candidate lag: those the IMU covers at
    # both extremes of the search.
    lo, hi = t_motion[0] + MAX_LAG_MS, t_motion[-1] - MAX_LAG_MS
    intervals = intervals[(intervals["start_ms"] >= lo) & (intervals["end_ms"] <= hi)].reset_index(drop=True)
    if len(intervals) < MIN_INTERVALS:
        result["reason"] = "the motion recording does not cover enough of the video"
        return result
    starts = intervals["start_ms"].to_numpy()
    ends = intervals["end_ms"].to_numpy()
    v = intervals[["vx", "vy"]].to_numpy()

    lags = np.arange(-MAX_LAG_MS, MAX_LAG_MS + LAG_STEP_MS / 2, LAG_STEP_MS)
    scores = np.empty(lags.size)
    for i, lag in enumerate(lags):
        w = interval_rates(t_motion, gyro, starts - lag, ends - lag)
        _, _, residual = _fit(w, v)
        scores[i] = 1.0 - (residual ** 2).sum() / ((v - v.mean(axis=0)) ** 2).sum()
    best = int(np.argmax(scores))
    lag = float(lags[best])
    w = interval_rates(t_motion, gyro, starts - lag, ends - lag)
    k, b, residual = _fit(w, v)
    r2 = _r2(v, residual)

    dominant = int(np.argmax(np.var(w, axis=0)))
    # A column of K is only determined by the data if the trial actually
    # rotated about that axis; otherwise it is fitted to gyroscope noise and
    # means nothing. Report unexcited columns as null rather than as numbers.
    excited = np.sqrt(np.mean(w ** 2, axis=0)) * 180 / np.pi >= MIN_HEAD_SPEED_DPS
    column = k[:, dominant]
    scale_px_per_rad = float(np.linalg.norm(column))
    direction = column / scale_px_per_rad if scale_px_per_rad > 0 else column
    # The image motion along the direction the dominant rotation moves it,
    # converted back into an angular velocity: what the camera says the head did.
    omega_image = (v - b) @ direction / scale_px_per_rad
    omega_imu = w[:, dominant]
    residual_dps = float(np.sqrt(np.mean((omega_image - omega_imu) ** 2))) * 180 / np.pi
    slope = float(np.polyfit(omega_imu, omega_image, 1)[0])

    reliable = bool(scores[best] >= MIN_RELIABLE_R2)
    if not reliable:
        result["reason"] = (
            f"the image motion is not explained by head rotation (R² {scores[best]:.2f}): the "
            "scene itself may have been moving, too plain to track, or the head was translating "
            "rather than rotating")
    result.update({
        "analyzed": True,
        "reliable": reliable,
        "intervals_used": int(len(intervals)),
        "lag_ms": lag,
        "lag_at_search_edge": bool(best in (0, lags.size - 1)),
        "lag_curve": {"lag_ms": lags[:: max(1, int(10 / LAG_STEP_MS))].tolist(),
                      "r2": scores[:: max(1, int(10 / LAG_STEP_MS))].tolist()},
        "r2_total": float(scores[best]),
        "r2_image_x": float(r2[0]),
        "r2_image_y": float(r2[1]),
        "excited_axes": [AXES[j] for j in range(3) if excited[j]],
        # Rows: image x, image y. Columns: glasses x, y, z; null where unexcited.
        "k_px_per_rad": [[float(k[i, j]) if excited[j] else None for j in range(3)] for i in range(2)],
        "bias_px_per_s": b.tolist(),
        "dominant_gyro_axis": AXES[dominant],
        "image_direction_of_dominant_rotation": direction.tolist(),
        "scale_px_per_deg": scale_px_per_rad * np.pi / 180,
        "residual_dps": residual_dps,
        "residual_fraction_of_head_speed": residual_dps / head_rms_dps if head_rms_dps else None,
        "image_to_imu_slope": slope,
    })

    f0 = _stimulus_frequency(session)
    if f0:
        # At the paced frequency, independently of the broadband fit: the
        # head's angular velocity over each frame interval *unaligned* (lag 0),
        # and the raw image velocity along the direction the dominant rotation
        # moves the image. Their amplitude ratio is a second scale estimate;
        # their phase difference is the image's lag at f0 — a cross-check on
        # the cross-correlation lag that uses only the paced component.
        mid = (starts + ends) / 2000.0
        head = interval_rates(t_motion, gyro, starts, ends)[:, dominant]
        image_px = (v - b) @ direction
        amp_head, phase_head = _sinusoid(mid, head, f0)
        amp_image, phase_image = _sinusoid(mid, image_px, f0)
        phase_deg = float(np.degrees(np.angle(np.exp(1j * (phase_head - phase_image)))))
        head_dps = amp_head * 180 / np.pi
        result["stimulus"] = {
            "frequency_hz": f0,
            "head_amplitude_dps": head_dps,
            "image_amplitude_px_per_s": amp_image,
            "scale_at_stimulus_px_per_deg": amp_image / head_dps if head_dps > 0 else None,
            "image_phase_lag_deg": phase_deg,
            "image_phase_lag_ms": phase_deg / 360.0 / f0 * 1000.0,
        }
    return result


def image_angular_velocity(session: Session, image_motion: dict[str, Any],
                           scale_px_per_deg: float | None = None) -> pd.DataFrame | None:
    """The camera's view of head rotation, one row per quality-gated frame interval.

    Image velocity along the direction the dominant rotation moves the image,
    in °/s at ``scale_px_per_deg`` (default: the fit's own scale; pass the slow-pan
    calibration to compare against a scale measured at distance). Times are
    shifted back by the fitted lag and onto the motion stream's ``tPerf`` base
    (s), so the result overlays the gyroscope in ``session.motion``.
    """
    if not image_motion.get("analyzed"):
        return None
    clocks = clock_check(session)
    if clocks is None:
        return None
    if clocks.basis == "device clock" and (clocks.motion_origin_ns is None or clocks.video_origin_us is None):
        clocks.basis = "phone arrival envelope"
    t_motion, t_video, motion, video = _timelines(session, clocks)
    intervals = frame_intervals(t_video, video)
    if intervals.empty:
        return None
    # tPerf is the device time re-based by the motion stream's lower envelope.
    t_perf = motion["tDevice"].to_numpy() + (
        lower_envelope_offset(motion["tPhone"].to_numpy(), motion["tDevice"].to_numpy()) or 0.0)
    shift = float(np.median(t_perf - t_motion))
    direction = np.asarray(image_motion["image_direction_of_dominant_rotation"])
    bias = np.asarray(image_motion["bias_px_per_s"])
    scale = scale_px_per_deg or image_motion["scale_px_per_deg"]
    mid = (intervals["start_ms"] + intervals["end_ms"]).to_numpy() / 2.0 - image_motion["lag_ms"] + shift
    return pd.DataFrame({
        "t_s": mid / 1000.0,
        "image_dps": (intervals[["vx", "vy"]].to_numpy() - bias) @ direction / scale,
        "peak": intervals["peak"].to_numpy(),
    })


def attach_calibration(results: list[dict[str, Any]]) -> None:
    """Scale each camera trial against its participant's slow-pan calibration.

    The slow pan (V4) has the least motion blur, so its pixels-per-degree is
    the best available estimate of the camera's own scale. A faster trial whose
    scale departs from it is one where the image stopped keeping up with the
    head — a discrepancy worth reporting, not a new focal length.
    """
    reference: dict[str, list[float]] = {}
    for r in results:
        im = r.get("image_motion") or {}
        if r.get("trial_id", "").startswith("V4") and im.get("reliable"):
            reference.setdefault(r.get("participant", "anon"), []).append(im["scale_px_per_deg"])
    for r in results:
        im = r.get("image_motion") or {}
        ref = reference.get(r.get("participant", "anon"))
        if im.get("reliable") and ref:
            f_ref = float(np.median(ref))
            im["calibration_scale_px_per_deg"] = f_ref
            im["scale_vs_calibration"] = im["scale_px_per_deg"] / f_ref
            stimulus = im.get("stimulus") or {}
            if stimulus.get("scale_at_stimulus_px_per_deg"):
                # Image motion per degree of head motion at the paced frequency,
                # relative to the camera's calibrated scale: 1.0 means the image
                # moved exactly as a pure head rotation predicts.
                stimulus["calibrated_image_gain"] = stimulus["scale_at_stimulus_px_per_deg"] / f_ref
