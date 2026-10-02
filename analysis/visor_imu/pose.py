"""Head pose estimation from the IMU alone (gyroscope + accelerometer).

Six degrees of freedom split cleanly into two problems of very different
difficulty, and this module treats them separately:

* **Orientation (roll, pitch, yaw).** Gyro rate is integrated into a quaternion
  and continuously corrected toward the gravity direction the accelerometer
  sees (a Mahony complementary filter). Roll and pitch are therefore bounded;
  yaw has no gravity reference and drifts at the residual gyro bias.
* **Translation (x, y, z).** Acceleration must be rotated into the world frame,
  have gravity removed, and be integrated *twice*. Any residual — bias, the
  0.1 m/s² quantization the browser applies, or gravity leaking through a small
  tilt error — grows quadratically in position. Unaided, this diverges within
  seconds. It only becomes usable under a constraint: zero-velocity updates
  (ZUPT) when the head is known to be still, or step-based pedestrian dead
  reckoning (PDR) while walking.

Body frame, established empirically from the recordings (see
:func:`axis_mapping_scores`): **x = wearer's right, y = up, z = backward**
(toward the face), right-handed, with gyro channels mapping
``rrAlpha → ωx`` (pitch), ``rrBeta → ωy`` (yaw), ``rrGamma → ωz`` (roll).
This is *not* the W3C-spec assignment (alpha about z, beta about x, gamma about
y); it matches Chromium's long-standing ordering of ``rotationRate``.

World frame: X = initial forward heading, Y = left, Z = up.
"""

from __future__ import annotations

import itertools
from dataclasses import dataclass, field
from typing import Any

import numpy as np
import pandas as pd
from scipy import signal
from scipy.integrate import cumulative_trapezoid

from .loader import Session
from .metrics import GRAVITY, GYRO_HEAD_AXIS

# Gyro channel order that gives body (x, y, z) rates. Kept as data, not
# hard-wired indexing, so the mapping test and the filter share one definition.
BODY_GYRO = ("rrAlpha", "rrBeta", "rrGamma")
BODY_ACCEL = ("agx", "agy", "agz")
BODY_LINEAR = ("ax", "ay", "az")

# Head-anatomical meaning of each body gyro axis, from the mapping above.
HEAD_AXIS = GYRO_HEAD_AXIS

# Largest gap bridged by integration. A longer hole means the stream paused
# (e.g. the app was backgrounded) and integrating across it would invent motion.
MAX_DT_S = 0.1


# ============================ inputs ============================


@dataclass
class ImuArrays:
    """Time-aligned, finite IMU samples in SI units and the body frame."""

    t: np.ndarray            # s, from session start
    gyro: np.ndarray         # rad/s, (N, 3) body x/y/z
    accel: np.ndarray        # m/s², (N, 3) specific force incl. gravity
    linear: np.ndarray       # m/s², (N, 3) platform gravity-removed acceleration
    fs: float

    @property
    def dt(self) -> np.ndarray:
        d = np.diff(self.t, prepend=self.t[0])
        return np.clip(d, 0.0, MAX_DT_S)


def imu_arrays(session: Session) -> ImuArrays | None:
    df = session.motion
    needed = ("tPerf", *BODY_GYRO, *BODY_ACCEL)
    if df.empty or any(c not in df for c in needed):
        return None
    frame = df.dropna(subset=list(needed))
    if len(frame) < 32:
        return None
    t = frame["tPerf"].to_numpy(dtype="float64") / 1000.0
    lin_cols = [c for c in BODY_LINEAR if c in frame]
    linear = (frame[lin_cols].to_numpy(dtype="float64") if len(lin_cols) == 3
              else np.full((len(frame), 3), np.nan))
    return ImuArrays(
        t=t,
        gyro=np.deg2rad(frame[list(BODY_GYRO)].to_numpy(dtype="float64")),
        accel=frame[list(BODY_ACCEL)].to_numpy(dtype="float64"),
        linear=linear,
        fs=float(1.0 / np.median(np.diff(t))),
    )


# ============================ axis identification ============================


