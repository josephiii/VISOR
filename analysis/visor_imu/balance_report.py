"""Figures for the balance and vestibular tasks (Tier S): one per task, plus an overview.

Shares the characterization report's print style and validated palette. Colour
carries one meaning per figure: eyes open (blue) against eyes closed (orange)
in the stance figures; head (gyroscope, blue) against camera image (orange) in
the impulse and gaze figures. Every figure has a legend, and every number it
shows is also in the report's tables.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.lines import Line2D
from matplotlib.patches import Ellipse
from matplotlib.ticker import FuncFormatter, LogLocator, NullFormatter
import numpy as np
import pandas as pd

from . import balance
from .loader import Session
from .metrics import YAW_AXIS
from .report import INK, INK_SECONDARY, MUTED, SERIES, SURFACE, _despine

EYES_COLOR = {"open": SERIES[0], "closed": SERIES[1]}
HEAD_COLOR, CAMERA_COLOR = SERIES[0], SERIES[1]


def _note(fig, text: str, y: float = -0.02) -> None:
    fig.text(0.01, y, text, fontsize=7, color=MUTED, va="top", wrap=True)


def _hold_text(row: dict[str, Any]) -> str:
    planned = row.get("planned_s")
    if row.get("balance_lost"):
        return f"balance lost at {row['hold_s']:.1f} s" + (f" of {planned:.0f}" if planned else "")
    return f"held {planned or row['hold_s']:.0f} s"


# ============================ stances ============================


def _ellipse_reach(e: dict[str, float]) -> float:
    """Half-width or half-height of the ellipse's bounding box, whichever is larger."""
    phi = np.radians(e["angle_deg"])
    a, b = e["major_deg"], e["minor_deg"]
    return float(max(np.hypot(a * np.cos(phi), b * np.sin(phi)), np.hypot(a * np.sin(phi), b * np.cos(phi))))


def _stabilogram(ax, row: dict[str, Any] | None, sway: balance.Sway | None, lim: float, title: str) -> None:
    ax.set_xlim(-lim, lim)
    ax.set_ylim(-lim, lim)
    ax.set_aspect("equal")
    ax.axhline(0, color=MUTED, linewidth=0.5)
    ax.axvline(0, color=MUTED, linewidth=0.5)
    ax.set_title(title, loc="left", fontsize=9.5)
    _despine(ax)
    # The panel's result sits under its axis, where a long "balance lost" note fits.
    stats = ""
    if row is None or sway is None:
        ax.text(0, 0, "not recorded" if row is None else "too short to measure",
                ha="center", va="center", fontsize=8.5, color=MUTED,
                bbox={"facecolor": SURFACE, "edgecolor": "none", "pad": 3})
        if row is not None:
            stats = _hold_text(row)
    else:
        ax.plot(sway.ml, sway.ap, color=EYES_COLOR.get(row["eyes"], SERIES[0]), linewidth=0.8, alpha=0.9)
        e = row["sway"]["ellipse"]
        ax.add_patch(Ellipse((0, 0), 2 * e["major_deg"], 2 * e["minor_deg"], angle=e["angle_deg"],
                             fill=False, edgecolor=INK, linewidth=1.1))
        stats = f"{_hold_text(row)} · area {e['area_deg2']:.2f} °²"
    ax.set_xlabel("ML sway (°, + right)" + (f"\n{stats}" if stats else ""))


