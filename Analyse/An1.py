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

DIST2_RE = re.compile(
    r"\[\d{4}-\d{2}-\d{2};(\d{2}):(\d{2}):([-+0-9.eE]+)\],DistMeasurement:Technology:(\d+);Distance:([-+0-9.eE]+);"
)

names = ["UWB","BLE","WIFI"]
colors=["green","blue","red"]

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


def parse_log(path: Path) -> dict[str, list[(float,float)]]:
    """Returns {'00': [distances...], '01': [...], ...}."""
    result = defaultdict(list)
    offsetTime=0
    for h,m,s,tech, dist in DIST2_RE.findall(read_text(path)):
        timeS = int(h)*24*60 + int(m)*60 + float(s)
        if(offsetTime==0):
            offsetTime=timeS
        timeS-=offsetTime
        result[tech].append((timeS,float(dist)))
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
            positions, [counts[t][d] for d in distances], width, label=f"Technology {names[int(t)]}",color=colors[int(t)]
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

def meansAndPointsForDistance(data):
    figC, axC = plt.subplots(figsize=(9, 6))
    loC=0
    hiC=0
    for tech in sorted(data):
        entries = sorted(data[tech], key=lambda e: e[0])

        xs, ys = [], []
        mean_x, mean_y = [], []
        median_x, median_y = [], []
        for true_dist, values in entries:
            newLists = list(map(list, zip(*values)))

            xs += [true_dist] * len(newLists[1])
            ys += newLists[1]
            if newLists[1]:
                mean_x.append(true_dist)
                mean_y.append(sum(newLists[1]) / len(newLists[1]))
                median_x.append(true_dist)
                median_y.append(median(newLists[1]))

        fig, ax = plt.subplots(figsize=(9, 6))
        ax.scatter(xs, ys, s=10, alpha=0.35, label="Measurements")
        ax.plot(mean_x, mean_y, "o-", color="tab:red", label="Mean per distance")
        axC.plot(mean_x, mean_y, "o-", color=colors[int(tech)], label=f"Mean per distance for {names[int(tech)]}")
        ax.plot(median_x, median_y, "o-", color="tab:green", label="Median per distance")

        lo = min(0, min(xs), min(ys))
        hi = max(max(xs), max(ys))
        loC = min(loC,lo)
        hiC = max(hiC,hi)
        ax.plot([lo, hi], [lo, hi], "k--", linewidth=1, label="Ideal (measured = true)")

        ax.set_title(f"Technology {names[int(tech)]}")
        ax.set_xlabel("Correct distance (m)")
        ax.set_ylabel("Measured distance (m)")
        ax.grid(True, alpha=0.3)
        ax.legend()
        fig.tight_layout()

        if SAVE_PNG:
            out = FOLDER / f"plot_technology_{tech}.png"
            fig.savefig(out, dpi=150)
            print(f"Saved {out}")

    axC.plot([loC, hiC], [loC, hiC], "k--", linewidth=1, label="Ideal (measured = true)")
    
    axC.set_title(f"Technologys in comparison")
    axC.set_xlabel("Correct distance (m)")
    axC.set_ylabel("Measured distance (m)")
    axC.grid(True, alpha=0.3)
    axC.legend()
    figC.tight_layout()

    if SAVE_PNG:
        out = FOLDER / f"plot_technologys.png"
        figC.savefig(out, dpi=150)
        print(f"Saved {out}")

def valuesForTime(data):
    newDataX=defaultdict()
    newDataY=defaultdict()
    techs=[]
    dists=[]
    for tech in data:
        techs.append(tech)
        for dist,v2 in data[tech]:
            d= "{:.4f}".format(dist)
            
            if not dists.__contains__(d):
                dists.append(d)
            if not newDataX.__contains__(d):
                newDataX[d] = defaultdict()
                newDataY[d] = defaultdict()
            v3 = list(map(list,zip(*v2)))
            newDataX[d][tech]=v3[0]
            newDataY[d][tech]=v3[1]


    for d in dists:
        fig, ax = plt.subplots(figsize=(9, 6))

        for t in techs:
            ax.plot(newDataX[d][t], newDataY[d][t], "o-", color=colors[int(t)], label=f"Distance from {names[int(t)]}")
        ax.set_title(f"Distance {d}m")
        ax.set_xlabel("Time (s)")
        ax.set_ylabel("Measured distance (m)")
        ax.grid(True, alpha=0.3)
        ax.legend()
        fig.tight_layout()

        if SAVE_PNG:
            out = FOLDER / f"plot_time_{d}.png"
            fig.savefig(out, dpi=150)
            print(f"Saved {out}")
    return



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
    
    meansAndPointsForDistance(data)

    plot_counts(data)

    valuesForTime(data)


    plt.show()


if __name__ == "__main__":
    main()