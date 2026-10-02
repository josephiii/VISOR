#!/usr/bin/env python3
"""IMU-only 6-DOF head pose: what the gyroscope and accelerometer can recover.

Runs the orientation filter, drift analysis, zero-velocity-aided integration
and step dead reckoning over recorded sessions, then writes figures, a
markdown report and a JSON summary.

Usage:
    python analysis/pose_analysis.py
    python analysis/pose_analysis.py --in data/imu-sessions --assets documentation/assets/imu/pose
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import date
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))

from visor_imu import loader, metrics, pose, report  # noqa: E402
from visor_imu import pose_report as figs  # noqa: E402

DEFAULT_IN = REPO_ROOT / "data" / "imu-sessions"
DEFAULT_ASSETS = REPO_ROOT / "documentation" / "assets" / "imu" / "pose"
DEFAULT_REPORT = REPO_ROOT / "documentation" / "testing" / "imu-pose-tracking-report.md"
DEFAULT_SUMMARY = REPO_ROOT / "data" / "imu-analysis" / "pose-summary.json"

PACED = {"B1_yaw_paced": "yaw", "B2_pitch_paced": "pitch", "B3_roll_paced": "roll"}
TOLERANCE_M = 0.10
TOLERANCE_DEG = 1.0


# ============================ session selection ============================


def completed(sessions: list[loader.Session]) -> list[loader.Session]:
    """Sessions that ran to their planned length and were never backgrounded."""
    out = []
    for s in sessions:
        if s.meta.get("outcome") not in (None, "completed"):
            continue
        check = metrics.duration_check(s.motion, s.meta.get("plannedDurationSec"), s.duration_s)
        if check.get("checked") and not check["within_tolerance"]:
            continue
        if any(m.get("label") == "visibility_hidden" for m in s.marks):
            continue
        out.append(s)
    return out


def pick(sessions: list[loader.Session], trial: str) -> loader.Session | None:
    """Longest completed session of a trial."""
    matches = [s for s in sessions if s.trial_id == trial]
    return max(matches, key=lambda s: s.duration_s) if matches else None


def static_reference(sessions: list[loader.Session]) -> loader.Session | None:
    """A static trial that actually passed the stationarity check."""
    for s in sorted(sessions, key=lambda s: -s.duration_s):
        if s.trial_id in ("A1_static_rest", "A2_static_extended"):
            if metrics.stationarity_check(s.motion).get("stationary"):
                return s
    return None


def primary_participant(sessions: list[loader.Session]) -> str | None:
    """The participant who completed the most trials — the fullest single battery."""
    counts = pd.Series([s.participant for s in sessions]).value_counts()
    return str(counts.index[0]) if len(counts) else None


def label(s: loader.Session) -> str:
    return f"{s.trial_id} ({s.participant})"


# ============================ analyses ============================


def axis_identification(done: list[loader.Session]) -> tuple[pd.DataFrame, pd.DataFrame]:
    rows = {}
    for trial, name in PACED.items():
        shares = [pose.axis_energy_share(s) for s in done if s.trial_id == trial]
        if shares:
            rows[f"{name} ({trial[:2]}, n={len(shares)})"] = pd.DataFrame(shares).mean()
    share_frame = pd.DataFrame(rows).T[list(pose.BODY_GYRO)] if rows else pd.DataFrame()

    probe_trials = {"B1_yaw_paced", "B2_pitch_paced", "B3_roll_paced", "C2_scanning_pattern",
                    "C4_sit_stand"}
    probe = [s for s in done if s.trial_id in probe_trials]
    return share_frame, pose.axis_mapping_scores(probe)


def orientation_panels(done: list[loader.Session]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    panels, stats = [], []
    for trial, axis in PACED.items():
        primary = primary_participant(done)
        s = pick([x for x in done if x.participant == primary], trial) or pick(done, trial)
        if s is None:
            continue
        imu = pose.imu_arrays(s)
        ref = pose.platform_angles(s)
        if imu is None or ref is None:
            continue
        filt = pose.mahony(imu)
        gyro_only = pose.mahony(imu, use_accel=False)
        comp = pose.compare_to_platform(filt, ref)
        comp_gyro = pose.compare_to_platform(gyro_only, ref)
        row: dict[str, Any] = {"trial": label(s), "axis": axis}
        for key in ("pitch", "roll"):
            row[f"{key}_rms_deg"] = float(np.sqrt(np.mean((comp[f"{key}_imu"] - comp[f"{key}_ref"]) ** 2)))
            row[f"{key}_rms_gyro_only_deg"] = float(
                np.sqrt(np.mean((comp_gyro[f"{key}_imu"] - comp_gyro[f"{key}_ref"]) ** 2)))
            row[f"{key}_range_deg"] = float(np.ptp(comp[f"{key}_ref"]))
        row["heading"] = pose.heading_agreement(filt, ref)
        row["yaw_range_imu_deg"] = float(np.ptp(filt.head_angles()["yaw"]))
        stats.append(row)

        if axis == "yaw":
            h = row["heading"]
            caption = (f"platform heading vs gyro yaw: increment r = {h['r']:+.2f}, gain {h['gain']:+.2f}\n"
                       "(a usable heading reference would give ≈ +1, +1)")
        else:
            caption = (f"RMS difference {row[f'{axis}_rms_deg']:.2f}° over {s.duration_s:.0f} s "
                       f"(range {row[f'{axis}_range_deg']:.0f}°); gyro-only "
                       f"{row[f'{axis}_rms_gyro_only_deg']:.2f}°")
        panels.append({"comparison": comp, "axis": axis, "trial": label(s), "caption": caption})
    return panels, stats


def heading_survey(done: list[loader.Session]) -> list[dict[str, Any]]:
    """Platform heading agreement for every yaw-dominant recording."""
    out = []
    for s in done:
        if s.trial_id[:2] not in ("B1", "B4", "C2"):
            continue
        imu, ref = pose.imu_arrays(s), pose.platform_angles(s)
        if imu is None or ref is None:
            continue
        out.append({"session": label(s), **pose.heading_agreement(pose.mahony(imu), ref)})
    return out


def drift_curves(sessions: list[loader.Session], horizon_s: float, start_every_s: float = 1.0,
                 max_start_dps: float | None = None) -> dict[str, np.ndarray] | None:
    """Naive double-integration error vs time, over many start points."""
    tau = np.logspace(np.log10(0.05), np.log10(horizon_s), 60)
    all_err = []
    for s in sessions:
        imu = pose.imu_arrays(s)
        if imu is None:
            continue
        track = pose.mahony(imu)
        a = pose.world_linear_accel(imu, track)
        speed = np.rad2deg(np.linalg.norm(imu.gyro, axis=1))
        step = max(1, int(start_every_s * imu.fs))
        for i0 in range(0, len(imu.t), step):
            if imu.t[-1] - imu.t[i0] < horizon_s:
                break
            if max_start_dps is not None:
                # Start where the head is momentarily still, so the unknown
                # initial velocity is ≈ 0 rather than a free error source.
                window = slice(i0, min(i0 + step, len(imu.t)))
                j = i0 + int(np.argmin(speed[window]))
                if speed[j] > max_start_dps:
                    continue
                i0 = j
            end = np.searchsorted(imu.t, imu.t[i0] + horizon_s)
            t = imu.t[i0:end] - imu.t[i0]
            _, p = pose.integrate_naive(t, a[i0:end])
            err = np.linalg.norm(p, axis=1)
            all_err.append(np.interp(tau, t, err))
    if not all_err:
        return None
    arr = np.vstack(all_err)
    median = np.median(arr, axis=0)
    crossing = np.flatnonzero(median >= TOLERANCE_M)
    return {
        "tau": tau,
        "median": median,
        "p25": np.percentile(arr, 25, axis=0),
        "p75": np.percentile(arr, 75, axis=0),
        "n_windows": len(all_err),
        "time_to_tolerance_s": float(tau[crossing[0]]) if crossing.size else float("inf"),
    }


def sit_stand(session: loader.Session) -> dict[str, Any]:
    imu = pose.imu_arrays(session)
    track = pose.mahony(imu)
    a = pose.world_linear_accel(imu, track)
    still = pose.stationary_mask(imu, track.g_local)
    zupt = pose.integrate_zupt(imu.t, a, still)

    rows = []
    for seg in zupt.segments:
        if seg["duration_s"] < 1.0 or not seg["closed"]:
            continue
        i0 = int(np.searchsorted(zupt.t, seg["start_s"]))
        i1 = int(np.searchsorted(zupt.t, seg["end_s"]))
        d = zupt.p[i1] - zupt.p[i0]
        dz = float(d[2] * 100)
        rows.append({
            "i0": i0, "i1": i1,
            "start_s": seg["start_s"] - zupt.t[0], "duration_s": seg["duration_s"],
            "kind": "stand" if dz > 0 else "sit",
            "dz_cm": dz, "dx_cm": float(d[0] * 100), "dy_cm": float(d[1] * 100),
            "horizontal_cm": float(np.hypot(d[0], d[1]) * 100),
            # A single stand or sit takes ~2–4 s; longer means two transitions
            # ran together with no stop to reset velocity between them.
            "merged": bool(seg["duration_s"] > 6.0 or abs(dz) < 15.0),
        })
    transitions = pd.DataFrame(rows)
    cues = session.cue_marks()
    cues = cues[cues["kind"] == "cue"] if not cues.empty else cues
    return {"zupt": zupt, "track": track, "transitions": transitions, "cues": cues,
            "still_fraction": float(still.mean())}


# ============================ report ============================


def fmt(v: float, digits: int = 2) -> str:
    return "∞" if not np.isfinite(v) else f"{v:,.{digits}f}"


def write_report(path: Path, assets_rel: str, figures: dict[str, Path], summary: dict[str, Any]) -> None:
    o = summary["orientation"]
    d = summary["drift"]
    st = summary["sit_stand"]
    w = summary["walking"]
    m = summary["axis_mapping"]

    lines = [
        "# IMU-only 6-DOF Head Pose — What the Gyroscope and Accelerometer Can Recover",
        "",
        f"*Generated {date.today().isoformat()} by `analysis/pose_analysis.py`*",
        "",
        "Companion to [imu-characterization-report.md](./imu-characterization-report.md). Internal pilot "
        "data, one pair of glasses, one wearer, no external motion-capture reference: every accuracy "
        "statement below is self-consistency or agreement with the platform's own orientation stream, "
        "not validated accuracy.",
        "",
        "## Summary",
        "",
        "| Degree of freedom | IMU-only result | Status |",
        "|---|---|---|",
        f"| Roll, pitch | {o['pitch_roll_rms_range']} RMS vs platform orientation | **Bounded** — gravity-referenced |",
        f"| Yaw (relative) | 1° drift after ~{fmt(summary['budget']['yaw_calibrated_s'], 0)} s with a rest-period bias "
        f"estimate; ~{fmt(summary['budget']['yaw_uncalibrated_s'], 0)} s without (bias only; scale-factor "
        "error unmeasured) | **Drifts slowly** — usable over a movement or a scan |",
        "| Yaw (absolute heading) | Platform compass heading does not track head yaw | **Not available** — see finding 2 |",
        f"| x, y, z unaided | 10 cm error after ~{fmt(d['moving']['time_to_tolerance_s'], 1)} s (seated head turns) | **Diverges** — ∝ t² |",
        f"| Height, sit-to-stand, with ZUPT | rise {st['rise_mean_cm']:.1f} ± {st['rise_sd_cm']:.1f} cm, "
        f"drop {st['drop_mean_cm']:.1f} ± {st['drop_sd_cm']:.1f} cm | **Bounded per movement** |",
        f"| x, y walking, step dead reckoning | {w['steps']} steps, {w['cadence_hz']:.2f} Hz; shape only | "
        "**Scale unvalidated** — assumed step length |",
        "",
        "## Findings",
        "",
        "1. **Gyro channels are not in W3C order on this platform.** Yaw is `rrBeta`, pitch is `rrAlpha`, "
        f"roll is `rrGamma` (body x = right, y = up, z = backward). The adopted mapping has a gravity-"
        f"prediction residual of {m['adopted_residual']:.2f} m/s² against {m['w3c_residual']:.2f} m/s² for the "
        "spec mapping. The characterization pipeline takes its axis labels and scan yaw channel from "
        "`visor_imu.metrics.GYRO_HEAD_AXIS`.",
        "2. **The platform's heading is not a usable yaw reference.** Its roll and pitch agree with the "
        "IMU-only filter, but its heading increments are negatively related or unrelated to gyro yaw "
        f"(r from {summary['heading_r_range'][0]:+.2f} to {summary['heading_r_range'][1]:+.2f} across yaw-dominant "
        "recordings). No Euler convention consistent with gravity makes it track the gyro. Absolute heading "
        "should be treated as unavailable until this is explained.",
        "3. **Browser rounding sets a hard floor on translation.** Acceleration and rotation rate arrive "
        "rounded to 0.1 m/s² and 0.1 °/s. An undithered offset up to 0.05 m/s² is invisible, and alone "
        f"reaches 10 cm of position error in {summary['budget']['quantization_s']:.1f} s.",
        "4. **Translation is only recoverable under a constraint.** Zero-velocity updates bound each "
        "sit-to-stand transition; step counting gives a path shape while walking. Neither gives tracked "
        "absolute position over time.",
        "",
    ]
    captions = {
        "pipeline": "Estimator structure.",
        "axes": "Which gyro channel is which head axis.",
        "orientation": "Roll, pitch and yaw from the IMU alone, against the platform's fused orientation.",
        "scan": "The practical payoff of 3-DOF orientation: compensatory-scan geometry on the corrected yaw axis.",
        "drift": "Why unaided translation fails: position error growth with truth ≈ 0.",
        "budget": "Time to tolerance for each degree of freedom.",
        "zupt": "Height change recovered with zero-velocity updates.",
        "pose3d": "Six-DOF trajectory through each sit-to-stand transition.",
        "walking": "Step-based dead reckoning while walking.",
    }
    lines.append("## Figures\n")
    for key, p in figures.items():
        lines.append(f"**{captions.get(key, key)}**\n")
        lines.append(f"![{key}]({assets_rel}/{p.name})\n")

    lines += [
        "## Method notes",
        "",
        "- **Orientation:** Mahony complementary filter, Kp = 1.0, Ki = 0.02, accelerometer correction "
        "weighted by exp(−(‖a‖−g)²/0.8²) so head acceleration does not pull the tilt estimate. Heading "
        "origin is arbitrary (no compass).",
        "- **Gravity removal:** specific force rotated to world, minus local g estimated in-run from "
        "near-static samples.",
        "- **ZUPT:** stationary when mean |ω| < 5 °/s and max ‖a‖−g < 0.3 m/s² over 0.2 s, for ≥ 0.2 s. "
        "Velocity is zeroed there and de-drifted linearly across each movement.",
        "- **Step dead reckoning:** steps are peaks of 0.8–3.5 Hz band-passed vertical acceleration; "
        "heading is filter yaw averaged per step; step length is an assumed 0.70 m.",
        f"- **Walking trial note:** C3 asks for a straight path, but the recording shows "
        f"{w['total_turn_deg']:,.0f}° of cumulative leftward turning (~{w['total_turn_deg']/360:.1f} laps), "
        "so the wearer walked a loop.",
        "",
    ]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


# ============================ main ============================


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
    assets = Path(args.assets)
    assets.mkdir(parents=True, exist_ok=True)

    print(f"Loading sessions from {indir} …")
    sessions = loader.load_directory(indir)
    done = completed(sessions)
    print(f"  {len(sessions)} loaded, {len(done)} completed")

    figures: dict[str, Path] = {}
    summary: dict[str, Any] = {"generated": date.today().isoformat()}

    figures["pipeline"] = figs.fig_pipeline(assets / "pose_pipeline.png")

    print("  axis identification …")
    shares, mapping = axis_identification(done)
    if len(shares) and len(mapping):
        figures["axes"] = figs.fig_axis_identification(shares, mapping,
                                                       assets / "pose_axis_identification.png")
        summary["axis_mapping"] = {
            "adopted_residual": float(mapping[mapping["is_adopted"]]["residual_ms2"].iloc[0]),
            "w3c_residual": float(mapping[mapping["is_w3c_spec"]]["residual_ms2"].iloc[0]),
            "ranking": mapping[["mapping", "residual_ms2"]].head(8).to_dict("records"),
            "energy_share": shares.round(4).to_dict("index"),
        }

    print("  orientation …")
    panels, ostats = orientation_panels(done)
    if panels:
        figures["orientation"] = figs.fig_orientation(panels, assets / "pose_orientation.png")
    rms_all = [r[f"{k}_rms_deg"] for r in ostats for k in ("pitch", "roll")]
    summary["orientation"] = {"per_trial": ostats,
                              "pitch_roll_rms_range": (f"{min(rms_all):.1f}–{max(rms_all):.1f}°"
                                                       if rms_all else "n/a")}
    survey = heading_survey(done)
    summary["heading_survey"] = survey
    rs = [h["r"] for h in survey]
    summary["heading_r_range"] = [min(rs), max(rs)] if rs else [float("nan")] * 2

    scan_session = pick(done, "C2_scanning_pattern")
    if scan_session is not None:
        angles = pose.mahony(pose.imu_arrays(scan_session)).head_angles()
        sweeps = pose.yaw_sweeps(angles)
        figures["scan"] = figs.fig_scan_map(angles, sweeps, assets / "pose_scan_map.png", label(scan_session))
        left = sweeps[sweeps["direction"] == "left"]["amplitude_deg"]
        right = sweeps[sweeps["direction"] == "right"]["amplitude_deg"]
        summary["scan"] = {
            "session": label(scan_session),
            "sweeps": int(len(sweeps)),
            "mean_amplitude_deg": float(sweeps["amplitude_deg"].mean()),
            "left_mean_deg": float(left.mean()), "right_mean_deg": float(right.mean()),
            "asymmetry_index": float((left.sum() - right.sum()) / (left.sum() + right.sum())),
        }

    print("  translation drift …")
    static = static_reference(sessions)
    moving = [s for s in done if s.trial_id in PACED]
    curves: dict[str, dict[str, np.ndarray]] = {}
    drift_summary: dict[str, Any] = {}
    if static is not None:
        c = drift_curves([static], horizon_s=60.0, start_every_s=2.0)
        if c:
            curves[f"glasses at rest — {label(static)}"] = c
            drift_summary["static"] = {"session": label(static), "n_windows": c["n_windows"],
                                       "time_to_tolerance_s": c["time_to_tolerance_s"],
                                       "error_at_10s_m": float(np.interp(10, c["tau"], c["median"]))}
    c = drift_curves(moving, horizon_s=30.0, start_every_s=1.0, max_start_dps=15.0)
    if c:
        curves[f"seated head turns — B1–B3, {len(moving)} sessions"] = c
        drift_summary["moving"] = {"n_windows": c["n_windows"],
                                   "time_to_tolerance_s": c["time_to_tolerance_s"],
                                   "error_at_10s_m": float(np.interp(10, c["tau"], c["median"]))}
    summary["drift"] = drift_summary

    fs = 60.0
    accel_noise = 0.02
    if static is not None:
        accel_noise = float(np.mean([np.std(static.motion[a].dropna()) for a in ("agy", "agz")]))
        fs = metrics.estimate_fs(static.motion)
    tau = np.logspace(np.log10(0.05), np.log10(60), 200)
    bounds = pose.position_error_bounds(tau, accel_noise, fs)
    figures["drift"] = figs.fig_position_drift(curves, bounds, tau, assets / "pose_position_drift.png",
                                               TOLERANCE_M)

    # ---- drift budget: angles from the static recording's yaw channel
    yaw_bi = yaw_turn_on = None
    if static is not None:
        fs_s = metrics.estimate_fs(static.motion)
        taus, adev = metrics.allan_deviation(static.motion["rrBeta"].to_numpy(dtype="float64"), fs_s)
        yaw_bi = metrics.allan_params(taus, adev)["bias_instability"]
        yaw_turn_on = abs(float(static.motion["rrBeta"].mean()))
    budget = {
        "yaw_calibrated_s": TOLERANCE_DEG / yaw_bi if yaw_bi else float("nan"),
        "yaw_uncalibrated_s": TOLERANCE_DEG / yaw_turn_on if yaw_turn_on else float("nan"),
        "quantization_s": pose.time_to_position_error(TOLERANCE_M, 0.05),
        "tilt_1deg_s": pose.time_to_position_error(TOLERANCE_M, metrics.GRAVITY * np.sin(np.deg2rad(1.0))),
        "tilt_0p1deg_s": pose.time_to_position_error(TOLERANCE_M, metrics.GRAVITY * np.sin(np.deg2rad(0.1))),
        "yaw_bias_instability_dps": yaw_bi, "yaw_turn_on_bias_dps": yaw_turn_on,
    }
    summary["budget"] = budget

    print("  sit-to-stand …")
    c4 = pick(done, "C4_sit_stand")
    if c4 is not None:
        res = sit_stand(c4)
        tr = res["transitions"]
        good = tr[~tr["merged"]]
        rise = good[good["kind"] == "stand"]["dz_cm"]
        drop = -good[good["kind"] == "sit"]["dz_cm"]
        summary["sit_stand"] = {
            "session": label(c4), "still_fraction": res["still_fraction"],
            "transitions": tr.drop(columns=["i0", "i1"]).round(2).to_dict("records"),
            "rise_mean_cm": float(rise.mean()), "rise_sd_cm": float(rise.std(ddof=1)),
            "drop_mean_cm": float(drop.mean()), "drop_sd_cm": float(drop.std(ddof=1)),
            "merged": int(tr["merged"].sum()),
        }
        figures["zupt"] = figs.fig_zupt_height(res["zupt"], tr, res["cues"], assets / "pose_zupt_height.png",
                                               label(c4))
        figures["pose3d"] = figs.fig_pose_3d(res["zupt"], res["track"].R, tr, assets / "pose_6dof_3d.png",
                                             label(c4))

    print("  walking …")
    c3 = pick(done, "C3_walk_straight")
    if c3 is not None:
        imu = pose.imu_arrays(c3)
        track = pose.mahony(imu)
        pdr = pose.pedestrian_dead_reckoning(imu, track)
        angles = track.head_angles()
        if pdr is not None:
            figures["walking"] = figs.fig_walking(pdr, angles, assets / "pose_walking_pdr.png", label(c3))
            summary["walking"] = {
                "session": label(c3), "steps": int(len(pdr.step_t)), "cadence_hz": pdr.cadence_hz,
                "assumed_step_length_m": pdr.step_length_m,
                "total_turn_deg": float(angles["yaw"].iloc[-1] - angles["yaw"].iloc[0]),
                "bob_rms_cm": float(np.std(pdr.bob) * 100),
                "bob_p5_p95_cm": (np.percentile(pdr.bob, [5, 95]) * 100).tolist(),
            }

    # Budget rows need the drift results, so the figure is drawn last.
    ms = summary["orientation"]["pitch_roll_rms_range"]
    rows = [
        {"header": True, "label": "Orientation (1°)"},
        {"label": "Roll & pitch — IMU-only filter", "seconds": float("inf"),
         "value_text": f"bounded · {ms} RMS vs platform"},
        {"label": "Yaw — bias estimated at rest", "seconds": budget["yaw_calibrated_s"]},
        {"label": "Yaw — uncalibrated turn-on bias", "seconds": budget["yaw_uncalibrated_s"]},
        {"header": True, "label": "Position (10 cm)"},
        {"label": "0.1° tilt error, unaided", "seconds": budget["tilt_0p1deg_s"]},
        {"label": "0.05 m/s² offset (½ rounding step)", "seconds": budget["quantization_s"]},
        {"label": "1° tilt error, unaided", "seconds": budget["tilt_1deg_s"]},
    ]
    for key, text in (("static", "Measured — glasses at rest"), ("moving", "Measured — seated head turns")):
        if key in drift_summary:
            rows.append({"label": text, "seconds": drift_summary[key]["time_to_tolerance_s"]})
    if "sit_stand" in summary and np.isfinite(summary["sit_stand"]["rise_sd_cm"]):
        s = summary["sit_stand"]
        rows.append({"label": "ZUPT — height per sit/stand", "seconds": float("inf"),
                     "value_text": f"bounded per movement · ±{max(s['rise_sd_cm'], s['drop_sd_cm']):.1f} cm SD"})
    figures["budget"] = figs.fig_drift_budget(rows, assets / "pose_drift_budget.png")

    # Report order follows the argument, not the order figures were computed.
    order = ["pipeline", "axes", "orientation", "scan", "drift", "budget", "zupt", "pose3d", "walking"]
    figures = {k: figures[k] for k in order if k in figures}

    report_path = Path(args.report)
    try:
        assets_rel = assets.resolve().relative_to(report_path.resolve().parent).as_posix()
    except ValueError:
        assets_rel = "../assets/imu/pose"
    if (all(k in summary for k in ("sit_stand", "walking", "axis_mapping"))
            and "moving" in summary["drift"] and rms_all):
        write_report(report_path, assets_rel, figures, summary)
        print(f"\nReport  -> {report_path}")

    summary_path = Path(args.summary)
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    summary_path.write_text(json.dumps(summary, indent=2, default=lambda v: v.tolist()
                                       if isinstance(v, np.ndarray) else str(v)), encoding="utf-8")
    print(f"Summary -> {summary_path}")
    print(f"Figures -> {assets}")


if __name__ == "__main__":
    main()
