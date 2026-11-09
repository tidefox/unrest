#!/usr/bin/env python3
"""Monte Carlo balance check for the Unrest sleep meter.

Models one night per run, starting at the earliest moment players can sleep,
with S of N counted players getting into bed immediately and staying there
(except when a Nightmare wakes them). Prints median and 10th-90th percentile
time-to-skip plus the share of nights that end without a skip.

Usage: python3 tools/simulate.py [--runs 4000] [--seed 1]
"""
import argparse
import random
import statistics

NIGHT_SECONDS = 546          # ticks 12541..23458 at 20 ticks/s
ROLL_INTERVAL = 15           # seconds between dream rolls
REBED_SECONDS = 15           # time a woken sleeper is out before counting again

BASE = 30.0                  # seconds
SCALE = 60.0                 # seconds
CAP = 520.0                  # seconds
NIGHTMARE_METER_LOSS = 0.05
RAMP_DOUBLE_SECONDS = 75.0   # fill speed doubles every this many seconds someone is asleep
RAMP_MAX = 8.0               # the ramp never speeds the meter up more than this

NIGHT_TYPES = {
    # name: (base weight, weight per debt, minimum weight, meter multiplier)
    "calm":     (25, -4, 5, 1.3),
    "normal":   (45, -3, 20, 1.0),
    "restless": (22, 4, 0, 0.7),
    "haunted":  (8, 3, 0, 0.6),
}
HAUNTED_MIN_RATIO = 0.5
HAUNTED_BELOW_RATIO_MULTIPLIER = 0.5   # extra slow-down below that ratio (0 = no fill)


def fill_time(r):
    return min(CAP, BASE + SCALE * (1 - r) / r)


def ramp(seconds):
    if RAMP_DOUBLE_SECONDS <= 0:
        return 1.0
    return min(RAMP_MAX, 2 ** (seconds / RAMP_DOUBLE_SECONDS))


def roll_night(debt):
    names = list(NIGHT_TYPES)
    weights = [max(lo, base + per * debt) for base, per, lo, _ in NIGHT_TYPES.values()]
    name = random.choices(names, weights=weights)[0]
    return name, NIGHT_TYPES[name][3]


def dream_chances(r, debt):
    deep = max(0.05, 0.10 + 0.20 * r - 0.02 * debt)
    restless = 0.30 - 0.20 * r + 0.03 * debt
    nightmare = 0.03 * (1 - r) * (1 + 0.3 * debt)
    return deep, restless, nightmare


def run_night(sleepers, players, debt):
    r = sleepers / players
    if sleepers == players:
        return 5
    night, mult = roll_night(debt)
    if night == "haunted" and r < HAUNTED_MIN_RATIO:
        if HAUNTED_BELOW_RATIO_MULTIPLIER <= 0:
            return None
        mult *= HAUNTED_BELOW_RATIO_MULTIPLIER
    deep, restless, nightmare = dream_chances(r, debt)
    meter, t = 0.0, 0
    out_until = [0] * sleepers
    while t < NIGHT_SECONDS:
        weight = 0.0
        for i in range(sleepers):
            if t < out_until[i]:
                continue
            x = random.random()
            if x < nightmare:
                meter = max(0.0, meter - NIGHTMARE_METER_LOSS)
                out_until[i] = t + REBED_SECONDS
            elif x < nightmare + restless:
                pass
            elif x < nightmare + restless + deep:
                weight += 2
            else:
                weight += 1
        r_eff = min(1.0, weight / players)
        rate = 0.0 if r_eff <= 0 else mult / fill_time(r_eff)
        for _ in range(ROLL_INTERVAL):
            meter += rate * ramp(t)
            t += 1
            if meter >= 1:
                return t
            if t >= NIGHT_SECONDS:
                break
    return None


def fmt(seconds):
    m, s = divmod(int(round(seconds)), 60)
    return f"{m}:{s:02d}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--runs", type=int, default=4000)
    ap.add_argument("--seed", type=int, default=1)
    args = ap.parse_args()
    random.seed(args.seed)
    cases = [(3, 4), (2, 3), (1, 2), (2, 4), (1, 3), (1, 4), (1, 8), (1, 12)]
    print("| Asleep | Unrest 0 | Unrest 2 | Unrest 5 |")
    print("|---|---|---|---|")
    for s, n in cases:
        cells = []
        for debt in (0, 2, 5):
            res = [run_night(s, n, debt) for _ in range(args.runs)]
            ok = [x for x in res if x is not None]
            miss = 100 * (1 - len(ok) / len(res))
            if len(ok) < 10:
                cells.append(f"no skip ({miss:.0f}%)")
                continue
            q = statistics.quantiles(ok, n=10)
            cell = f"{fmt(statistics.median(ok))} ({fmt(q[0])}–{fmt(q[8])})"
            if miss >= 1:
                cell += f", {miss:.0f}% no skip"
            cells.append(cell)
        print(f"| {s} of {n} | " + " | ".join(cells) + " |")


if __name__ == "__main__":
    main()
