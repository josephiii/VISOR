"""Session loading and normalization.

Two capture paths write sessions, both as gzipped column-oriented JSON with
JSON ``null`` marking a reading the platform did not supply:

- ``visor.imu.session/1`` — the on-glasses **IMU Lab web app**
  (frontend/visor-webapp), through the browser's DeviceMotion API.
- ``visor.imu.session/2`` — the Android app's **head-motion lab**
  (frontend/visor, ``ucf.visor.motionlab``), through MWDAT 1.0's Motion
  capability, with an optional camera stream (``dat_video``) whose per-frame
  image shift was measured on the phone.

``null`` is preserved as ``NaN`` all the way through. It is never filled with
zero, because "the API returned nothing" and "the sensor read zero" are
different measurements and conflating them would fabricate data.

For ``/2`` sessions the loader also derives a ``devicemotion`` frame in the
``/1`` column vocabulary, so every existing analysis (timing, Allan deviation,
scanning, gait, cue segmentation, pose) runs on native recordings unchanged.
The mapping is documented on :func:`_derive_devicemotion`. Native-only fields
(magnetometer, MWDAT's fused quaternion) are read from
:attr:`Session.native_glasses`, which shares that frame's time base.
"""

from __future__ import annotations

import gzip
import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

SCHEMA_V1 = "visor.imu.session/1"
SCHEMA_V2 = "visor.imu.session/2"
SCHEMAS = (SCHEMA_V1, SCHEMA_V2)
# Kept for callers that referred to the original constant.
SCHEMA = SCHEMA_V1

STREAM_NAMES = ("devicemotion", "deviceorientation", "deviceorientationabsolute")
NATIVE_STREAM_NAMES = ("dat_motion", "dat_video")

# MotionSample.source codes written by the Android recorder.
SOURCE_GLASSES = 0

@dataclass
class Session:
    """One recorded trial."""

    path: Path
    meta: dict[str, Any]
    marks: list[dict[str, Any]]
    streams: dict[str, pd.DataFrame]
    null_counts: dict[str, dict[str, int]]
    started_at: str | None = None
    duration_ms: float | None = None
    schema: str = SCHEMA_V1
    raw: dict[str, Any] = field(default_factory=dict, repr=False)

    # ---- convenience accessors -------------------------------------------------

    @property
    def trial_id(self) -> str:
        return self.meta.get("trialId", "unknown")

    @property
    def tier(self) -> str:
        return self.meta.get("tier", "?")

    @property
    def participant(self) -> str:
        return self.meta.get("participant", "anon")

    @property
    def session_id(self) -> str:
        return self.meta.get("sessionId", self.path.stem)

    @property
    def is_native(self) -> bool:
        """Recorded by the Android app through MWDAT, not by the web app."""
        return self.schema == SCHEMA_V2

    @property
    def platform(self) -> str:
        return "android-mwdat" if self.is_native else "web-devicemotion"

    @property
    def motion(self) -> pd.DataFrame:
        return self.streams["devicemotion"]

    @property
    def orientation(self) -> pd.DataFrame:
        return self.streams["deviceorientation"]

    @property
    def dat_motion(self) -> pd.DataFrame:
        """Every native MotionSample, in SDK units (m/s², rad/s, µT), all sources."""
        return self.streams.get("dat_motion", pd.DataFrame())

    @property
    def dat_video(self) -> pd.DataFrame:
        """One row per camera frame with its measured image shift (source pixels)."""
        return self.streams.get("dat_video", pd.DataFrame())

    @property
    def native_glasses(self) -> pd.DataFrame:
        """The glasses' own native samples, every SDK column, plus ``tPerf`` as in :attr:`motion`."""
        return glasses_samples(self.dat_motion)

    @property
    def clocks(self) -> dict[str, Any]:
        return self.meta.get("clocks") or {}

    @property
    def duration_s(self) -> float:
        if self.duration_ms:
            return self.duration_ms / 1000.0
        m = self.motion
        if len(m) > 1:
            return float(m["tPerf"].iloc[-1] - m["tPerf"].iloc[0]) / 1000.0
        return 0.0

    def cue_marks(self) -> pd.DataFrame:
        """Commanded-movement timestamps, in seconds, used to segment the signal."""
        rows = [
            {
                "t_s": (m.get("t") or 0.0) / 1000.0,
                "label": (m.get("extra") or {}).get("label", m.get("label")),
                "index": (m.get("extra") or {}).get("index"),
                "kind": m.get("label"),
            }
            for m in self.marks
        ]
        return pd.DataFrame(rows)

    def fully_null_columns(self, stream: str = "devicemotion") -> list[str]:
        """Columns the platform never populated across the whole trial."""
        df = self.streams.get(stream)
        if df is None or df.empty:
            return []
        return [c for c in df.columns if df[c].isna().all()]


def _stream_to_frame(stream: dict[str, Any]) -> pd.DataFrame:
    columns = stream.get("columns") or {}
    if not columns:
        return pd.DataFrame()
    # float64 so JSON null becomes NaN rather than raising or coercing to 0.
    return pd.DataFrame({k: np.asarray(v, dtype="float64") for k, v in columns.items()})


