"""Figures for the IMU-only pose-tracking analysis.

Shares the characterization report's print style and validated three-slot
palette. Each figure answers one question about what a gyroscope and
accelerometer alone can recover, and says so in its title.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.colors import LinearSegmentedColormap
from matplotlib.patches import FancyArrowPatch, FancyBboxPatch
import numpy as np
import pandas as pd

from . import pose
from .report import (
    ACCENT, AXIS, GRID, INK, INK_SECONDARY, MUTED, SERIES, SURFACE, _despine,
)

# Sequential blue ramp (palette.md, steps 100 -> 700) for magnitude and time.
BLUE_RAMP = LinearSegmentedColormap.from_list(
    "visor_blue", ["#cde2fb", "#86b6ef", "#3987e5", "#256abf", "#184f95", "#0d366b"])
# Ordinal time ramp that stays ≥ 2:1 against the surface at its light end.
TIME_RAMP = LinearSegmentedColormap.from_list(
    "visor_time", ["#86b6ef", "#3987e5", "#256abf", "#184f95", "#0d366b"])
REFERENCE = MUTED


def _note(fig, text: str, y: float = -0.03) -> None:
    fig.text(0.01, y, text, fontsize=7, color=MUTED, va="top", wrap=True)


# ============================ 0. pipeline diagram ============================


def fig_pipeline(out: Path) -> Path:
    """Block diagram of the estimator, with what is bounded and what drifts."""
    fig, ax = plt.subplots(figsize=(10.0, 5.0))
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 52)
    ax.axis("off")

    good = "#e9f5ef"   # quiet wash of the aqua slot: bounded outputs
    warn = "#fdeee7"   # quiet wash of the orange slot: drifting outputs

    def box(x, y, w, h, title, body="", fill=SURFACE):
        ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.3,rounding_size=1.0",
                                    linewidth=0.9, edgecolor=AXIS, facecolor=fill))
        ax.text(x + w / 2, y + h - 1.8, title, ha="center", va="top", fontsize=8.5,
                fontweight="bold", color=INK)
        if body:
            ax.text(x + w / 2, y + h - 5.4, body, ha="center", va="top", fontsize=7,
                    color=INK_SECONDARY, linespacing=1.4)

    def arrow(x0, y0, x1, y1, label="", offset=(0.0, 0.9)):
        ax.add_patch(FancyArrowPatch((x0, y0), (x1, y1), arrowstyle="-|>", mutation_scale=9,
                                     linewidth=0.9, color=INK_SECONDARY, shrinkA=0, shrinkB=0))
        if label:
            ax.text((x0 + x1) / 2 + offset[0], (y0 + y1) / 2 + offset[1], label, ha="center",
                    va="bottom", fontsize=6.5, color=MUTED)

    # column 1: sensors
    box(1, 31, 16, 13, "Gyroscope", "rrAlpha, rrBeta, rrGamma\n°/s, 0.1 rounding\n→ ωx, ωy, ωz")
    box(1, 5, 16, 14, "Accelerometer", "agx, agy, agz\nm/s², 0.1 rounding\nspecific force incl. g")
    # column 2: estimation
    box(25, 21, 20, 23, "Attitude filter",
        "Mahony complementary\n\n"
        r"$\dot{q} = \frac{1}{2}\,q \otimes (\omega + K_p e + K_i \int e)$" "\n"
        r"$e = \hat{a} \times R^{\top} \hat{z}$" "\n\n"
        r"$K_p$ reduced when $|a| \neq g$")
    box(25, 1, 20, 14, "Gravity removal", r"$a_w = R\,f - g\,\hat{z}$" "\n" "g from in-run still periods")
    # column 3: raw outputs
    box(53, 34, 19, 10, "Roll & pitch", "gravity-referenced: bounded", fill=good)
    box(53, 19.5, 19, 11.5, "Yaw", "no compass reference:\ndrifts at gyro bias", fill=warn)
    box(53, 1, 19, 14, "Double integration",
        r"$v = \int a_w,\;\; p = \int v$" "\n" r"error $\propto t^2$: diverges" "\n" "in seconds unaided",
        fill=warn)
    # column 4: constraints
    box(80, 18, 19, 17, "Step dead reckoning",
        "steps: vertical-accel peaks\nheading: filter yaw\nlength: assumed 0.70 m", fill=good)
    box(80, 1, 19, 14, "ZUPT", "still: |ω| < 5 °/s and\n||a| − g| < 0.3 m/s²\n→ v = 0, de-drift per move",
        fill=good)

    arrow(17, 37.5, 25, 37.5)
    arrow(17, 16, 25, 25, "tilt reference", offset=(-1.2, 1.2))
    arrow(17, 9, 25, 8)
    arrow(35, 21, 35, 15)
    ax.text(36, 17.4, "R", fontsize=7, color=MUTED, va="center")
    arrow(45, 38.5, 53, 39)
    arrow(45, 27, 53, 25.5)
    arrow(45, 8, 53, 8, r"$a_w$")
    arrow(72, 25.5, 80, 26.5, "heading")
    arrow(72, 12.5, 80, 20.5)
    ax.text(77.4, 15.2, r"$a_{w,z}$", fontsize=6.5, color=MUTED, ha="right")
    arrow(72, 8, 80, 8, "constrain")

    ax.text(1, 51, "IMU-only 6-DOF head pose: what each stage contributes",
            fontsize=11, fontweight="bold", color=INK, va="top")
    for x, fill, text in ((74, good, "bounded"), (86, warn, "drifts")):
        ax.add_patch(FancyBboxPatch((x, 48.2), 2.4, 1.6, boxstyle="round,pad=0.1", facecolor=fill,
                                    edgecolor=AXIS, linewidth=0.6))
        ax.text(x + 3.4, 49.0, text, fontsize=7, color=INK_SECONDARY, va="center")
    fig.savefig(out)
    plt.close(fig)
    return out


# ============================ 1. axis identification ============================


def fig_axis_identification(shares: pd.DataFrame, mapping: pd.DataFrame, out: Path) -> Path:
    """Which gyro channel is which head axis, settled two independent ways."""
    fig, (a, b) = plt.subplots(1, 2, figsize=(9.6, 3.6), gridspec_kw={"width_ratios": [1, 1.25]})

    matrix = shares.to_numpy() * 100
    a.imshow(matrix, cmap=BLUE_RAMP, vmin=0, vmax=100, aspect="auto")
    a.set_xticks(range(3), [f"{c}\n({pose.HEAD_AXIS[c]})" for c in shares.columns])
    a.set_yticks(range(len(shares.index)), shares.index)
    a.tick_params(length=0)
    a.grid(False)
    for i in range(matrix.shape[0]):
        for j in range(matrix.shape[1]):
            v = matrix[i, j]
            a.text(j, i, f"{v:.0f}%", ha="center", va="center", fontsize=9,
                   color="#ffffff" if v > 45 else INK, fontweight="bold" if v > 45 else "normal")
    for side in a.spines.values():
        side.set_visible(False)
    a.set_title("Share of gyro RMS by channel", loc="left")
    a.set_xlabel("gyro channel (inferred head axis)")
    a.set_ylabel("commanded movement")

    top = mapping.head(6).copy()
    spec = mapping[mapping["is_w3c_spec"]]
    if not spec.empty and not spec.index[0] in top.index:
        top = pd.concat([top, spec])
    top = top.iloc[::-1]
    colors = [SERIES[0] if r else "#c3c2b7" for r in top["is_adopted"]]
    y = np.arange(len(top))
    b.barh(y, top["residual_ms2"], height=0.55, color=colors)
    labels = []
    for _, r in top.iterrows():
        tag = "  (adopted)" if r["is_adopted"] else ("  (W3C spec)" if r["is_w3c_spec"] else "")
        labels.append(r["mapping"] + tag)
    b.set_yticks(y, labels, fontsize=7)
    for yi, v in zip(y, top["residual_ms2"]):
        b.text(v + 0.15, yi, f"{v:.2f}", va="center", fontsize=7.5, color=INK_SECONDARY)
    b.set_xlabel("gravity-prediction residual (m/s²), lower = physically consistent")
    b.set_title("Gyro-predicted vs measured gravity motion", loc="left")
    b.grid(axis="y", visible=False)
    b.set_xlim(0, top["residual_ms2"].max() * 1.15)
    _despine(b)

    fig.suptitle("Axis identification: yaw is rrBeta, pitch is rrAlpha, roll is rrGamma",
                 x=0.01, ha="left", fontsize=11, fontweight="bold", color=INK)
    fig.tight_layout()
    _note(fig, "Left: B1/B2/B3 paced trials pooled across participants. Right: over 1.5 s windows the gyro "
               "alone predicts how gravity moves in the sensor frame; each of the 48 signed channel→axis "
               "assignments is scored against the accelerometer (all completed B1–B3, C2, C4 sessions).")
    fig.savefig(out)
    plt.close(fig)
    return out


# ============================ 2. orientation ============================


def fig_orientation(panels: list[dict[str, Any]], out: Path, window_s: float = 24.0) -> Path:
    """IMU-only roll/pitch/yaw against the platform's fused orientation."""
    fig, axes = plt.subplots(len(panels), 1, figsize=(9.6, 2.35 * len(panels)), sharex=False)
    for ax, p in zip(np.atleast_1d(axes), panels):
        c = p["comparison"]
        key = p["axis"]
        t0 = c["t"].iloc[0] + p.get("offset_s", 4.0)
        sel = (c["t"] >= t0) & (c["t"] <= t0 + window_s)
        tt = c["t"][sel] - t0
        ref, est = c[f"{key}_ref"][sel], c[f"{key}_imu"][sel]
        if key == "yaw":
            # Heading origins are arbitrary on both sides; centering each on its
            # own median shows the direction of motion without a fitted offset.
            ref, est = ref - np.median(ref), est - np.median(est)
        # The reference is drawn wide and pale underneath so that where the two
        # agree, both remain visible rather than one hiding the other.
        ax.plot(tt, ref, color=SERIES[1], linewidth=3.4, alpha=0.55,
                label="platform orientation (fused, compass)", solid_capstyle="round")
        ax.plot(tt, est, color=SERIES[0], linewidth=1.4, label="IMU-only filter (gyro + accel)")
        ax.set_ylabel(f"{key} (°)" + (", centred" if key == "yaw" else ""))
        ax.set_title(f"{key.capitalize()} — {p['trial']}", loc="left", fontsize=9.5)
        ax.set_title(p["caption"], loc="right", fontsize=7.5, color=INK_SECONDARY,
                     fontweight="normal")
        _despine(ax)
    first = np.atleast_1d(axes)[0]
    first.legend(loc="lower left", ncol=2, bbox_to_anchor=(0.0, 1.14), fontsize=8)
    np.atleast_1d(axes)[-1].set_xlabel("time (s)")
    fig.suptitle("Orientation from gyro + accelerometer only", x=0.01, ha="left",
                 fontsize=11, fontweight="bold", color=INK, y=1.02)
    fig.tight_layout()
    _note(fig, "Nothing is fitted between the two estimates. The platform stream is not ground truth: its "
               "roll/pitch share our accelerometer; its heading adds the magnetometer, and moves opposite to "
               "the gyro here.", y=0.0)
    fig.savefig(out)
    plt.close(fig)
    return out