def fig_stance(task: str, rows: list[dict[str, Any]], series: dict[str, balance.Sway],
               out: Path, who: str) -> Path:
    """Sway paths for every condition of one stance task, on shared axes."""
    variants = {"romberg": [None], "tandem": ["left", "right"],
                "single_leg": ["dominant", "non_dominant"]}[task]
    names = {None: "", "left": "left foot in front", "right": "right foot in front",
             "dominant": "dominant leg", "non_dominant": "non-dominant leg"}
    found = {(r["eyes"], r["variant"]): r for r in rows if r["task"] == task}
    # One scale for every panel, so eyes-closed sway reads as larger at a glance.
    reach = [float(np.max(np.abs(np.concatenate([p.ap, p.ml]))))
             for p in (series.get(r["trial_id"]) for r in found.values()) if p is not None]
    reach += [_ellipse_reach(r["sway"]["ellipse"]) for r in found.values() if r.get("sway")]
    lim = max([1.0, *reach]) * 1.08

    ncols = len(variants)
    nrows = 1 if task == "romberg" else 2
    if task == "romberg":
        fig, axes = plt.subplots(1, 2, figsize=(7.4, 3.9))
        cells = [(axes[0], "open", None), (axes[1], "closed", None)]
    else:
        fig, axes = plt.subplots(nrows, ncols, figsize=(7.4, 7.2))
        cells = [(axes[i, j], eyes, v) for i, eyes in enumerate(("open", "closed"))
                 for j, v in enumerate(variants)]
    for ax, eyes, variant in cells:
        row = found.get((eyes, variant))
        title = f"Eyes {eyes}" + (f", {names[variant]}" if variant else "")
        _stabilogram(ax, row, series.get(row["trial_id"]) if row else None, lim, title)
    for ax, *_ in cells:
        ax.set_ylabel("AP sway (°, + forward)")
    handles = [Line2D([], [], color=EYES_COLOR["open"], lw=1.6), Line2D([], [], color=EYES_COLOR["closed"], lw=1.6),
               Line2D([], [], color=INK, lw=1.1)]
    fig.legend(handles, ["eyes open", "eyes closed", "95% ellipse"], loc="upper right", ncol=3,
               fontsize=8, bbox_to_anchor=(0.99, 0.995))
    fig.suptitle(f"{balance.TASK_TITLES[task]}: head sway — {who}", x=0.01, ha="left",
                 fontsize=11, fontweight="bold", color=INK)
    fig.tight_layout(rect=(0, 0, 1, 0.95))
    # Clear of the two-line axis labels, which reach further down a one-row figure.
    _note(fig, "Head tilt integrated from the gyroscope in the trial's gravity-levelled mean pose, bias fitted "
               "against the accelerometer; about its mean, from 2 s after the start tones and without the last "
               "1 s before a lost hold. Head sway is not centre-of-pressure sway.",
          y=-0.07 if nrows == 1 else -0.025)
    fig.savefig(out)
    plt.close(fig)
    return out


VARIANT_LABEL = {("romberg", None): "Romberg", ("tandem", "left"): "Tandem, left foot front",
                 ("tandem", "right"): "Tandem, right foot front",
                 ("single_leg", "dominant"): "Single leg, dominant",
                 ("single_leg", "non_dominant"): "Single leg, non-dominant"}


def fig_overview(rows: list[dict[str, Any]], ratios: list[dict[str, Any]], out: Path, who: str) -> Path:
    """Sway area for every stance, eyes open against eyes closed."""
    order = [k for k in VARIANT_LABEL if any((r["task"], r["variant"]) == k for r in rows)]
    fig, ax = plt.subplots(figsize=(7.6, 0.62 * len(order) + 1.5))
    y = {k: len(order) - 1 - i for i, k in enumerate(order)}
    ratio = {(r["task"], r["variant"]): r["area_ratio"] for r in ratios}
    values = [r["sway"]["ellipse"]["area_deg2"] for r in rows if r.get("sway")]
    lo, hi = (min(values), max(values)) if values else (0.1, 10.0)
    for key in order:
        pair = {r["eyes"]: r for r in rows if (r["task"], r["variant"]) == key and r.get("sway")}
        xs = [pair[e]["sway"]["ellipse"]["area_deg2"] for e in ("open", "closed") if e in pair]
        if len(xs) == 2:
            ax.plot(xs, [y[key]] * 2, color=MUTED, linewidth=1.2, zorder=1)
        for eyes, r in pair.items():
            lost = r.get("balance_lost")
            ax.scatter([r["sway"]["ellipse"]["area_deg2"]], [y[key]], s=64, zorder=3,
                       facecolor=SURFACE if lost else EYES_COLOR[eyes], edgecolor=EYES_COLOR[eyes],
                       linewidth=2.0)
        if key in ratio:
            ax.annotate(f"×{ratio[key]:.1f}", xy=(hi * 1.6, y[key]), fontsize=8, color=INK_SECONDARY,
                        va="center", annotation_clip=False)
    ax.set_xscale("log")
    ax.set_xlim(lo / 1.6, hi * 1.5)
    ax.xaxis.set_major_locator(LogLocator(subs=(1.0, 3.0)))
    ax.xaxis.set_major_formatter(FuncFormatter(lambda v, _: f"{v:g}"))
    ax.xaxis.set_minor_formatter(NullFormatter())
    ax.set_yticks([y[k] for k in order], [VARIANT_LABEL[k] for k in order])
    ax.set_ylim(-0.7, len(order) - 0.3)
    ax.set_xlabel("95% sway ellipse area (°², log scale)")
    ax.grid(axis="y", visible=False)
    ax.annotate("eyes closed ÷ open", xy=(hi * 1.6, len(order) - 0.45), fontsize=7, color=MUTED,
                annotation_clip=False)
    _despine(ax)
    handles = [Line2D([], [], marker="o", linestyle="", markersize=8, color=EYES_COLOR["open"]),
               Line2D([], [], marker="o", linestyle="", markersize=8, color=EYES_COLOR["closed"]),
               Line2D([], [], marker="o", linestyle="", markersize=8, markerfacecolor=SURFACE,
                      markeredgecolor=INK_SECONDARY, markeredgewidth=2)]
    ax.legend(handles, ["eyes open", "eyes closed", "balance lost before the end"], loc="lower center",
              bbox_to_anchor=(0.5, 1.0), ncol=3, fontsize=8)
    fig.suptitle(f"Standing balance: sway by condition — {who}", x=0.01, ha="left", fontsize=11,
                 fontweight="bold", color=INK, y=1.04)
    fig.savefig(out)
    plt.close(fig)
    return out