def glasses_samples(dat_motion: pd.DataFrame) -> pd.DataFrame:
    """The glasses' own rows of a native ``dat_motion`` stream, with ``tPerf`` added.

    * Only samples whose ``source`` is the glasses are kept. MWDAT can interleave
      a Neural Band into the same feed, and a second rigid body in a head signal
      describes neither.
    * ``tPerf`` is the **glasses' own sample time** (``tDevice``) re-based onto
      the recording's phone clock, so cue marks (phone clock) still line up.
      The web path's ``tPerf`` is an arrival time; the native path has the true
      sampling time, which is what integration and spectra should use.
    """
    if dat_motion.empty or "tDevice" not in dat_motion:
        return pd.DataFrame()
    glasses = dat_motion
    if "source" in dat_motion:
        glasses = dat_motion[dat_motion["source"] == SOURCE_GLASSES]
    glasses = glasses[np.isfinite(glasses["tDevice"])].reset_index(drop=True)
    if glasses.empty:
        return pd.DataFrame()

    # Re-base device time onto the phone clock by the lower envelope of
    # arrival - stamp (see vor.lower_envelope_offset): arrival is never earlier
    # than the sample, so the minimum is the offset plus the fastest delivery.
    offset = lower_envelope_offset(glasses["tPhone"].to_numpy(), glasses["tDevice"].to_numpy())
    return glasses.assign(tPerf=glasses["tDevice"].to_numpy() + (offset if offset is not None else 0.0))


def _derive_devicemotion(dat_motion: pd.DataFrame) -> pd.DataFrame:
    """Express native MWDAT samples in the web session's ``devicemotion`` columns.

    * Rows and ``tPerf`` are those of :func:`glasses_samples`. The phone
      arrival time is kept as ``tEvent``.
    * ``agx..agz`` are the accelerometer (m/s², gravity included — the same
      convention as ``accelerationIncludingGravity``). Linear acceleration is
      not supplied by MWDAT, so ``ax..az`` are all null.
    * Rotation rates are converted to °/s, and the SDK's ``gx, gy, gz`` become
      ``rrAlpha, rrBeta, rrGamma`` **unchanged in order**. Both capture paths
      report in the same glasses body frame — x = wearer's right, y = up,
      z = backward. The pose analysis measured it on the web stream
      (:data:`metrics.GYRO_HEAD_AXIS`, by gravity consistency); Meta's
      BirdSpotter sample measured +Y up and -Z forward on worn glasses. So
      ``rrAlpha`` is pitch, ``rrBeta`` yaw and ``rrGamma`` roll on both paths, and
      every analysis keyed on those names reads native sessions correctly.
      :func:`pose.axis_mapping_scores` re-tests this on each platform's data.
    """
    glasses = glasses_samples(dat_motion)
    if glasses.empty:
        return pd.DataFrame()

    deg = 180.0 / np.pi
    nan = np.full(len(glasses), np.nan)
    return pd.DataFrame({
        "tPerf": glasses["tPerf"].to_numpy(),
        "tEvent": glasses["tPhone"].to_numpy(),
        "ax": nan, "ay": nan, "az": nan,
        "agx": glasses["ax"].to_numpy(),
        "agy": glasses["ay"].to_numpy(),
        "agz": glasses["az"].to_numpy(),
        "rrAlpha": glasses["gx"].to_numpy() * deg,
        "rrBeta": glasses["gy"].to_numpy() * deg,
        "rrGamma": glasses["gz"].to_numpy() * deg,
        "interval": nan,
    })


def lower_envelope_offset(phone_ms: np.ndarray, stream_ms: np.ndarray) -> float | None:
    """min(arrival − stamp): maps a stream's own clock onto the phone clock.

    Arrival = stamp + clock offset + transport delay, and a delay is never
    negative, so the minimum recovers the offset up to the stream's fastest
    delivery. Identical to ``QuickLook.lowerEnvelopeOffset`` on the phone.
    """
    d = np.asarray(phone_ms, dtype="float64") - np.asarray(stream_ms, dtype="float64")
    d = d[np.isfinite(d)]
    return float(d.min()) if d.size else None


def load_session(path: str | Path) -> Session:
    """Read one ``.json`` or ``.json.gz`` session file (schema /1 or /2)."""
    path = Path(path)
    opener = gzip.open if path.suffix == ".gz" else open
    with opener(path, "rt", encoding="utf-8") as fh:
        payload = json.load(fh)

    schema = payload.get("schema")
    if schema not in SCHEMAS:
        raise ValueError(f"{path.name}: unexpected schema {schema!r}, expected one of {SCHEMAS}")

    streams: dict[str, pd.DataFrame] = {}
    null_counts: dict[str, dict[str, int]] = {}
    raw_streams = payload.get("streams") or {}
    marks = payload.get("marks") or []

    if schema == SCHEMA_V1:
        for name in STREAM_NAMES:
            block = raw_streams.get(name) or {}
            streams[name] = _stream_to_frame(block)
            null_counts[name] = block.get("nullCounts") or {}
    else:
        for name in NATIVE_STREAM_NAMES:
            block = raw_streams.get(name) or {}
            streams[name] = _stream_to_frame(block)
            null_counts[name] = block.get("nullCounts") or {}
        streams["devicemotion"] = _derive_devicemotion(streams["dat_motion"])
        # MWDAT delivers a fused quaternion rather than W3C Euler angles; the
        # web orientation streams have no native counterpart.
        streams["deviceorientation"] = pd.DataFrame()
        streams["deviceorientationabsolute"] = pd.DataFrame()

    return Session(
        path=path,
        meta=payload.get("meta") or {},
        marks=marks,
        streams=streams,
        null_counts=null_counts,
        started_at=payload.get("startedAt"),
        duration_ms=payload.get("durationMs"),
        schema=schema,
        raw=payload,
    )


def load_directory(root: str | Path, pattern: str = "**/*.json*") -> list[Session]:
    """Load every session under ``root``, skipping unreadable files with a note."""
    root = Path(root)
    sessions: list[Session] = []
    for path in sorted(root.glob(pattern)):
        if path.is_dir() or path.name.endswith(".partial"):
            continue
        try:
            sessions.append(load_session(path))
        except (ValueError, json.JSONDecodeError, OSError) as exc:
            print(f"  ! skipping {path.name}: {exc}")
    return sessions
