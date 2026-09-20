#!/usr/bin/env python3
"""Analyze recorded IMU sessions and emit figures plus a markdown report.

Usage:
    python analysis/analyze.py
    python analysis/analyze.py --in data/imu-sessions --report documentation/testing/imu-characterization-report.md
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import date
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))

from visor_imu import loader, metrics, rehab, report  # noqa: E402

DEFAULT_IN = REPO_ROOT / "data" / "imu-sessions"
DEFAULT_ASSETS = REPO_ROOT / "documentation" / "assets" / "imu"
DEFAULT_REPORT = REPO_ROOT / "documentation" / "testing" / "imu-characterization-report.md"
DEFAULT_SUMMARY = REPO_ROOT / "data" / "imu-analysis" / "summary.json"

# Trials whose recordings are stationary, so bias/noise/Allan statistics are valid.
STATIC_TRIALS = {"A1_static_rest", "A2_static_extended", "A3_six_position", "B5_impulse_taps"}


def analyze_session(session: loader.Session) -> dict[str, Any]:
    """Run every analysis applicable to this trial's design."""
    df = session.motion
    result: dict[str, Any] = {
        "session_id": session.session_id,
        "trial_id": session.trial_id,
        "tier": session.tier,
        "participant": session.participant,
        "started_at": session.started_at,
        "duration_s": session.duration_s,
        "fully_null_columns": session.fully_null_columns("devicemotion"),
        "orientation_samples": int(len(session.orientation)),
        "orientation_null_columns": session.fully_null_columns("deviceorientation"),
    }

    timing = metrics.timing_stats(df)
    result["timing"] = timing.as_dict() if timing else None

    orient_timing = metrics.timing_stats(session.orientation)
    result["orientation_timing"] = orient_timing.as_dict() if orient_timing else None

    result["saturation"] = metrics.saturation_check(df, metrics.GYRO_AXES)

    # ---- quality control -------------------------------------------------
    outcome = session.meta.get("outcome")
    duration = metrics.duration_check(df, session.meta.get("plannedDurationSec"),
                                      session.duration_s)
    qc: dict[str, Any] = {
        "outcome": outcome,
        "duration": duration,
        "violations": [],
    }
    if outcome and outcome != "completed":
        qc["violations"].append(f"trial outcome was '{outcome}', not 'completed'")
    if duration.get("checked") and not duration["within_tolerance"]:
        qc["violations"].append(
            f"ran {duration['actual_s']:.1f}s of a planned {duration['planned_s']:.0f}s "
            f"({duration['completion_fraction']*100:.0f}%)")
    if timing and timing.dropout_fraction > 0.01:
        qc["violations"].append(f"dropout fraction {timing.dropout_fraction*100:.2f}% exceeds 1%")
    if timing and timing.longest_gap_ms > 250:
        qc["violations"].append(f"longest gap {timing.longest_gap_ms:.0f} ms exceeds 250 ms")

    # Bias, noise and Allan statistics are only meaningful on a stationary trial.
    if session.trial_id in STATIC_TRIALS:
        stationarity = metrics.stationarity_check(df)
        qc["stationarity"] = stationarity
        if stationarity.get("checked") and not stationarity["stationary"]:
            qc["violations"].extend(stationarity["reasons"])

        # Computed either way so the numbers exist for debugging, but flagged so
        # the report never presents movement artefacts as sensor specifications.
        result["static_stats_valid"] = bool(stationarity.get("stationary"))
        result["gravity"] = metrics.gravity_check(df)
        result["accel_static"] = metrics.static_axis_stats(df, metrics.ACCEL_AXES)
        result["gyro_static"] = metrics.static_axis_stats(df, metrics.GYRO_AXES)

        fs = metrics.estimate_fs(df)
        allan: dict[str, Any] = {}
        if fs > 0:
            for axis in metrics.GYRO_AXES:
                if axis not in df:
                    continue
                taus, adev = metrics.allan_deviation(df[axis].to_numpy(dtype="float64"), fs)
                if taus.size:
                    allan[axis] = metrics.allan_params(taus, adev)
        result["allan"] = allan

    qc["valid"] = not qc["violations"]
    result["qc"] = qc

    if session.trial_id.startswith("C1"):
        profile = rehab.stillness_profile(session)
        result["stillness"] = profile.as_dict() if profile else None
        if profile:
            dwells = rehab.detect_dwells(df, profile.recommended_threshold_dps)
            result["dwells"] = {
                "count": int(len(dwells)),
                "total_s": float(dwells["duration_s"].sum()) if len(dwells) else 0.0,
                "longest_s": float(dwells["duration_s"].max()) if len(dwells) else 0.0,
            }

    if session.trial_id.startswith("C2"):
        result["scan"] = rehab.scan_metrics(session)

    if session.trial_id.startswith("C3"):
        result["gait"] = rehab.gait_cadence(session)

    segments = rehab.segment_by_cues(session)
    if len(segments):
        result["cue_segments"] = {
            "count": int(len(segments)),
            "mean_peak_dps": float(segments["peak_dps"].mean()),
            "max_peak_dps": float(segments["peak_dps"].max()),
            "mean_onset_latency_s": (
                float(segments["onset_latency_s"].dropna().mean())
                if segments["onset_latency_s"].notna().any() else None
            ),
        }

    return result