def axis_mapping_scores(sessions: list[Session], window_s: float = 1.5,
                        lowpass_hz: float = 2.0) -> pd.DataFrame:
    """Rank every signed channel→axis assignment by physical consistency.

    Over short windows, the gyro alone predicts how the gravity vector should
    move in the body frame (dg/dt = −ω × g). The accelerometer measures where it
    actually went. The correct assignment minimizes the disagreement; a wrong
    one, however plausible its labels, cannot. This settles the axis question
    from the data rather than from the spec or from channel names.
    """
    names = BODY_GYRO
    scores: dict[tuple, list[float]] = {}
    for session in sessions:
        imu = imu_arrays(session)
        if imu is None:
            continue
        sos = signal.butter(2, lowpass_hz / (imu.fs / 2.0), output="sos")
        acc = signal.sosfiltfilt(sos, imu.accel, axis=0)
        dt = imu.dt
        win = int(window_s * imu.fs)
        starts = range(0, len(imu.t) - win, win)
        for perm in itertools.permutations(range(3)):
            for signs in itertools.product((1, -1), repeat=3):
                w = imu.gyro[:, perm] * np.asarray(signs)
                errs = []
                for st in starts:
                    g = acc[st].copy()
                    for k in range(st + 1, st + win):
                        g = g - np.cross(w[k], g) * dt[k]
                    errs.append(np.linalg.norm(g - acc[st + win - 1]))
                if errs:
                    scores.setdefault((perm, signs), []).append(float(np.median(errs)))

    rows = []
    for (perm, signs), errs in scores.items():
        label = ", ".join(f"ω{'xyz'[i]}={'+' if signs[i] > 0 else '−'}{names[perm[i]]}"
                          for i in range(3))
        rows.append({
            "mapping": label,
            "perm": perm,
            "signs": signs,
            "residual_ms2": float(np.mean(errs)),
            "is_w3c_spec": perm == (1, 2, 0) and signs == (1, 1, 1),
            "is_adopted": perm == (0, 1, 2) and signs == (1, 1, 1),
        })
    if not rows:
        return pd.DataFrame()
    return pd.DataFrame(rows).sort_values("residual_ms2").reset_index(drop=True)


def axis_energy_share(session: Session) -> dict[str, float]:
    """Fraction of total gyro RMS carried by each channel."""
    df = session.motion
    rms = {c: float(np.sqrt(np.nanmean(df[c].to_numpy(dtype="float64") ** 2)))
           for c in BODY_GYRO if c in df}
    total = sum(rms.values())
    return {c: v / total for c, v in rms.items()} if total > 0 else {}


# ============================ quaternions ============================


def _qmul(a: np.ndarray, b: np.ndarray) -> np.ndarray:
    w1, x1, y1, z1 = a
    w2, x2, y2, z2 = b
    return np.array([
        w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2,
        w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
        w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2,
        w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2,
    ])


def quat_to_matrix(q: np.ndarray) -> np.ndarray:
    """Body→world rotation matrices for an (N, 4) or (4,) quaternion array."""
    q = np.atleast_2d(q)
    w, x, y, z = q.T
    r = np.empty((q.shape[0], 3, 3))
    r[:, 0, 0] = 1 - 2 * (y * y + z * z)
    r[:, 0, 1] = 2 * (x * y - w * z)
    r[:, 0, 2] = 2 * (x * z + w * y)
    r[:, 1, 0] = 2 * (x * y + w * z)
    r[:, 1, 1] = 1 - 2 * (x * x + z * z)
    r[:, 1, 2] = 2 * (y * z - w * x)
    r[:, 2, 0] = 2 * (x * z - w * y)
    r[:, 2, 1] = 2 * (y * z + w * x)
    r[:, 2, 2] = 1 - 2 * (x * x + y * y)
    return r


def _matrix_to_quat(r: np.ndarray) -> np.ndarray:
    tr = np.trace(r)
    if tr > 0:
        s = 2.0 * np.sqrt(tr + 1.0)
        q = [0.25 * s, (r[2, 1] - r[1, 2]) / s, (r[0, 2] - r[2, 0]) / s, (r[1, 0] - r[0, 1]) / s]
    else:
        i = int(np.argmax(np.diag(r)))
        j, k = (i + 1) % 3, (i + 2) % 3
        s = 2.0 * np.sqrt(1.0 + r[i, i] - r[j, j] - r[k, k])
        q = [0.0] * 4
        q[0] = (r[k, j] - r[j, k]) / s
        q[1 + i] = 0.25 * s
        q[1 + j] = (r[j, i] + r[i, j]) / s
        q[1 + k] = (r[k, i] + r[i, k]) / s
    q = np.asarray(q)
    return q / np.linalg.norm(q)