# ============================ head impulses ============================


def fig_head_impulses(session: Session, impulses: pd.DataFrame, image: pd.DataFrame | None,
                      out: Path, who: str) -> Path:
    """Each impulse's head velocity, aligned at its peak, left and right; and every peak."""
    df = session.motion
    t = df["tPerf"].to_numpy(dtype="float64") / 1000.0
    yaw = df[YAW_AXIS].to_numpy(dtype="float64")
    found = impulses[impulses["detected"]] if len(impulses) else impulses
    fig, axes = plt.subplots(1, 3, figsize=(10.4, 3.7), gridspec_kw={"width_ratios": [1, 1, 0.9]})
    top = max(100.0, float(found["peak_dps"].max()) * 1.15) if len(found) else 300.0
    for ax, side in zip(axes[:2], ("left", "right")):
        sign = 1.0 if side == "left" else -1.0
        rows = found[found["direction"] == side] if len(found) else found
        for _, imp in rows.iterrows():
            sel = (t >= imp["peak_t_s"] - 0.25) & (t <= imp["peak_t_s"] + 0.45)
            ax.plot((t[sel] - imp["peak_t_s"]) * 1000, sign * yaw[sel], color=HEAD_COLOR, linewidth=1.1)
            if image is not None and len(image):
                isel = (image["t_s"] >= imp["peak_t_s"] - 0.25) & (image["t_s"] <= imp["peak_t_s"] + 0.45)
                ax.scatter((image["t_s"][isel] - imp["peak_t_s"]) * 1000, sign * image["image_dps"][isel],
                           s=18, color=CAMERA_COLOR, edgecolors=SURFACE, linewidths=0.8, zorder=3)
        ax.axhline(balance.IMPULSE_RAPID_DPS, color=MUTED, linewidth=0.8)
        ax.set_ylim(-0.15 * top, top)
        ax.set_xlim(-250, 450)
        ax.set_xlabel("time from peak (ms)")
        ax.set_title(f"{side.capitalize()} impulses (n = {len(rows)})", loc="left", fontsize=9.5)
        _despine(ax)
    axes[0].set_ylabel("head velocity (°/s)")
    axes[0].annotate(f"{balance.IMPULSE_RAPID_DPS:.0f} °/s", xy=(445, balance.IMPULSE_RAPID_DPS),
                     xytext=(0, 3), textcoords="offset points", ha="right", fontsize=7, color=MUTED)

    c = axes[2]
    if len(impulses):
        for _, imp in impulses.iterrows():
            x = imp["cue"] + 1
            if not imp["detected"]:
                c.scatter([x], [0], marker="x", s=36, color=MUTED, zorder=3)
                continue
            marker = "<" if imp["direction"] == "left" else ">"
            c.scatter([x], [imp["peak_dps"]], marker=marker, s=64, zorder=3, linewidths=1.8,
                      facecolor=INK_SECONDARY if imp["rapid"] else SURFACE, edgecolor=INK_SECONDARY)
        c.set_xticks(range(1, len(impulses) + 1))
    c.axhline(balance.IMPULSE_RAPID_DPS, color=MUTED, linewidth=0.8)
    c.set_ylim(0, top)
    c.set_xlabel("tone")
    c.set_ylabel("peak head velocity (°/s)")
    c.set_title("Peak velocity, in tone order", loc="left", fontsize=9.5)
    c.grid(axis="x", visible=False)
    _despine(c)

    handles = [Line2D([], [], color=HEAD_COLOR, lw=1.6),
               Line2D([], [], marker="o", linestyle="", markersize=6, color=CAMERA_COLOR),
               Line2D([], [], marker="<", linestyle="", markersize=7, color=INK_SECONDARY),
               Line2D([], [], marker=">", linestyle="", markersize=7, color=INK_SECONDARY),
               Line2D([], [], marker="<", linestyle="", markersize=7, markerfacecolor=SURFACE,
                      markeredgecolor=INK_SECONDARY)]
    labels = ["head (gyroscope)", "camera image", "left", "right", f"slower than {balance.IMPULSE_RAPID_DPS:.0f} °/s"]
    if image is None or not len(image):
        handles, labels = handles[:1] + handles[2:], labels[:1] + labels[2:]
    fig.legend(handles, labels, loc="upper right", ncol=len(labels), fontsize=7.5, bbox_to_anchor=(0.99, 0.99))
    fig.suptitle(f"Head impulse test — {who}", x=0.01, ha="left", fontsize=11, fontweight="bold", color=INK)
    fig.tight_layout(rect=(0, 0, 1, 0.92))
    _note(fig, "Head kinematics only: a clinical head impulse test also records the eyes, so this is not a "
               "VOR gain. Camera points are the image motion the head-fixed camera saw, converted to °/s; "
               "frames blurred by fast turns fail the quality gates and are absent.")
    fig.savefig(out)
    plt.close(fig)
    return out


