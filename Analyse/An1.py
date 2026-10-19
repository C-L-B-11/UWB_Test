#!/usr/bin/env python3
"""
Reads rangingLog*.txt files, matches each to its reference distance via the
hh:mm in the file name (using ZuordnungAbständeZeiten.txt), and plots measured
distances per Technology (00, 01, 02) against the true distance.

Usage:
    python plot_ranging.py [folder]      (default: folder of this script)

Requires: matplotlib  (pip install matplotlib)
"""

import re
import sys
from collections import defaultdict
from pathlib import Path

import matplotlib.pyplot as plt

# ----------------------------------------------------------------------------
FOLDER = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent
SAVE_PNG = True  # also save the plots as PNG files into the folder
# ----------------------------------------------------------------------------

# rangingLog2026-10-08;13_07_46.368.txt  ->  hour=13, minute=07
FILENAME_RE = re.compile(r"rangingLog\d{4}-\d{2}-\d{2};(\d{2})_(\d{2})_")

# [2026-10-08;13:06:45.677],DistMeasurement:Technology:00;Distance:4.82;
# finditer on the whole text, so lines that got glued together still work
DIST_RE = re.compile(
    r"\[[^\]]*\],DistMeasurement:Technology:(\d+);Distance:([-+0-9.eE]+);"
)

# 0.255,12:32,
REF_RE = re.compile(r"^\s*([-+0-9.]+)\s*,\s*(\d{1,2}):(\d{2})")


def read_text(path: Path) -> str:
    for enc in ("utf-8-sig", "cp1252", "latin-1"):
        try:
            return path.read_text(encoding=enc)
        except UnicodeDecodeError:
            continue
    raise RuntimeError(f"Could not decode {path}")


def load_reference(folder: Path) -> dict[str, float]:
    """Returns {'HH:MM': true_distance}."""
    candidates = sorted(folder.glob("Zuordnung*.txt"))
    if not candidates:
        sys.exit(f"No reference file (Zuordnung*.txt) found in {folder}")
    ref = {}
    for line in read_text(candidates[0]).splitlines():
        m = REF_RE.match(line)
        if m:
            dist, hh, mm = m.groups()
            ref[f"{int(hh):02d}:{mm}"] = float(dist)
    return ref


def parse_log(path: Path) -> dict[str, list[float]]:
    """Returns {'00': [distances...], '01': [...], ...}."""
    result = defaultdict(list)
    for tech, dist in DIST_RE.findall(read_text(path)):
        result[tech].append(float(dist))
    return result

def median(values):
    return sorted(values)[(int)(len(values)/2)]

def plot_counts(data):
    """Grouped bar plot: number of measurements per correct distance and technology."""
    techs = sorted(data)
    distances = sorted({d for t in techs for d, _ in data[t]})
 
    counts = {t: {d: 0 for d in distances} for t in techs}
    for t in techs:
        for d, values in data[t]:
            counts[t][d] += len(values)
 
    width = 0.8 / len(techs)
    fig, ax = plt.subplots(figsize=(11, 6))
    for i, t in enumerate(techs):
        shift = (i - (len(techs) - 1) / 2) * width
        positions = [j + shift for j in range(len(distances))]
        bars = ax.bar(
            positions, [counts[t][d] for d in distances], width, label=f"Technology {t}"
        )
        ax.bar_label(bars, fontsize=7, padding=2)
 
    ax.set_xticks(range(len(distances)))
    ax.set_xticklabels([f"{d:g}" for d in distances])
    ax.set_title("Number of measurements per distance and technology")
    ax.set_xlabel("Correct distance (m)")
    ax.set_ylabel("Number of measurements")
    ax.grid(True, axis="y", alpha=0.3)
    ax.set_axisbelow(True)
    ax.legend()
    fig.tight_layout()
 
    if SAVE_PNG:
        out = FOLDER / "plot_measurement_counts.png"
        fig.savefig(out, dpi=150)
        print(f"Saved {out}")

def main():
    ref = load_reference(FOLDER)

    # data[tech] = list of (true_distance, [measurements])
    data = defaultdict(list)

    log_files = sorted(FOLDER.glob("rangingLog*.txt"))
    if not log_files:
        sys.exit(f"No rangingLog*.txt files found in {FOLDER}")

    for f in log_files:
        m = FILENAME_RE.search(f.name)
        if not m:
            print(f"Skipping (unexpected file name): {f.name}")
            continue
        key = f"{m.group(1)}:{m.group(2)}"
        if key not in ref:
            print(f"Skipping {f.name}: no reference distance for {key}")
            continue

        true_dist = ref[key]
        for tech, values in parse_log(f).items():
            data[tech].append((true_dist, values))
        print(f"{f.name}: true distance {true_dist} m")

    if not data:
        sys.exit("No measurements found.")

    for tech in sorted(data):
        entries = sorted(data[tech], key=lambda e: e[0])

        xs, ys = [], []
        mean_x, mean_y = [], []
        median_x, median_y = [], []
        for true_dist, values in entries:
            xs += [true_dist] * len(values)
            ys += values
            if values:
                mean_x.append(true_dist)
                mean_y.append(sum(values) / len(values))
                median_x.append(true_dist)
                median_y.append(median(values))

        fig, ax = plt.subplots(figsize=(9, 6))
        ax.scatter(xs, ys, s=10, alpha=0.35, label="Measurements")
        ax.plot(mean_x, mean_y, "o-", color="tab:red", label="Mean per distance")
        ax.plot(median_x, median_y, "o-", color="tab:green", label="Median per distance")

        lo = min(0, min(xs), min(ys))
        hi = max(max(xs), max(ys))
        ax.plot([lo, hi], [lo, hi], "k--", linewidth=1, label="Ideal (measured = true)")

        ax.set_title(f"Technology {tech}")
        ax.set_xlabel("Correct distance (m)")
        ax.set_ylabel("Measured distance (m)")
        ax.grid(True, alpha=0.3)
        ax.legend()
        fig.tight_layout()

        if SAVE_PNG:
            out = FOLDER / f"plot_technology_{tech}.png"
            fig.savefig(out, dpi=150)
            print(f"Saved {out}")


    plot_counts(data)
    plt.show()


if __name__ == "__main__":
    main()