def initial_alignment(accel: np.ndarray) -> np.ndarray:
    """Quaternion that levels the body using gravity and faces forward along +X.

    Heading is arbitrary without a magnetometer, so the initial forward
    direction (-z body, projected on the horizontal) defines world +X.
    """
    up_b = accel / np.linalg.norm(accel)
    fwd_b = np.array([0.0, 0.0, -1.0])
    fwd_b = fwd_b - np.dot(fwd_b, up_b) * up_b
    fwd_b /= np.linalg.norm(fwd_b)
    left_b = np.cross(up_b, fwd_b)
    # Rows are world X (forward), Y (left), Z (up) expressed in body coordinates,
    # so this matrix maps body→world.
    r = np.vstack([fwd_b, left_b, up_b])
    return _matrix_to_quat(r)


# ============================ attitude filter ============================


@dataclass
class AttitudeTrack:
    t: np.ndarray
    q: np.ndarray                 # (N, 4) body→world
    bias: np.ndarray              # (N, 3) estimated gyro bias, rad/s
    g_local: float

    @property
    def R(self) -> np.ndarray:
        return quat_to_matrix(self.q)

    def head_angles(self) -> pd.DataFrame:
        return head_angles(self.R, self.t)


def mahony(imu: ImuArrays, kp: float = 1.0, ki: float = 0.02,
           accel_gate_ms2: float = 0.8, use_accel: bool = True,
           init_s: float = 0.25) -> AttitudeTrack:
    """Mahony complementary filter, 6-axis (no magnetometer).

    The accelerometer correction is weighted down when |f| departs from g,
    because then the accelerometer is seeing head acceleration as well as
    gravity and would pull the estimate the wrong way. ``use_accel=False``
    gives pure gyro integration for comparison.
    """
    n = len(imu.t)
    dt = imu.dt
    acc_norm = np.linalg.norm(imu.accel, axis=1)
    init = imu.t <= imu.t[0] + init_s
    q = initial_alignment(np.mean(imu.accel[init], axis=0))

    still = np.abs(acc_norm - np.median(acc_norm)) < 0.15
    g_local = float(np.median(acc_norm[still])) if still.any() else GRAVITY

    qs = np.empty((n, 4))
    biases = np.zeros((n, 3))
    integral = np.zeros(3)
    for k in range(n):
        omega = imu.gyro[k].copy()
        if use_accel and acc_norm[k] > 0:
            a = imu.accel[k] / acc_norm[k]
            r = quat_to_matrix(q)[0]
            v = r[2, :]                      # world up expressed in body frame
            err = np.cross(a, v)
            weight = np.exp(-((acc_norm[k] - g_local) / accel_gate_ms2) ** 2)
            if ki > 0:
                integral += ki * err * weight * dt[k]
            omega = omega + kp * weight * err + integral
        half = 0.5 * omega * dt[k]
        angle = np.linalg.norm(half)
        if angle > 0:
            dq = np.concatenate([[np.cos(angle)], np.sin(angle) * half / angle])
            q = _qmul(q, dq)
            q /= np.linalg.norm(q)
        qs[k] = q
        biases[k] = -integral
    return AttitudeTrack(t=imu.t, q=qs, bias=biases, g_local=g_local)


def head_angles(R: np.ndarray, t: np.ndarray) -> pd.DataFrame:
    """Yaw / pitch / roll of the head from body→world rotation matrices.

    Yaw: heading of the gaze direction (+ = left). Pitch: elevation of the gaze
    direction (+ = up). Roll: tilt of the ear-to-ear axis (+ = left ear down).
    Defined geometrically rather than as an Euler sequence so the three read as
    the anatomical movements the trials command.
    """
    fwd = R @ np.array([0.0, 0.0, -1.0])
    right = R @ np.array([1.0, 0.0, 0.0])
    up = R @ np.array([0.0, 1.0, 0.0])
    yaw = np.unwrap(np.arctan2(fwd[:, 1], fwd[:, 0]))
    pitch = np.arcsin(np.clip(fwd[:, 2], -1, 1))
    roll = np.arctan2(right[:, 2], up[:, 2])
    return pd.DataFrame({"t": t, "yaw": np.rad2deg(yaw), "pitch": np.rad2deg(pitch),
                         "roll": np.rad2deg(roll)})