def fig_scan_map(angles: pd.DataFrame, sweeps: pd.DataFrame, out: Path, trial: str) -> Path:
    """Head-direction trace during compensatory scanning — the 3-DOF payoff."""
    fig, (a, b) = plt.subplots(1, 2, figsize=(9.6, 3.6), gridspec_kw={"width_ratios": [1.6, 1]})
    yaw = angles["yaw"] - np.median(angles["yaw"])
    t = angles["t"] - angles["t"].iloc[0]
    sc = a.scatter(yaw, angles["pitch"], c=t, cmap=TIME_RAMP, s=3, linewidths=0)
    a.set_xlabel("yaw (°, + = left, centred on median)")
    a.set_ylabel("pitch (°, + = up)")
    a.set_title("Head direction over the trial", loc="left", fontsize=9.5)
    a.invert_xaxis()
    cb = fig.colorbar(sc, ax=a, pad=0.02)
    cb.set_label("time (s)", color=INK_SECONDARY, fontsize=8)
    cb.outline.set_visible(False)
    cb.ax.tick_params(labelsize=7, colors=MUTED)
    _despine(a)

    if not sweeps.empty:
        for i, (direction, color) in enumerate((("left", SERIES[0]), ("right", SERIES[1]))):
            d = sweeps[sweeps["direction"] == direction]["amplitude_deg"].to_numpy()
            x = np.full(d.size, i) + (np.random.default_rng(1).random(d.size) - 0.5) * 0.25
            b.scatter(x, d, s=22, color=color, edgecolors=SURFACE, linewidths=1.2, zorder=3,
                      label=f"{direction} sweeps (n={d.size})")
            if d.size:
                b.plot([i - 0.25, i + 0.25], [d.mean()] * 2, color=INK, linewidth=1.4)
                b.text(i + 0.3, d.mean(), f"mean {d.mean():.0f}°", va="center", fontsize=7.5,
                       color=INK_SECONDARY)
        b.set_xticks([0, 1], ["left", "right"])
        b.set_xlim(-0.6, 1.9)
        b.set_ylabel("sweep amplitude (°)")
        b.set_title("Sweep amplitude by direction", loc="left", fontsize=9.5)
        b.legend(loc="lower center", fontsize=7, bbox_to_anchor=(0.5, -0.38), ncol=1)
        b.grid(axis="x", visible=False)
        _despine(b)
    fig.suptitle(f"Compensatory scanning measured in 3 DOF — {trial}", x=0.01, ha="left",
                 fontsize=11, fontweight="bold", color=INK)
    fig.tight_layout()
    fig.savefig(out)
    plt.close(fig)
    return out