# ============================ gaze stabilization ============================


def fig_gaze(rows: list[dict[str, Any]], out: Path, who: str, window_s: float = 6.0) -> Path:
    """Head velocity in the commanded plane, and the camera's view of it."""
    fig, axes = plt.subplots(len(rows), 1, figsize=(8.6, 2.6 * len(rows) + 0.6), squeeze=False)
    for ax, r in zip(axes[:, 0], rows):
        s, m, image = r["session"], r["metrics"], r.get("image")
        df = s.motion
        t = df["tPerf"].to_numpy(dtype="float64") / 1000.0
        t0 = balance.hold(s)["start_s"] + 5.0
        sel = (t >= t0) & (t <= t0 + window_s)
        ax.plot(t[sel] - t0, df[m["axis"]].to_numpy()[sel], color=HEAD_COLOR, linewidth=1.4,
                label="head (gyroscope)")
        if image is not None and len(image):
            isel = (image["t_s"] >= t0) & (image["t_s"] <= t0 + window_s)
            ax.plot(image["t_s"][isel] - t0, image["image_dps"][isel], color=CAMERA_COLOR, linewidth=1.0,
                    marker="o", markersize=3.5, label=r.get("image_label", "camera image"))
        axis_name = "yaw" if m["plane"] == "horizontal" else "pitch"
        paced = f" of {m['paced_hz']:.2f} paced" if m.get("paced_hz") else ""
        ax.set_title(f"{m['plane'].capitalize()} ({axis_name}): {m['frequency_hz']:.2f} Hz{paced}, "
                     f"±{m['amplitude_deg']:.1f}°", loc="left", fontsize=9.5)
        if r.get("scale_ratio") is not None:
            ax.set_title(f"camera ÷ head = {r['scale_ratio']:.2f} at the far-scene scale", loc="right",
                         fontsize=7.5, color=INK_SECONDARY, fontweight="normal")
        ax.set_ylabel(f"{axis_name} velocity (°/s)")
        _despine(ax)
    axes[-1, 0].set_xlabel("time (s)")
    axes[0, 0].legend(loc="lower left", bbox_to_anchor=(0.0, 1.12), ncol=2, fontsize=8)
    fig.suptitle(f"Gaze stabilization while reading — {who}", x=0.01, ha="left", fontsize=11,
                 fontweight="bold", color=INK, y=1.0)
    fig.tight_layout()
    _note(fig, "Camera velocity uses the pixels-per-degree measured on the slow look-around (V4), at a "
               "distant scene. The camera sits a few centimetres in front of the neck's rotation axis, so "
               "near text slides further across the image than a distant scene for the same head turn: a "
               "camera ÷ head ratio above 1 is that parallax (about 1 + r/d), the same geometry that makes "
               "a near target need a VOR gain above 1.", y=0.0)
    fig.savefig(out)
    plt.close(fig)
    return out