# ============================ platform reference ============================


def platform_rotation(alpha: np.ndarray, beta: np.ndarray, gamma: np.ndarray) -> np.ndarray:
    """W3C deviceorientation angles → device→earth (ENU) rotation matrices.

    The spec defines the intrinsic Z-X'-Y'' sequence R = Rz(α)·Rx(β)·Ry(γ).
    """
    a, b, g = (np.deg2rad(np.asarray(v, dtype="float64")) for v in (alpha, beta, gamma))
    ca, sa, cb, sb, cg, sg = np.cos(a), np.sin(a), np.cos(b), np.sin(b), np.cos(g), np.sin(g)
    r = np.empty((a.size, 3, 3))
    r[:, 0, 0] = ca * cg - sa * sb * sg
    r[:, 0, 1] = -cb * sa
    r[:, 0, 2] = ca * sg + cg * sa * sb
    r[:, 1, 0] = cg * sa + ca * sb * sg
    r[:, 1, 1] = ca * cb
    r[:, 1, 2] = sa * sg - ca * cg * sb
    r[:, 2, 0] = -cb * sg
    r[:, 2, 1] = sb
    r[:, 2, 2] = cb * cg
    return r


def platform_angles(session: Session) -> pd.DataFrame | None:
    """Head angles from the platform's absolute (compass-referenced) orientation.

    This stream is fused by the platform with the magnetometer and is not
    ground truth. It is an *independent* reference for heading, and a
    same-sensor cross-check for roll and pitch.
    """
    o = session.orientation
    if o.empty or any(c not in o for c in ("tPerf", "alpha", "beta", "gamma")):
        return None
    o = o.dropna(subset=["tPerf", "alpha", "beta", "gamma"])
    if len(o) < 10:
        return None
    R = platform_rotation(o["alpha"].to_numpy(), o["beta"].to_numpy(), o["gamma"].to_numpy())
    return head_angles(R, o["tPerf"].to_numpy(dtype="float64") / 1000.0)


def platform_gravity_residual(session: Session) -> float | None:
    """Check the deviceorientation convention: predicted vs measured gravity.

    If the platform angles follow the spec convention in the same body frame as
    the motion stream, R^T·ẑ·g must match the accelerometer at rest-ish moments.
    """
    imu = imu_arrays(session)
    o = session.orientation.dropna(subset=["tPerf", "alpha", "beta", "gamma"])
    if imu is None or len(o) < 10:
        return None
    R = platform_rotation(o["alpha"].to_numpy(), o["beta"].to_numpy(), o["gamma"].to_numpy())
    up_b = R[:, 2, :] * GRAVITY
    to = o["tPerf"].to_numpy(dtype="float64") / 1000.0
    acc = np.column_stack([np.interp(to, imu.t, imu.accel[:, i]) for i in range(3)])
    return float(np.median(np.linalg.norm(up_b - acc, axis=1)))


def heading_agreement(track: AttitudeTrack, platform: pd.DataFrame, lag_s: float = 0.25
                      ) -> dict[str, float]:
    """Does the platform's heading move with the gyro's yaw?

    Compares yaw *increments* over ``lag_s`` rather than absolute values, so an
    arbitrary heading offset or a slow drift cannot masquerade as agreement or
    disagreement. A trustworthy heading gives gain ≈ +1 and r ≈ +1.
    """
    mine = track.head_angles()
    t = platform["t"].to_numpy()
    inside = (t >= mine["t"].iloc[0]) & (t <= mine["t"].iloc[-1])
    t = t[inside]
    est = np.interp(t, mine["t"], mine["yaw"])
    ref = platform["yaw"].to_numpy()[inside]
    step = max(1, int(round(lag_s / np.median(np.diff(t)))))
    d_est = est[step:] - est[:-step]
    d_ref = np.rad2deg(np.angle(np.exp(1j * np.deg2rad(ref[step:] - ref[:-step]))))
    d_est, d_ref = d_est - d_est.mean(), d_ref - d_ref.mean()
    return {
        "gain": float(np.sum(d_est * d_ref) / np.sum(d_est ** 2)),
        "r": float(np.corrcoef(d_est, d_ref)[0, 1]),
        "lag_s": lag_s,
    }