# ============================ 3. translation drift ============================


def fig_position_drift(curves: dict[str, dict[str, np.ndarray]], bounds: dict[str, np.ndarray],
                       tau: np.ndarray, out: Path, tolerance_m: float = 0.10) -> Path:
    """Unaided double integration against the error terms that drive it."""
    fig, ax = plt.subplots(figsize=(7.6, 4.4))
    order = list(curves.items())
    for i, (label, c) in enumerate(order):
        color = SERIES[i]
        ax.fill_between(c["tau"], c["p25"], c["p75"], color=color, alpha=0.12, linewidth=0)
        ax.loglog(c["tau"], c["median"], color=color, linewidth=2.0, label=label)

    # The t² references are parallel on log-log axes, so labels placed along
    # them collide. They are labelled at the right edge instead, where the
    # constant factors between them separate them vertically.
    ref_styles = {
        "tilt_1deg": r"1° tilt error, $\frac{1}{2} g \sin(1°)\, t^2$",
        "quantization_bias": "0.05 m/s² offset (½ rounding step)",
        "tilt_0.1deg": "0.1° tilt error",
        "white_noise": r"measured white noise only, $\propto t^{1.5}$",
    }
    for key, text in ref_styles.items():
        y = bounds[key]
        ax.loglog(tau, y, color=REFERENCE, linewidth=0.9)
        ax.annotate(text, xy=(tau[-1], y[-1]), xytext=(5, 0), textcoords="offset points",
                    fontsize=7, color=INK_SECONDARY, ha="left", va="center", annotation_clip=False)

    ax.axhline(tolerance_m, color=ACCENT, linewidth=1.0)
    ax.annotate(f"{tolerance_m*100:.0f} cm", xy=(tau[0], tolerance_m), xytext=(2, 3),
                textcoords="offset points", fontsize=7.5, color=ACCENT)
    ax.set_xlim(tau[0], tau[-1])
    ax.set_ylim(1e-4, 1e3)
    ax.set_xlabel("integration time τ (s)")
    ax.set_ylabel("position error |p(τ)| (m)")
    ax.set_title("Unaided position drifts quadratically — metres within seconds", loc="left")
    ax.grid(True, which="major", color=GRID, linewidth=0.5)
    ax.legend(loc="upper left", fontsize=7.5)
    _despine(ax)
    _note(fig, "Truth is ≈0 in both recordings: glasses resting on a table, and a seated wearer turning the head "
               "(the neck pivot keeps real displacement to a few cm, so the seated curve below ~5 cm is partly "
               "genuine motion). Lines: median over all start times; bands: IQR. Grey lines are analytic, from "
               "the measured noise and the browser's 0.1 m/s² rounding.")
    fig.savefig(out)
    plt.close(fig)
    return out