def build_figures(session: loader.Session, analysis: dict[str, Any],
                  assets: Path) -> dict[str, Path]:
    assets.mkdir(parents=True, exist_ok=True)
    stem = session.session_id
    figures: dict[str, Path | None] = {}

    figures["Sample timing"] = report.fig_timing_jitter(session, assets / f"{stem}_timing.png")

    # Noise-characterization figures are withheld for the same reason their
    # tables are: a plausible-looking Allan curve of someone's hand movement is
    # worse than no curve at all.
    if session.trial_id in STATIC_TRIALS and analysis.get("static_stats_valid") is not False:
        figures["Allan deviation"] = report.fig_allan(session, assets / f"{stem}_allan.png")
        figures["Power spectral density"] = report.fig_psd(session, assets / f"{stem}_psd.png")

    if analysis.get("cue_segments"):
        figures["Cue-aligned response"] = report.fig_timeseries_with_cues(
            session, assets / f"{stem}_cues.png")

    still = analysis.get("stillness")
    if still:
        figures["Head stability"] = report.fig_stillness(
            session, assets / f"{stem}_stillness.png", still["recommended_threshold_dps"])

    gait = analysis.get("gait")
    if gait:
        figures["Gait spectrum"] = report.fig_gait(session, assets / f"{stem}_gait.png", gait)

    return {k: v for k, v in figures.items() if v is not None}


def platform_summary(results: list[dict[str, Any]]) -> str:
    """Cross-session findings — the part a reviewer reads first."""
    if not results:
        return "_No sessions analyzed._\n"

    motion_rates = [r["timing"]["effective_hz"] for r in results if r.get("timing")]
    orient_counts = [r["orientation_samples"] for r in results]
    total_orient = sum(orient_counts)
    dropouts = [r["timing"]["dropout_fraction"] for r in results if r.get("timing")]

    null_union: set[str] = set()
    for r in results:
        null_union.update(r.get("fully_null_columns") or [])

    lines = ["| Finding | Value |", "|---|---|"]
    lines.append(f"| Sessions analyzed | {len(results)} |")
    lines.append(f"| Trials represented | {len({r['trial_id'] for r in results})} |")
    if motion_rates:
        lines.append(f"| devicemotion rate, median across sessions | "
                     f"{sorted(motion_rates)[len(motion_rates)//2]:.1f} Hz |")
        lines.append(f"| devicemotion rate, range | "
                     f"{min(motion_rates):.1f} – {max(motion_rates):.1f} Hz |")
    if dropouts:
        lines.append(f"| Worst dropout fraction | {max(dropouts)*100:.2f}% |")
    lines.append(f"| deviceorientation samples, all sessions | {total_orient} |")
    if null_union:
        lines.append(f"| devicemotion fields never populated | `{'`, `'.join(sorted(null_union))}` |")

    out = "\n".join(lines) + "\n\n"

    if total_orient == 0:
        out += (
            "> **Orientation stream absent.** No `deviceorientation` samples were "
            "recorded in any session. Absolute head orientation is therefore not "
            "available through the standard Web API on this platform, and any VISOR "
            "feature depending on compass heading must derive it another way or be "
            "scoped out.\n\n"
        )
    else:
        orient_nulls: set[str] = set()
        for r in results:
            orient_nulls.update(r.get("orientation_null_columns") or [])
        if orient_nulls:
            out += (
                f"> **Orientation partially populated.** The stream fires, but "
                f"`{'`, `'.join(sorted(orient_nulls))}` were null throughout. "
                "This is a documented-versus-actual gap worth citing directly.\n\n"
            )

    return out


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--in", dest="indir", default=str(DEFAULT_IN))
    parser.add_argument("--assets", default=str(DEFAULT_ASSETS))
    parser.add_argument("--report", default=str(DEFAULT_REPORT))
    parser.add_argument("--summary", default=str(DEFAULT_SUMMARY))
    args = parser.parse_args()

    indir = Path(args.indir)
    if not indir.exists():
        sys.exit(f"No session directory at {indir}. Run analysis/fetch_sessions.py first.")

    report.apply_style()

    print(f"Loading sessions from {indir} …")
    sessions = loader.load_directory(indir)
    if not sessions:
        sys.exit(f"No readable sessions found in {indir}.")
    print(f"  {len(sessions)} session(s) loaded")

    assets = Path(args.assets)
    results: list[dict[str, Any]] = []
    sections: list[str] = []

    # Figures live beside the report; links are written relative to it.
    report_path = Path(args.report)
    try:
        assets_rel = Path(assets).resolve().relative_to(report_path.resolve().parent).as_posix()
    except ValueError:
        assets_rel = "../assets/imu"

    for session in sorted(sessions, key=lambda s: (s.tier, s.trial_id, s.started_at or "")):
        print(f"  analyzing {session.trial_id} ({session.session_id}) …")
        analysis = analyze_session(session)
        figures = build_figures(session, analysis, assets)
        results.append(analysis)
        sections.append(report.session_section(session, analysis, figures, assets_rel))

    body = [
        "# Meta Ray-Ban Display — IMU Characterization Report",
        "",
        f"*Generated {date.today().isoformat()} by `analysis/analyze.py`*",
        "",
        "Automated output. Methodology and interpretation are in "
        "[imu-test-protocol.md](./imu-test-protocol.md) and "
        "[imu-capability-analysis.md](../research/imu-capability-analysis.md).",
        "",
        "---",
        "",
        "## Platform summary",
        "",
        platform_summary(results),
        "---",
        "",
        "## Per-trial results",
        "",
        *sections,
    ]

    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text("\n".join(body), encoding="utf-8")
    print(f"\nReport  -> {report_path}")

    summary_path = Path(args.summary)
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    summary_path.write_text(
        json.dumps({"generated": date.today().isoformat(), "sessions": results},
                   indent=2, default=str),
        encoding="utf-8")
    print(f"Summary -> {summary_path}")
    print(f"Figures -> {assets}")


if __name__ == "__main__":
    main()