def yaw_sweeps(angles: pd.DataFrame, min_amplitude_deg: float = 10.0,
               lowpass_hz: float = 1.5) -> pd.DataFrame:
    """Split a yaw trace into left and right sweeps between turning points.

    Works on the filter's heading angle, so amplitude is read directly as the
    change in heading between reversals rather than integrated from one gyro
    channel.
    """
    t = angles["t"].to_numpy()
    yaw = angles["yaw"].to_numpy()
    fs = 1.0 / np.median(np.diff(t))
    sos = signal.butter(2, lowpass_hz / (fs / 2.0), output="sos")
    smooth = signal.sosfiltfilt(sos, yaw)
    rate = np.gradient(smooth, t)
    turns = np.flatnonzero(np.diff(np.sign(rate)) != 0) + 1
    edges = np.concatenate([[0], turns, [len(t) - 1]])
    rows = []
    for s, e in zip(edges[:-1], edges[1:]):
        amp = float(smooth[e] - smooth[s])
        if abs(amp) < min_amplitude_deg:
            continue
        rows.append({
            "start_s": float(t[s]),
            "end_s": float(t[e]),
            "direction": "left" if amp > 0 else "right",
            "amplitude_deg": abs(amp),
            "peak_dps": float(np.max(np.abs(rate[s:e + 1]))),
        })
    return pd.DataFrame(rows)


def compare_to_platform(track: AttitudeTrack, platform: pd.DataFrame) -> pd.DataFrame:
    """Filter angles resampled onto the platform timestamps, heading-aligned.

    Heading offsets are removed with a single constant (the median difference),
    since the filter's heading origin is arbitrary. Nothing else is fitted.
    """
    mine = track.head_angles()
    t = platform["t"].to_numpy()
    inside = (t >= mine["t"].iloc[0]) & (t <= mine["t"].iloc[-1])
    p = platform[inside].reset_index(drop=True)
    out = pd.DataFrame({"t": p["t"]})
    for col in ("yaw", "pitch", "roll"):
        est = np.interp(p["t"], mine["t"], mine[col])
        ref = p[col].to_numpy()
        if col == "yaw":
            ref = np.rad2deg(np.unwrap(np.deg2rad(ref)))
            offset = np.median(est - ref)
            est = est - offset
        out[f"{col}_imu"] = est
        out[f"{col}_ref"] = ref
    return out


# ============================ translation ============================


def world_linear_accel(imu: ImuArrays, track: AttitudeTrack) -> np.ndarray:
    """Specific force rotated to world, with local gravity removed. (N, 3) m/s²."""
    f_w = np.einsum("nij,nj->ni", track.R, imu.accel)
    f_w[:, 2] -= track.g_local
    return f_w


