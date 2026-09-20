"""Figure generation and markdown reporting.

Figures are print-oriented: light surface, recessive chrome, thin marks. Every
multi-series plot carries both a legend and end-of-line direct labels, because
one of the three series colours sits below 3:1 contrast on the light surface and
must not rely on colour alone to be identified.

Palette: validated three-slot categorical set (blue / orange / aqua), which
clears CVD separation on all pairs.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any, Sequence

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

from .loader import Session
from .metrics import (
    ACCEL_AXES, GYRO_AXES, allan_deviation, allan_params, estimate_fs, psd, _finite,
)
from .rehab import angular_speed

SERIES = ("#2a78d6", "#eb6834", "#1baf7a")
SURFACE = "#fcfcfb"
INK = "#0b0b0b"
INK_SECONDARY = "#52514e"
MUTED = "#898781"
GRID = "#e1e0d9"
AXIS = "#c3c2b7"
ACCENT = "#d03b3b"

AXIS_LABELS = {
    "rrAlpha": "α (yaw)", "rrBeta": "β (pitch)", "rrGamma": "γ (roll)",
    "agx": "X", "agy": "Y", "agz": "Z",
}


def apply_style() -> None:
    plt.rcParams.update({
        "figure.facecolor": SURFACE,
        "axes.facecolor": SURFACE,
        "savefig.facecolor": SURFACE,
        "font.family": "sans-serif",
        "font.sans-serif": ["Segoe UI", "DejaVu Sans", "Arial"],
        "font.size": 9,
        "axes.titlesize": 11,
        "axes.titleweight": "bold",
        "axes.titlecolor": INK,
        "axes.labelcolor": INK_SECONDARY,
        "axes.labelsize": 9,
        "axes.edgecolor": AXIS,
        "axes.linewidth": 0.8,
        "axes.grid": True,
        "axes.axisbelow": True,
        "grid.color": GRID,
        "grid.linewidth": 0.6,
        "xtick.color": MUTED,
        "ytick.color": MUTED,
        "xtick.labelsize": 8,
        "ytick.labelsize": 8,
        "legend.frameon": False,
        "legend.fontsize": 8,
        "lines.linewidth": 1.6,
        "figure.dpi": 140,
        "savefig.bbox": "tight",
    })


def _despine(ax) -> None:
    for side in ("top", "right"):
        ax.spines[side].set_visible(False)


def _place_end_labels(ax, entries: list[tuple[float, float, str, str]],
                      min_gap_px: float = 12.0) -> None:
    """Direct labels at line ends — the relief for low-contrast hues.

    Curves that converge would otherwise stack their labels on top of each
    other, so label positions are separated in display space before being
    mapped back to data coordinates.
    """
    if not entries:
        return
    ax.figure.canvas.draw()
    to_display = ax.transData.transform
    to_data = ax.transData.inverted().transform

    points = [to_display((x, y)) for x, y, _, _ in entries]
    order = sorted(range(len(entries)), key=lambda i: points[i][1])

    ys = [points[i][1] for i in order]
    for k in range(1, len(ys)):
        if ys[k] - ys[k - 1] < min_gap_px:
            ys[k] = ys[k - 1] + min_gap_px

    for rank, i in enumerate(order):
        x_data, _, text, color = entries[i]
        _, y_adjusted = to_data((points[i][0], ys[rank]))
        ax.annotate(
            text, xy=(x_data, y_adjusted), xytext=(6, 0), textcoords="offset points",
            color=color, fontsize=8, fontweight="bold", va="center", clip_on=False,
        )


# ============================ figures ============================


def fig_timing_jitter(session: Session, out: Path) -> Path | None:
    df = session.motion
    if df.empty or "tPerf" not in df or len(df) < 10:
        return None
    t = _finite(df["tPerf"])
    dt = np.diff(t)
    dt = dt[(dt > 0) & (dt < np.percentile(dt, 99.9) * 3)]
    if dt.size < 10:
        return None

    fig, ax = plt.subplots(figsize=(6.2, 3.2))
    ax.hist(dt, bins=60, color=SERIES[0], edgecolor=SURFACE, linewidth=0.5)
    median = float(np.median(dt))
    p99 = float(np.percentile(dt, 99))
    ax.axvline(median, color=INK, linewidth=1.2, linestyle="-")
    ax.axvline(p99, color=ACCENT, linewidth=1.2, linestyle="--")
    ax.annotate(f"median {median:.2f} ms\n({1000/median:.1f} Hz)",
                xy=(median, ax.get_ylim()[1] * 0.92), xytext=(6, 0),
                textcoords="offset points", fontsize=8, color=INK, va="top")
    ax.annotate(f"p99 {p99:.2f} ms", xy=(p99, ax.get_ylim()[1] * 0.55),
                xytext=(6, 0), textcoords="offset points", fontsize=8,
                color=ACCENT, va="top")
    ax.set_xlabel("inter-sample interval (ms)")
    ax.set_ylabel("samples")
    ax.set_title(f"Sample timing — {session.trial_id}")
    _despine(ax)
    fig.savefig(out)
    plt.close(fig)
    return out


def fig_allan(session: Session, out: Path, axes: Sequence[str] = GYRO_AXES,
              unit: str = "°/s") -> Path | None:
    df = session.motion
    if df.empty:
        return None
    fs = estimate_fs(df)
    if fs <= 0:
        return None

    fig, ax = plt.subplots(figsize=(6.2, 3.8))
    labels: list[tuple[float, float, str, str]] = []
    for i, axis in enumerate(axes):
        if axis not in df:
            continue
        taus, adev = allan_deviation(df[axis].to_numpy(dtype="float64"), fs)
        if taus.size == 0:
            continue
        color = SERIES[i % len(SERIES)]
        label = AXIS_LABELS.get(axis, axis)
        ax.loglog(taus, adev, color=color, label=label)
        labels.append((taus[-1], adev[-1], label, color))
        params = allan_params(taus, adev)
        if params["bias_instability_tau_s"]:
            ax.plot([params["bias_instability_tau_s"]], [params["min_adev"]],
                    marker="o", markersize=5, color=color, zorder=5)

    if not labels:
        plt.close(fig)
        return None

    _place_end_labels(ax, labels)
    ax.set_xlabel("averaging time τ (s)")
    ax.set_ylabel(f"Allan deviation ({unit})")
    ax.set_title(f"Allan deviation — {session.trial_id}")
    ax.grid(True, which="both", color=GRID, linewidth=0.5)
    ax.legend(loc="upper right")
    _despine(ax)
    fig.text(0.01, -0.04,
             "Markers show the curve minimum (bias instability). "
             "Slope −1/2 is random walk; slope +1/2 is rate random walk.",
             fontsize=7, color=MUTED)
    fig.savefig(out)
    plt.close(fig)
    return out


def fig_psd(session: Session, out: Path, axes: Sequence[str] = GYRO_AXES,
            unit: str = "°/s") -> Path | None:
    df = session.motion
    if df.empty:
        return None
    fs = estimate_fs(df)
    if fs <= 0:
        return None

    fig, ax = plt.subplots(figsize=(6.2, 3.4))
    labels: list[tuple[float, float, str, str]] = []
    for i, axis in enumerate(axes):
        if axis not in df:
            continue
        freqs, density = psd(df[axis].to_numpy(dtype="float64"), fs)
        if freqs.size == 0:
            continue
        positive = freqs > 0
        color = SERIES[i % len(SERIES)]
        label = AXIS_LABELS.get(axis, axis)
        ax.loglog(freqs[positive], density[positive], color=color, label=label)
        labels.append((freqs[positive][-1], density[positive][-1], label, color))

    if not labels:
        plt.close(fig)
        return None

    _place_end_labels(ax, labels)
    ax.set_xlabel("frequency (Hz)")
    ax.set_ylabel(f"PSD ({unit}²/Hz)")
    ax.set_title(f"Power spectral density — {session.trial_id}")
    ax.grid(True, which="both", color=GRID, linewidth=0.5)
    ax.legend(loc="upper right")
    _despine(ax)
    fig.savefig(out)
    plt.close(fig)
    return out


def fig_timeseries_with_cues(session: Session, out: Path) -> Path | None:
    df = session.motion
    if df.empty or "tPerf" not in df:
        return None
    speed = angular_speed(df)
    t = np.asarray(df["tPerf"], dtype="float64") / 1000.0
    mask = np.isfinite(speed) & np.isfinite(t)
    if mask.sum() < 10:
        return None

    fig, ax = plt.subplots(figsize=(6.8, 3.2))
    ax.plot(t[mask], speed[mask], color=SERIES[0], linewidth=1.0)

    marks = session.cue_marks()
    cues = marks[marks["kind"] == "cue"] if not marks.empty else marks
    for _, row in cues.iterrows():
        ax.axvline(row["t_s"], color=MUTED, linewidth=0.7, linestyle=":", zorder=1)

    # Cues usually repeat a short vocabulary. Labelling every one is unreadable,
    # and labelling the first N clusters them all at the left, so label the
    # first occurrence of each distinct cue and let the tick lines carry the rest.
    low, high = ax.get_ylim()
    ax.set_ylim(low, high * 1.18)
    seen: set[str] = set()
    for _, row in cues.iterrows():
        label = str(row["label"])
        if label in seen or len(seen) >= 6:
            continue
        seen.add(label)
        ax.annotate(label, xy=(row["t_s"], high * 1.15), rotation=90, fontsize=7,
                    color=INK_SECONDARY, va="top", ha="right")

    if len(cues) > len(seen):
        ax.annotate(f"{len(cues)} cues total; first occurrence of each labelled",
                    xy=(0.99, 0.02), xycoords="axes fraction", fontsize=7,
                    color=MUTED, ha="right")

    ax.set_xlabel("time (s)")
    ax.set_ylabel("angular speed (°/s)")
    ax.set_title(f"Head angular speed with commanded cues — {session.trial_id}")
    _despine(ax)
    fig.savefig(out)
    plt.close(fig)
    return out


def fig_stillness(session: Session, out: Path, threshold: float | None = None) -> Path | None:
    df = session.motion
    if df.empty:
        return None
    speed = _finite(angular_speed(df))
    if speed.size < 32:
        return None

    fig, ax = plt.subplots(figsize=(6.2, 3.2))
    ax.hist(speed, bins=60, color=SERIES[2], edgecolor=SURFACE, linewidth=0.5)
    p95 = float(np.percentile(speed, 95))
    thr = threshold if threshold is not None else p95
    ax.axvline(thr, color=ACCENT, linewidth=1.4)
    ax.annotate(f"capture-gate threshold\n{thr:.2f} °/s (p95)",
                xy=(thr, ax.get_ylim()[1] * 0.85), xytext=(8, 0),
                textcoords="offset points", fontsize=8, color=ACCENT, va="top")
    ax.set_xlabel("angular speed (°/s)")
    ax.set_ylabel("samples")
    ax.set_title(f"Head-stability distribution — {session.trial_id}")
    _despine(ax)
    fig.savefig(out)
    plt.close(fig)
    return out


def fig_gait(session: Session, out: Path, gait: dict[str, Any] | None) -> Path | None:
    df = session.motion
    if df.empty or not gait or not gait.get("vertical_axis"):
        return None
    fs = estimate_fs(df)
    axis = gait["vertical_axis"]
    if axis not in df or fs <= 0:
        return None

    freqs, density = psd(df[axis].to_numpy(dtype="float64"), fs)
    if freqs.size == 0:
        return None
    band = (freqs >= 0.3) & (freqs <= 5.0)
    if not band.any():
        return None

    fig, ax = plt.subplots(figsize=(6.2, 3.2))
    ax.plot(freqs[band], density[band], color=SERIES[1])
    peak = gait.get("step_frequency_hz")
    if peak:
        ax.axvline(peak, color=ACCENT, linewidth=1.3, linestyle="--")
        ax.annotate(f"{peak:.2f} Hz\n{gait['cadence_steps_per_min']:.0f} steps/min\n"
                    f"({gait.get('confidence','?')} confidence)",
                    xy=(peak, max(density[band]) * 0.8), xytext=(8, 0),
                    textcoords="offset points", fontsize=8, color=ACCENT, va="top")
    ax.set_xlabel("frequency (Hz)")
    ax.set_ylabel("PSD ((m/s²)²/Hz)")
    ax.set_title(f"Gait spectrum, vertical axis {axis} — {session.trial_id}")
    _despine(ax)
    fig.savefig(out)
    plt.close(fig)
    return out


# ============================ markdown ============================


def _table(rows: list[tuple[str, Any]], headers: tuple[str, str] = ("Measure", "Value")) -> str:
    out = [f"| {headers[0]} | {headers[1]} |", "|---|---|"]
    for key, value in rows:
        if isinstance(value, float):
            value = f"{value:,.4g}"
        out.append(f"| {key} | {value} |")
    return "\n".join(out) + "\n"


def session_section(session: Session, analysis: dict[str, Any],
                    figures: dict[str, Path], assets_rel: str) -> str:
    """Render one trial's results as a markdown section."""
    lines: list[str] = []
    lines.append(f"### {session.trial_id} — {session.meta.get('trialTitle', '')}\n")
    lines.append(f"*{session.meta.get('purpose', '')}*\n")

    qc = analysis.get("qc") or {}
    if qc.get("violations"):
        lines.append("> **QC: session rejected for pooled statistics.**\n>")
        for violation in qc["violations"]:
            lines.append(f"> - {violation}")
        lines.append(">\n> Timing and event-stream observations below remain valid. "
                     "Statistics that assume a completed or stationary recording are "
                     "withheld.\n")
    elif qc:
        lines.append("> **QC: passed.**\n")

    timing = analysis.get("timing")
    if timing:
        lines.append("**Sample timing**\n")
        lines.append(_table([
            ("Samples", timing["n_samples"]),
            ("Duration (s)", timing["duration_s"]),
            ("Effective rate (Hz)", timing["effective_hz"]),
            ("Platform-reported rate (Hz)", timing["nominal_hz_from_interval"] or "not reported"),
            ("Median interval (ms)", timing["dt_median_ms"]),
            ("Interval SD (ms)", timing["dt_sd_ms"]),
            ("p99 interval (ms)", timing["dt_p99_ms"]),
            ("Longest gap (ms)", timing["longest_gap_ms"]),
            ("Dropouts (>3x median)", f"{timing['dropout_count']} ({timing['dropout_fraction']*100:.2f}%)"),
            ("Jitter CV", timing["jitter_cv"]),
            ("Event-clock slope vs performance.now()", timing["clock_slope"] or "n/a"),
        ]))

    nulls = analysis.get("fully_null_columns") or []
    if nulls:
        lines.append(f"**Fields null for the entire trial:** `{'`, `'.join(nulls)}`\n")

    # Statistics that presuppose a stationary device are withheld rather than
    # shown with a caveat: a number in a table gets quoted, a caveat does not.
    static_valid = analysis.get("static_stats_valid")
    if static_valid is False:
        stationarity = (analysis.get("qc") or {}).get("stationarity") or {}
        worst = stationarity.get("gyro_sd_dps") or {}
        detail = (f" Peak gyro SD was {max(worst.values()):.2f} °/s."
                  if worst else "")
        lines.append(
            "**Static statistics withheld.** This recording is not stationary, so "
            "accelerometer bias, noise floor and Allan deviation would describe how "
            "the glasses were moved rather than the sensor." + detail +
            " Re-run with the glasses resting untouched on a solid surface.\n")

    gravity = analysis.get("gravity")
    if gravity and static_valid is not False:
        lines.append("**Gravity-vector check**\n")
        lines.append(_table([
            ("Mean |a| (m/s²)", gravity["mean_magnitude"]),
            ("SD |a| (m/s²)", gravity["sd_magnitude"]),
            ("Error vs 9.80665 (m/s²)", gravity["error"]),
            ("Error (%)", gravity["error_pct"]),
        ]))

    allan = analysis.get("allan") or {}
    if allan and static_valid is not False:
        rows = []
        for axis, params in allan.items():
            label = AXIS_LABELS.get(axis, axis)
            if params.get("random_walk") is not None:
                rows.append((f"{label} random walk (°/√hr)", params["random_walk_per_sqrt_hr"]))
            if params.get("bias_instability") is not None:
                rows.append((f"{label} bias instability (°/s)", params["bias_instability"]))
                rows.append((f"{label} bias-instability τ (s)", params["bias_instability_tau_s"]))
        if rows:
            lines.append("**Allan deviation parameters**\n")
            lines.append(_table(rows))

    segments = analysis.get("cue_segments")
    if segments:
        lines.append("**Cue-aligned response**\n")
        lines.append(_table([
            ("Commanded movements", segments["count"]),
            ("Mean peak angular speed (°/s)", segments["mean_peak_dps"]),
            ("Max peak angular speed (°/s)", segments["max_peak_dps"]),
            ("Mean onset latency (s)", segments.get("mean_onset_latency_s")),
        ]))

    still = analysis.get("stillness")
    if still:
        lines.append("**Head stability**\n")
        lines.append(_table([
            ("Median angular speed (°/s)", still["median_dps"]),
            ("p95 (°/s)", still["p95_dps"]),
            ("p99 (°/s)", still["p99_dps"]),
            ("Recommended capture-gate threshold (°/s)", still["recommended_threshold_dps"]),
        ]))

    scan = analysis.get("scan")
    if scan and scan.get("sweeps"):
        lines.append("**Compensatory scanning**\n")
        lines.append(_table([
            ("Sweeps detected", scan["sweeps"]),
            ("Sweep rate (per min)", scan.get("sweep_rate_per_min")),
            ("Mean amplitude (°)", scan.get("mean_amplitude_deg")),
            ("Max amplitude (°)", scan.get("max_amplitude_deg")),
            ("Left / right sweeps", f"{scan.get('left_sweeps')} / {scan.get('right_sweeps')}"),
            ("Asymmetry index", scan.get("asymmetry_index")),
        ]))

    gait = analysis.get("gait")
    if gait:
        lines.append("**Gait**\n")
        if gait.get("detected"):
            lines.append(_table([
                ("Step frequency (Hz)", gait["step_frequency_hz"]),
                ("Cadence (steps/min)", gait["cadence_steps_per_min"]),
                ("Peak prominence ratio", gait["peak_prominence_ratio"]),
                ("Confidence", gait["confidence"]),
            ]))
        else:
            lines.append(f"Not detected: {gait.get('reason', 'weak spectral peak')}\n")

    saturation = analysis.get("saturation") or {}
    clipped = [a for a, v in saturation.items() if v.get("suspected_clipping")]
    if clipped:
        lines.append(f"**Suspected clipping on:** `{'`, `'.join(clipped)}` — "
                     "peak-rate figures from this trial are lower bounds.\n")

    for name, path in figures.items():
        if path:
            lines.append(f"\n![{name}]({assets_rel}/{path.name})\n")

    return "\n".join(lines) + "\n"