def fig_drift_budget(rows: list[dict[str, Any]], out: Path) -> Path:
    """Time until each degree of freedom exceeds a working tolerance."""
    fig, ax = plt.subplots(figsize=(8.4, 0.42 * len(rows) + 1.6))
    xmax = 1e4
    y = np.arange(len(rows))[::-1]
    for yi, r in zip(y, rows):
        if r.get("header"):
            ax.text(0.105, yi, r["label"], fontsize=8.5, fontweight="bold", color=INK, va="center")
            continue
        seconds = r["seconds"]
        bounded = not np.isfinite(seconds)
        right = xmax if bounded else seconds
        ax.barh(yi, right - 0.1, left=0.1, height=0.5, color=SERIES[2] if bounded else SERIES[0])
        text = r.get("value_text") or (f"{seconds:,.1f} s" if seconds < 100 else f"{seconds:,.0f} s")
        if bounded:
            ax.text(xmax * 0.9, yi, text, va="center", ha="right", fontsize=7.5, color="#ffffff",
                    fontweight="bold")
        else:
            ax.text(right * 1.12, yi, text, va="center", fontsize=7.5, color=INK_SECONDARY)
        ax.text(0.085, yi, r["label"], va="center", ha="right", fontsize=7.8, color=INK_SECONDARY)
    ax.set_xscale("log")
    ax.set_xlim(0.1, xmax)
    ax.set_ylim(-0.7, len(rows) - 0.3)
    ax.set_yticks([])
    ax.set_xlabel("time until error exceeds tolerance (s, log scale)")
    ax.grid(axis="y", visible=False)
    ax.grid(axis="x", which="major", color=GRID, linewidth=0.5)
    for side in ("top", "right", "left"):
        ax.spines[side].set_visible(False)
    ax.set_title("Drift budget per degree of freedom (1° for angles, 10 cm for position)",
                 loc="left", x=-0.55)
    handles = [plt.Rectangle((0, 0), 1, 1, color=SERIES[0]), plt.Rectangle((0, 0), 1, 1, color=SERIES[2])]
    ax.legend(handles, ["drifts: time to tolerance", "bounded by a reference or constraint"],
              loc="upper right", fontsize=7, bbox_to_anchor=(1.0, 1.0))
    fig.subplots_adjust(left=0.36)
    _note(fig, "Angle rows from the QC-passed static recording (PTest A1): bias instability and turn-on bias of "
               "the yaw channel. Position rows: ½·a·t² = 0.10 m for each constant acceleration error, and the "
               "empirical medians of the drift figure.", y=0.02)
    fig.savefig(out)
    plt.close(fig)
    return out


