#!/usr/bin/env python3
"""Standardized balance and vestibular tasks from the head-motion lab (Tier S).

Measures each of the five tasks in every participant's most recent usable
recordings, then writes one figure per task, a markdown report and a JSON
summary. What is measured, and what is not claimed, is set out in
analysis/visor_imu/balance.py.

Usage:
    python analysis/balance_analysis.py
    python analysis/balance_analysis.py --in data/imu-sessions --report documentation/testing/imu-balance-report.md
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import date
from pathlib import Path
from typing import Any

import numpy as np

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))

from visor_imu import balance, loader, report, vor  # noqa: E402
from visor_imu import balance_report as figs  # noqa: E402

DEFAULT_IN = REPO_ROOT / "data" / "imu-sessions"
DEFAULT_ASSETS = REPO_ROOT / "documentation" / "assets" / "imu" / "balance"
DEFAULT_REPORT = REPO_ROOT / "documentation" / "testing" / "imu-balance-report.md"
DEFAULT_SUMMARY = REPO_ROOT / "data" / "imu-analysis" / "balance-summary.json"


def latest_usable(sessions: list[loader.Session]) -> dict[tuple[str, str], loader.Session]:
    """The most recent usable recording of each trial, per participant: re-runs supersede."""
    out: dict[tuple[str, str], loader.Session] = {}
    for s in sorted(sessions, key=lambda s: s.started_at or ""):
        if balance.usable(s):
            out[(s.participant, s.trial_id)] = s
    return out


def camera_fits(sessions: list[loader.Session]) -> dict[str, dict[str, Any]]:
    """vor.py's fit for every camera recording, scaled against each participant's slow pan."""
    results = []
    for s in sessions:
        if s.is_native and not s.dat_video.empty:
            results.append({"session_id": s.session_id, "trial_id": s.trial_id,
                            "participant": s.participant, "image_motion": vor.analyze(s)})
    vor.attach_calibration(results)
    return {r["session_id"]: r["image_motion"] for r in results if r["image_motion"]}


def _f(v: Any, digits: int = 2) -> str:
    return "—" if v is None or (isinstance(v, float) and not np.isfinite(v)) else f"{v:.{digits}f}"


def participant_section(who: str, trials: dict[str, loader.Session], fits: dict[str, dict[str, Any]],
                        assets: Path, assets_rel: str) -> tuple[list[str], dict[str, Any]]:
    lines = [f"## Participant `{who}`", ""]
    summary: dict[str, Any] = {}
    stem = f"balance_{who}"

    # ---- standing balance: Romberg, tandem, single leg ----
    stances = [s for s in trials.values() if balance.task(s) in balance.STANCE_TASKS]
    rows, series = [], {}
    for s in sorted(stances, key=lambda s: s.trial_id):
        row = balance.stance_row(s)
        rows.append(row)
        sway = balance.sway_series(s)
        if sway is not None:
            series[s.trial_id] = sway
    ratios = balance.romberg_ratios(rows)
    summary["stance"] = rows
    summary["romberg_ratios"] = ratios
    if rows:
        lines += ["### Standing balance at a glance", "",
                  f"![overview]({assets_rel}/{figs.fig_overview(rows, ratios, assets / f'{stem}_overview.png', who).name})",
                  "",
                  "| Task | Eyes | Stance | Hold | AP sway RMS (°) | ML sway RMS (°) | 95% area (°²) | "
                  "Mean sway velocity (°/s) | Head angular speed RMS (°/s) | Sway acceleration RMS (m/s²) |",
                  "|---|---|---|---|---|---|---|---|---|---|"]
        for r in rows:
            sw = r.get("sway") or {}
            hold_text = (f"lost at {r['hold_s']:.1f} s" if r["balance_lost"]
                         else f"{r['planned_s'] or r['hold_s']:.0f} s")
            lines.append(
                f"| {balance.TASK_TITLES[r['task']]} | {r['eyes']} | {(r['variant'] or '—').replace('_', '-')} | "
                f"{hold_text} | {_f(sw.get('ap_rms_deg'))} | {_f(sw.get('ml_rms_deg'))} | "
                f"{_f((sw.get('ellipse') or {}).get('area_deg2'))} | {_f(sw.get('mean_velocity_dps'))} | "
                f"{_f(sw.get('angular_speed_rms_dps'))} | {_f(sw.get('accel_rms_ms2'), 3)} |")
        lines.append("")
        for number, task in enumerate(balance.STANCE_TASKS, start=1):
            task_rows = [r for r in rows if r["task"] == task]
            if not task_rows:
                continue
            figure = figs.fig_stance(task, task_rows, series, assets / f"{stem}_{task}.png", who)
            lines += [f"### {number}. {balance.TASK_TITLES[task]}", "", f"![{task}]({assets_rel}/{figure.name})", ""]
            for ratio in (r for r in ratios if r["task"] == task):
                where = f" ({ratio['variant'].replace('_', '-')})" if ratio["variant"] else ""
                lines.append(f"- Eyes closed ÷ eyes open{where}: sway area ×{ratio['area_ratio']:.2f}, "
                             f"mean sway velocity ×{ratio['velocity_ratio']:.2f}.")
            lines.append("")

    # ---- head impulses ----
    hit = next((s for s in trials.values() if balance.task(s) == "head_impulse"), None)
    if hit is not None:
        impulses = balance.head_impulses(hit)
        info = balance.impulse_summary(impulses)
        fit = fits.get(hit.session_id) or {}
        image = None
        if fit.get("reliable"):
            image = vor.image_angular_velocity(hit, fit, fit.get("calibration_scale_px_per_deg"))
        figure = figs.fig_head_impulses(hit, impulses, image, assets / f"{stem}_head_impulse.png", who)
        summary["head_impulse"] = {**info, "impulses": impulses.to_dict("records"),
                                   "camera": {k: fit.get(k) for k in ("reliable", "tracked_fraction", "lag_ms",
                                                                      "scale_vs_calibration")}}
        left, right = info.get("left", {}), info.get("right", {})
        lines += ["### 4. Head impulse test", "", f"![head impulse]({assets_rel}/{figure.name})", "",
                  "| Measure | Value |", "|---|---|",
                  f"| Impulses detected | {info['detected']} of {info['cues']} tones |",
                  f"| Left / right | {left.get('count', 0)} / {right.get('count', 0)} |",
                  f"| Median peak head velocity, left / right (°/s) | {_f(left.get('median_peak_dps'), 0)} / "
                  f"{_f(right.get('median_peak_dps'), 0)} |",
                  f"| Median amplitude, left / right (°) | {_f(left.get('median_amplitude_deg'), 1)} / "
                  f"{_f(right.get('median_amplitude_deg'), 1)} |",
                  f"| Peaks at or above {balance.IMPULSE_RAPID_DPS:.0f} °/s | {info.get('rapid', 0)} of {info['detected']} |"]
        if fit.get("analyzed"):
            lines.append(f"| Camera frame pairs passing quality, whole trial | "
                         f"{fit['tracked_fraction'] * 100:.0f}% |")
        lines += ["", "Head kinematics only: a clinical head impulse test also records the eyes, which the "
                  "glasses cannot, so no VOR gain is reported.", ""]

    # ---- gaze stabilization ----
    gaze_rows = []
    for plane in ("horizontal", "vertical"):
        s = next((x for x in trials.values() if balance.task(x) == "gaze_stabilization"
                  and balance.condition(x).get("plane") == plane), None)
        if s is None:
            continue
        m = balance.gaze_metrics(s)
        if m is None:
            continue
        fit = fits.get(s.session_id) or {}
        calibrated = fit.get("calibration_scale_px_per_deg")
        image = vor.image_angular_velocity(s, fit, calibrated) if fit.get("reliable") else None
        gaze_rows.append({"session": s, "metrics": m, "image": image, "fit": fit,
                          "scale_ratio": fit.get("scale_vs_calibration"),
                          "image_label": "camera image (far-scene scale)" if calibrated else "camera image"})
    if gaze_rows:
        figure = figs.fig_gaze(gaze_rows, assets / f"{stem}_gaze_stabilization.png", who)
        summary["gaze_stabilization"] = [{**g["metrics"], "camera": {k: g["fit"].get(k) for k in (
            "reliable", "lag_ms", "scale_px_per_deg", "scale_vs_calibration", "residual_dps")}}
            for g in gaze_rows]
        lines += ["### 5. Gaze stabilization", "", f"![gaze stabilization]({assets_rel}/{figure.name})", "",
                  "| Plane | Achieved / paced (Hz) | Amplitude (±°) | Peak velocity (°/s) | Off-axis share | "
                  "Camera lag (ms) | Camera ÷ head at far-scene scale |", "|---|---|---|---|---|---|---|"]
        for g in gaze_rows:
            m, fit = g["metrics"], g["fit"]
            lines.append(f"| {m['plane']} | {m['frequency_hz']:.2f} / {_f(m['paced_hz'])} | "
                         f"{m['amplitude_deg']:.1f} | {m['peak_velocity_dps']:.0f} | {m['off_axis_ratio']:.2f} | "
                         f"{_f(fit.get('lag_ms'), 0) if fit.get('reliable') else 'withheld'} | "
                         f"{_f(g['scale_ratio'])} |")
        lines.append("")
        if not any(g["scale_ratio"] for g in gaze_rows):
            lines += ["Run V4 (slow look-around) for this participant to compare the camera against a "
                      "far-scene scale.", ""]
    return lines, summary


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
        sys.exit(f"No session directory at {indir}.")
    report.apply_style()
    assets = Path(args.assets)
    assets.mkdir(parents=True, exist_ok=True)
    report_path = Path(args.report)
    try:
        assets_rel = assets.resolve().relative_to(report_path.resolve().parent).as_posix()
    except ValueError:
        assets_rel = "../assets/imu/balance"

    print(f"Loading sessions from {indir} …")
    sessions = [s for s in loader.load_directory(indir) if s.is_native]
    tasks = [s for s in sessions if balance.task(s)]
    if not tasks:
        sys.exit("No balance or vestibular task recordings (head-motion lab, Tier S) found.")
    chosen = latest_usable(tasks)
    print(f"  {len(tasks)} task recordings, {len(chosen)} used (latest usable per participant and trial)")
    # Camera trials are scaled against the slow look-around, so V4 recordings come along.
    fits = camera_fits([*chosen.values(), *(s for s in sessions if s.trial_id.startswith("V4"))])

    lines = [
        "# Balance and Vestibular Tasks — Head-motion Lab",
        "",
        f"*Generated {date.today().isoformat()} by `analysis/balance_analysis.py`*",
        "",
        "Five standardized tasks recorded through MWDAT 1.0 Motion on the glasses, with the camera on for "
        "the head impulse and gaze-stabilization tasks. Sway is the head's angular sway in its "
        "gravity-levelled mean pose. It is head sway, not center-of-pressure sway. Head impulses are head "
        "kinematics. With no eye tracking, nothing here is a VOR gain. Protocol: "
        "[imu-test-protocol.md](./imu-test-protocol.md), Tier S.",
        "",
    ]
    if any(s.meta.get("synthetic") for s in chosen.values()):
        lines += ["> **Includes synthetic sessions. They validate the code and are not measurements.**", ""]
    summary: dict[str, Any] = {"generated": date.today().isoformat(), "participants": {}}
    for who in sorted({p for p, _ in chosen}):
        trials = {t: s for (p, t), s in chosen.items() if p == who}
        print(f"  {who}: {len(trials)} tasks …")
        section, data = participant_section(who, trials, fits, assets, assets_rel)
        lines += section
        summary["participants"][who] = data

    lines += [
        "## Method notes",
        "",
        f"- **Sway window:** from {balance.SKIP_START_S:.0f} s after the start tones (eyes closing, a foot "
        f"lifting) to the end, less the last {balance.SKIP_BEFORE_LOSS_S:.0f} s before a lost hold, which "
        f"holds the step itself.",
        "- **Sway angle:** the window's mean pose is levelled by gravity; tilt about its forward and left "
        "axes is integrated from the gyroscope, with the bias fitted as the steady slope between that "
        f"integral and the accelerometer's tilt; low-passed at {balance.SWAY_LOWPASS_HZ:.0f} Hz. The pose "
        "analysis's attitude filter is not used for sway: its accelerometer correction reads a standing "
        "body's sway acceleration (in phase with its tilt) as more tilt, and overstated synthetic sway by "
        "7–28%.",
        f"- **Sway acceleration:** horizontal specific force in the same levelled frame, low-passed at "
        f"{balance.ACCEL_LOWPASS_HZ} Hz, about its mean (tilt and translation together, as in "
        "accelerometer-based posturography).",
        "- **95% area:** the ellipse expected to hold 95% of sway samples, π·χ²₂(0.95)·√det(Σ) with "
        "χ²₂(0.95) = 5.99, on AP and ML tilt.",
        "- **Mean sway velocity:** path length of the tilt trace over the window's duration.",
        "- **Head angular speed RMS:** all three gyroscope axes over the same window; the phone's spoken "
        "summary reports the same number.",
        f"- **Head impulses:** for each tone, the first run of yaw rate above "
        f"{balance.IMPULSE_DETECT_DPS:.0f} °/s that starts within {balance.IMPULSE_WINDOW_S:.0f} s after it "
        f"(and before the next tone); its peak is the run's maximum, its edges where the rate falls below "
        f"{balance.IMPULSE_EDGE_DPS:.0f} °/s. The phone's summary applies the same rule. At 60 Hz a 150 ms "
        "impulse spans about nine samples, so peaks can read a few percent low.",
        "- **Gaze stabilization:** dominant frequency of the commanded axis's rate, amplitude by a sinusoid "
        "fit at that frequency, off-axis share = RMS of the other two axes over the commanded one.",
        "",
    ]
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text("\n".join(lines), encoding="utf-8")
    print(f"\nReport  -> {report_path}")
    summary_path = Path(args.summary)
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    summary_path.write_text(json.dumps(summary, indent=2, default=lambda v: v.tolist()
                                       if isinstance(v, np.ndarray) else str(v)), encoding="utf-8")
    print(f"Summary -> {summary_path}")
    print(f"Figures -> {assets}")


if __name__ == "__main__":
    main()