def integrate_naive(t: np.ndarray, a: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Strapdown double integration with no constraint. Returns (v, p)."""
    v = cumulative_trapezoid(a, t, axis=0, initial=0.0)
    p = cumulative_trapezoid(v, t, axis=0, initial=0.0)
    return v, p


def stationary_mask(imu: ImuArrays, g_local: float, gyro_dps: float = 5.0,
                    accel_ms2: float = 0.3, window_s: float = 0.2,
                    min_s: float = 0.2) -> np.ndarray:
    """Samples where the head can be taken as not translating.

    Both conditions over a short moving window: |ω| small, and |f| within
    ``accel_ms2`` of gravity. The rotation limit is looser than the 1.5 °/s
    capture gate on purpose — a zero-velocity update needs the head's
    *position* to be still, and a few °/s of rotation about the neck moves the
    glasses by only ~1 cm/s.
    """
    win = max(1, int(window_s * imu.fs))
    kernel = np.ones(win) / win
    speed = np.rad2deg(np.linalg.norm(imu.gyro, axis=1))
    dev = np.abs(np.linalg.norm(imu.accel, axis=1) - g_local)
    speed_s = np.convolve(speed, kernel, mode="same")
    dev_max = pd.Series(dev).rolling(win, center=True, min_periods=1).max().to_numpy()
    still = (speed_s < gyro_dps) & (dev_max < accel_ms2)

    # Drop runs too short to trust as a genuine stop.
    min_n = int(min_s * imu.fs)
    out = still.copy()
    idx = 0
    while idx < still.size:
        if still[idx]:
            start = idx
            while idx < still.size and still[idx]:
                idx += 1
            if idx - start < min_n:
                out[start:idx] = False
        else:
            idx += 1
    return out


@dataclass
class ZuptResult:
    t: np.ndarray
    v: np.ndarray
    p: np.ndarray
    still: np.ndarray
    segments: list[dict[str, Any]] = field(default_factory=list)


def integrate_zupt(t: np.ndarray, a: np.ndarray, still: np.ndarray) -> ZuptResult:
    """Double integration constrained by zero-velocity updates.

    Velocity is forced to zero on stationary samples. Across each movement
    between two stops, the velocity integral should also end at zero; whatever
    it ends at is accumulated error, removed as a linear ramp over the
    movement. This is standard offline ZUPT smoothing: it bounds velocity error
    to one movement and stops the quadratic position blow-up, but it does not
    remove error *within* a movement, and position error still accumulates
    across many movements.
    """
    n = len(t)
    v = np.zeros((n, 3))
    segments: list[dict[str, Any]] = []

    moving = ~still
    idx = 0
    while idx < n:
        if not moving[idx]:
            idx += 1
            continue
        start = max(idx - 1, 0)
        while idx < n and moving[idx]:
            idx += 1
        end = min(idx, n - 1)
        seg_t = t[start:end + 1]
        raw = cumulative_trapezoid(a[start:end + 1], seg_t, axis=0, initial=0.0)
        closes = idx < n  # a stop follows, so the end velocity is known to be zero
        if closes and seg_t[-1] > seg_t[0]:
            ramp = (seg_t - seg_t[0]) / (seg_t[-1] - seg_t[0])
            correction = raw[-1] * ramp[:, None]
        else:
            correction = np.zeros_like(raw)
        v[start:end + 1] = raw - correction
        segments.append({
            "start_s": float(seg_t[0]),
            "end_s": float(seg_t[-1]),
            "duration_s": float(seg_t[-1] - seg_t[0]),
            "closed": bool(closes),
            "end_velocity_error_ms": raw[-1].tolist(),
        })
    v[still] = 0.0
    p = cumulative_trapezoid(v, t, axis=0, initial=0.0)
    return ZuptResult(t=t, v=v, p=p, still=still, segments=segments)


# ============================ walking ============================


def high_pass_double_integrate(a: np.ndarray, fs: float, cutoff_hz: float = 0.6
                               ) -> np.ndarray:
    """Displacement of an oscillation, e.g. vertical head bob while walking.

    High-pass after each integration so drift is removed at every stage. Valid
    only for motion well above the cutoff; anything slower — including a real
    change in height — is removed along with the drift.
    """
    sos = signal.butter(2, cutoff_hz / (fs / 2.0), btype="high", output="sos")
    dt = 1.0 / fs
    a_hp = signal.sosfiltfilt(sos, a - np.mean(a))
    v = signal.sosfiltfilt(sos, np.cumsum(a_hp) * dt)
    return signal.sosfiltfilt(sos, np.cumsum(v) * dt)


@dataclass
class PdrResult:
    step_t: np.ndarray
    step_heading: np.ndarray       # rad, world frame
    step_xy: np.ndarray            # (S+1, 2) positions after each step
    t: np.ndarray
    xy: np.ndarray                 # (N, 2) interpolated continuous track
    bob: np.ndarray                # (N,) vertical head displacement, m
    step_length_m: float
    cadence_hz: float
    turns: list[dict[str, Any]] = field(default_factory=list)


def pedestrian_dead_reckoning(imu: ImuArrays, track: AttitudeTrack,
                              step_length_m: float = 0.70,
                              band_hz: tuple[float, float] = (0.8, 3.5)) -> PdrResult | None:
    """Steps × heading, with the step length an explicit assumption.

    Steps are peaks of world-vertical acceleration; heading is the IMU-only
    filter's yaw averaged over each step. The *shape* of the path — straight
    legs, turn angles — comes from the IMU. Its *scale* comes entirely from
    ``step_length_m``, which is not measured here.
    """
    a_w = world_linear_accel(imu, track)
    vert = a_w[:, 2]
    nyq = imu.fs / 2.0
    sos = signal.butter(2, [band_hz[0] / nyq, band_hz[1] / nyq], btype="band", output="sos")
    vf = signal.sosfiltfilt(sos, vert)
    peaks, _ = signal.find_peaks(vf, distance=int(0.3 * imu.fs),
                                 prominence=max(0.3, 0.5 * np.std(vf)))
    if peaks.size < 4:
        return None

    angles = track.head_angles()
    yaw = np.deg2rad(angles["yaw"].to_numpy())
    bounds = np.concatenate([[0], peaks])
    headings = np.array([np.mean(yaw[bounds[i]:bounds[i + 1] + 1]) for i in range(peaks.size)])
    steps = np.column_stack([np.cos(headings), np.sin(headings)]) * step_length_m
    step_xy = np.vstack([[0.0, 0.0], np.cumsum(steps, axis=0)])
    step_times = np.concatenate([[imu.t[0]], imu.t[peaks]])
    xy = np.column_stack([np.interp(imu.t, step_times, step_xy[:, i]) for i in range(2)])

    # Turnarounds: runs of high yaw rate, reported with their net angle.
    yaw_rate = np.gradient(np.unwrap(yaw), imu.t)
    sos_lp = signal.butter(2, 1.0 / nyq, output="sos")
    turning = np.abs(signal.sosfiltfilt(sos_lp, yaw_rate)) > np.deg2rad(40)
    turns = []
    idx = 0
    while idx < turning.size:
        if turning[idx]:
            s = idx
            while idx < turning.size and turning[idx]:
                idx += 1
            net = float(np.rad2deg(np.unwrap(yaw)[idx - 1] - np.unwrap(yaw)[s]))
            if abs(net) > 45:
                turns.append({"t_s": float(imu.t[s]), "net_deg": net})
        else:
            idx += 1

    duration = imu.t[peaks[-1]] - imu.t[peaks[0]]
    return PdrResult(
        step_t=imu.t[peaks],
        step_heading=headings,
        step_xy=step_xy,
        t=imu.t,
        xy=xy,
        bob=high_pass_double_integrate(vert, imu.fs),
        step_length_m=step_length_m,
        cadence_hz=float((peaks.size - 1) / duration) if duration > 0 else float("nan"),
        turns=turns,
    )


# ============================ error growth ============================


def position_error_bounds(t: np.ndarray, accel_noise_ms2: float, fs: float,
                          quantum_ms2: float = 0.1) -> dict[str, np.ndarray]:
    """Analytic 1-σ position error for the terms that drive unaided drift.

    * Constant residual bias b:            ½·b·t²
    * White acceleration noise (σ, fs):    σ·√(1/fs)·t^{3/2}/√3
    * Tilt error δθ leaking gravity:       ½·g·sin(δθ)·t²

    The bias term uses half the browser's quantization step: a true offset
    smaller than that can sit invisibly inside a rounded, un-dithered reading.
    """
    vrw = accel_noise_ms2 * np.sqrt(1.0 / fs)
    return {
        "quantization_bias": 0.5 * (quantum_ms2 / 2.0) * t ** 2,
        "white_noise": vrw * t ** 1.5 / np.sqrt(3.0),
        "tilt_0.1deg": 0.5 * GRAVITY * np.sin(np.deg2rad(0.1)) * t ** 2,
        "tilt_1deg": 0.5 * GRAVITY * np.sin(np.deg2rad(1.0)) * t ** 2,
    }


def time_to_position_error(err_m: float, accel_ms2: float) -> float:
    """Time for a constant acceleration error to accumulate ``err_m`` of position."""
    return float(np.sqrt(2.0 * err_m / accel_ms2)) if accel_ms2 > 0 else float("inf")