# ============================ 4–5. sit-to-stand ============================


def fig_zupt_height(zupt: pose.ZuptResult, transitions: pd.DataFrame, cues: pd.DataFrame,
                    out: Path, trial: str) -> Path:
    """Vertical head displacement recovered with zero-velocity updates."""
    fig, (a, b) = plt.subplots(1, 2, figsize=(9.6, 3.6), gridspec_kw={"width_ratios": [2.6, 1]})
    t = zupt.t - zupt.t[0]
    z = zupt.p[:, 2] * 100

    edges = np.flatnonzero(np.diff(np.concatenate([[0], zupt.still.astype(int), [0]])))
    for s, e in zip(edges[::2], edges[1::2]):
        a.axvspan(t[s], t[min(e, len(t) - 1)], color=GRID, alpha=0.8, linewidth=0, zorder=0)
    for _, c in cues.iterrows():
        a.axvline(c["t_s"] - zupt.t[0], color=MUTED, linewidth=0.6, linestyle=":", zorder=1)
    a.plot(t, z, color=SERIES[0], linewidth=1.6, zorder=3)
    a.set_xlabel("time (s)")
    a.set_ylabel("head height change (cm)")
    a.set_title("Height from ZUPT-aided double integration", loc="left", fontsize=9.5)
    a.set_title("grey bands: stationary (velocity reset) · dotted: cues", loc="right", fontsize=7,
                color=MUTED, fontweight="normal")
    a.set_ylim(top=max(float(np.nanmax(z)) * 1.1, 10))
    _despine(a)

    good = transitions[~transitions["merged"]]
    for i, (kind, color) in enumerate((("stand", SERIES[0]), ("sit", SERIES[1]))):
        d = good[good["kind"] == kind]["dz_cm"].abs().to_numpy()
        x = np.full(d.size, i) + (np.random.default_rng(2).random(d.size) - 0.5) * 0.22
        b.scatter(x, d, s=24, color=color, edgecolors=SURFACE, linewidths=1.2, zorder=3,
                  label=f"{kind} (n={d.size})")
        if d.size:
            b.plot([i - 0.22, i + 0.22], [d.mean()] * 2, color=INK, linewidth=1.4)
            b.text(i + 0.27, d.mean(), f"{d.mean():.1f} ± {d.std(ddof=1):.1f}", va="center",
                   fontsize=7.5, color=INK_SECONDARY)
    b.set_xticks([0, 1], ["stand up\n(rise)", "sit down\n(drop)"])
    b.set_xlim(-0.5, 1.9)
    b.set_ylim(0, max(50, float(good["dz_cm"].abs().max()) * 1.2) if len(good) else 50)
    b.set_ylabel("|Δ height| per transition (cm)")
    b.set_title("Repeatability", loc="left", fontsize=9.5)
    b.legend(loc="lower center", fontsize=7, ncol=2, bbox_to_anchor=(0.5, -0.36))
    b.grid(axis="x", visible=False)
    _despine(b)

    fig.suptitle(f"Vertical displacement bounded by zero-velocity updates — {trial}", x=0.01,
                 ha="left", fontsize=11, fontweight="bold", color=INK)
    fig.tight_layout()
    merged = int(transitions["merged"].sum())
    text = ("Height is the robust component: a tilt error δθ leaks gravity into vertical acceleration only as "
            "g·(1−cos δθ), but into horizontal as g·sin δθ.")
    if merged:
        text += (f" {merged} movement(s) ran a stand and a sit together with no detectable stop; they are "
                 "excluded from the repeatability panel because no velocity reset bounds them, and they "
                 "offset the seated baseline in the time series.")
    _note(fig, text, y=0.02)
    fig.savefig(out)
    plt.close(fig)
    return out


