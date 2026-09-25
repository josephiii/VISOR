"""Session loading and normalization.

Sessions arrive as gzipped JSON written by the on-glasses capture engine. The
wire format is column-oriented: each stream is a dict of equal-length arrays,
with JSON ``null`` marking a reading the platform did not supply.

``null`` is preserved as ``NaN`` all the way through. It is never filled with
zero, because "the API returned nothing" and "the sensor read zero" are
different measurements and conflating them would fabricate data.
"""

from __future__ import annotations

import gzip
import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

SCHEMA = "visor.imu.session/1"

STREAM_NAMES = ("devicemotion", "deviceorientation", "deviceorientationabsolute")


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
    def motion(self) -> pd.DataFrame:
        return self.streams["devicemotion"]

    @property
    def orientation(self) -> pd.DataFrame:
        return self.streams["deviceorientation"]

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


def load_session(path: str | Path) -> Session:
    """Read one ``.json`` or ``.json.gz`` session file."""
    path = Path(path)
    opener = gzip.open if path.suffix == ".gz" else open
    with opener(path, "rt", encoding="utf-8") as fh:
        payload = json.load(fh)

    schema = payload.get("schema")
    if schema != SCHEMA:
        raise ValueError(f"{path.name}: unexpected schema {schema!r}, expected {SCHEMA!r}")

    streams: dict[str, pd.DataFrame] = {}
    null_counts: dict[str, dict[str, int]] = {}
    for name in STREAM_NAMES:
        block = (payload.get("streams") or {}).get(name) or {}
        streams[name] = _stream_to_frame(block)
        null_counts[name] = block.get("nullCounts") or {}

    return Session(
        path=path,
        meta=payload.get("meta") or {},
        marks=payload.get("marks") or [],
        streams=streams,
        null_counts=null_counts,
        started_at=payload.get("startedAt"),
        duration_ms=payload.get("durationMs"),
        raw=payload,
    )


def load_directory(root: str | Path, pattern: str = "**/*.json*") -> list[Session]:
    """Load every session under ``root``, skipping unreadable files with a note."""
    root = Path(root)
    sessions: list[Session] = []
    for path in sorted(root.glob(pattern)):
        if path.is_dir():
            continue
        try:
            sessions.append(load_session(path))
        except (ValueError, json.JSONDecodeError, OSError) as exc:
            print(f"  ! skipping {path.name}: {exc}")
    return sessions