def _triad(ax, origin, R, scale):
    """Head orientation at one pose. Gaze and up fully determine it; the third
    axis would only add ink, and its colour would collide with the path colours."""
    for v, color, width in ((R @ np.array([0, 0, -1.0]), INK, 1.8),
                            (R @ np.array([0, 1.0, 0]), SERIES[2], 1.8)):
        ax.plot([origin[0], origin[0] + v[0] * scale], [origin[1], origin[1] + v[1] * scale],
                [origin[2], origin[2] + v[2] * scale], color=color, linewidth=width,
                solid_capstyle="round")


def fig_pose_3d(zupt: pose.ZuptResult, R: np.ndarray, transitions: pd.DataFrame, out: Path,
                trial: str) -> Path:
    """Six-DOF head pose through each sit-to-stand transition."""
    fig = plt.figure(figsize=(10.2, 4.9))
    a = fig.add_subplot(1, 2, 1, projection="3d")
    b = fig.add_subplot(1, 2, 2)
    a.set_facecolor(SURFACE)

    good = transitions[~transitions["merged"]]
    colors = {"stand": SERIES[0], "sit": SERIES[1]}

    # Highlight one representative stand-up and the sit-down that follows it;
    # the rest are drawn faint so repeatability shows without clutter.
    stands = good[good["kind"] == "stand"]
    highlight: set[int] = set()
    if len(stands):
        pivot = stands.index[len(stands) // 2]
        highlight.add(pivot)
        after = good[(good.index > pivot) & (good["kind"] == "sit")]
        if len(after):
            highlight.add(after.index[0])

    for idx, tr in good.iterrows():
        i0, i1 = int(tr["i0"]), int(tr["i1"])
        # Stand-ups start at the seated origin; sit-downs end there, so both
        # kinds share one frame anchored at the seated pose.
        offset = zupt.p[i0] if tr["kind"] == "stand" else zupt.p[i1]
        seg = zupt.p[i0:i1 + 1] - offset
        bold = idx in highlight
        style = dict(color=colors[tr["kind"]], linewidth=2.0 if bold else 1.0,
                     alpha=1.0 if bold else 0.35, zorder=3 if bold else 2)
        a.plot(seg[:, 0], seg[:, 1], seg[:, 2], **style)
        b.plot(seg[:, 0] * 100, seg[:, 2] * 100, **style)
        if bold:
            for k in np.linspace(i0, i1, 6).astype(int):
                _triad(a, zupt.p[k] - offset, R[k], 0.08)

    a.set_xlabel("forward (m)", fontsize=7.5, labelpad=-4)
    a.set_ylabel("left (m)", fontsize=7.5, labelpad=-4)
    a.set_zlabel("up (m)", fontsize=7.5, labelpad=-4)
    a.tick_params(labelsize=6.5, pad=-3)
    a.set_xlim(-0.3, 0.7)
    a.set_ylim(-0.3, 0.3)
    a.set_zlim(-0.15, 0.5)
    a.set_box_aspect((1.0, 0.6, 0.65))
    a.view_init(elev=16, azim=-64)
    for pane in (a.xaxis.pane, a.yaxis.pane, a.zaxis.pane):
        pane.set_facecolor(SURFACE)
        pane.set_edgecolor(GRID)
    a.set_title("Position + orientation", loc="left", fontsize=9.5)
    handles = [plt.Line2D([], [], color=SERIES[0], lw=2.0), plt.Line2D([], [], color=SERIES[1], lw=2.0),
               plt.Line2D([], [], color=INK, lw=1.8), plt.Line2D([], [], color=SERIES[2], lw=1.8)]
    a.legend(handles, ["stand up", "sit down", "gaze direction", "head up"], loc="upper left",
             fontsize=7, ncol=2, bbox_to_anchor=(0.02, 0.98))

    b.set_xlabel("forward displacement (cm)")
    b.set_ylabel("height above seated pose (cm)")
    b.set_aspect("equal", adjustable="datalim")
    b.set_title(f"Side view, all {len(good)} bounded transitions", loc="left", fontsize=9.5)
    b.legend([plt.Line2D([], [], color=SERIES[0], lw=2.0), plt.Line2D([], [], color=SERIES[1], lw=2.0)],
             ["stand up (starts at seated pose)", "sit down (ends at seated pose)"],
             loc="upper left", fontsize=7)
    _despine(b)

    fig.suptitle(f"Six-DOF head trajectory from the IMU alone, per transition — {trial}", x=0.01,
                 ha="left", fontsize=11, fontweight="bold", color=INK)
    _note(fig, "Each transition is integrated between two stationary periods (ZUPT) and anchored at the seated "
               "pose; bold = one stand/sit pair with head orientation every ~0.6 s. Heights repeat within a few "
               "cm; forward displacement is far less repeatable, because tilt error leaks into horizontal "
               "acceleration at first order. Position is not tracked across the whole trial.", y=0.05)
    fig.subplots_adjust(left=0.0, right=0.98, wspace=0.08, top=0.86, bottom=0.15)
    fig.savefig(out)
    plt.close(fig)
    return out


# ============================ 6. walking ============================


def fig_walking(pdr: pose.PdrResult, angles: pd.DataFrame, out: Path, trial: str) -> Path:
    """Step-based dead reckoning: path shape from the IMU, scale assumed."""
    fig = plt.figure(figsize=(10.2, 4.6))
    gs = fig.add_gridspec(2, 2, width_ratios=[1.15, 1], height_ratios=[1.3, 1], hspace=0.55, wspace=0.25)
    a = fig.add_subplot(gs[:, 0])
    b = fig.add_subplot(gs[0, 1])
    c = fig.add_subplot(gs[1, 1])

    t = pdr.t - pdr.t[0]
    pts = pdr.xy
    for k in range(0, len(pts) - 1, 3):
        a.plot(pts[k:k + 4, 0], pts[k:k + 4, 1], color=TIME_RAMP(t[k] / t[-1]), linewidth=1.4,
               solid_capstyle="round")
    a.scatter(pdr.step_xy[1:, 0], pdr.step_xy[1:, 1], s=5, color=INK_SECONDARY, zorder=3, linewidths=0)
    a.scatter([0], [0], s=40, color=SERIES[1], edgecolors=SURFACE, linewidths=1.5, zorder=4)
    a.annotate("start", (0, 0), xytext=(6, 4), textcoords="offset points", fontsize=7.5, color=INK_SECONDARY)
    a.set_aspect("equal", adjustable="datalim")
    a.set_xlabel("x (m, initial heading)")
    a.set_ylabel("y (m, left)")
    a.set_title(f"Plan view, {len(pdr.step_t)} steps × {pdr.step_length_m:.2f} m (assumed)", loc="left",
                fontsize=9.5)
    sm = plt.cm.ScalarMappable(cmap=TIME_RAMP, norm=plt.Normalize(0, t[-1]))
    cb = fig.colorbar(sm, ax=a, pad=0.02, shrink=0.8)
    cb.set_label("time (s)", color=INK_SECONDARY, fontsize=8)
    cb.outline.set_visible(False)
    cb.ax.tick_params(labelsize=7, colors=MUTED)
    _despine(a)

    ta = angles["t"] - angles["t"].iloc[0]
    b.plot(ta, angles["yaw"] - angles["yaw"].iloc[0], color=SERIES[0], linewidth=1.4)
    b.set_ylabel("cumulative yaw (°)")
    b.set_title(f"Heading: {(angles['yaw'].iloc[-1]-angles['yaw'].iloc[0]):,.0f}° of turning "
                f"≈ {(angles['yaw'].iloc[-1]-angles['yaw'].iloc[0])/360:.1f} laps", loc="left", fontsize=9.5)
    _despine(b)

    win = (t >= 20) & (t <= 26)
    c.plot(t[win], pdr.bob[win] * 100, color=SERIES[0], linewidth=1.4)
    steps = pdr.step_t - pdr.t[0]
    for s in steps[(steps >= 20) & (steps <= 26)]:
        c.axvline(s, color=MUTED, linewidth=0.6, linestyle=":")
    c.set_xlabel("time (s)")
    c.set_ylabel("head bob (cm)")
    c.set_title(f"Vertical head bob, {pdr.cadence_hz:.2f} Hz steps (dotted)", loc="left", fontsize=9.5)
    _despine(c)

    fig.suptitle(f"Pedestrian dead reckoning from the head IMU — {trial}", x=0.01, ha="left",
                 fontsize=11, fontweight="bold", color=INK)
    _note(fig, "Path shape (turning, lap repetition) comes from the IMU; its scale is set entirely by the assumed "
               "step length and has no ground truth here. Head yaw is used as walking direction, so looking "
               "around while walking bends the path.", y=0.02)
    fig.subplots_adjust(left=0.07, right=0.98, top=0.87, bottom=0.16)
    fig.savefig(out)
    plt.close(fig)
    return out
