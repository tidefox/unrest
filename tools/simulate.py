#!/usr/bin/env python3
"""Monte Carlo balance check for the Unrest sleep meter.

Models one night per run, starting at the earliest moment players can sleep,
with S of N counted players getting into bed immediately and staying there
(except when a Nightmare wakes one of them). The sleepers roll one shared
dream every 15 seconds; nobody keeps watch and nobody has any Luck. Prints median and 10th-90th percentile
time-to-skip plus the share of nights that end without a skip.

Usage: python3 tools/simulate.py [--runs 4000] [--seed 1] [--luck 0]
"""
import argparse
import random
import statistics

NIGHT_SECONDS = 546          # ticks 12541..23458 at 20 ticks/s
ROLL_INTERVAL = 15           # seconds between shared dream rolls
REBED_SECONDS = 15           # time a woken sleeper is out before counting again

BASE = 20.0                  # seconds
SCALE = 40.0                 # seconds
CAP = 240.0                  # seconds
NIGHTMARE_METER_LOSS = 0.10
RAMP_DOUBLE_SECONDS = 40.0   # fill speed doubles every this many seconds someone is asleep
RAMP_MAX = 8.0               # the ramp never speeds the meter up more than this
MAX_SLEEP_SECONDS = 120.0    # the meter never lags behind this share of the time asleep

DEEP_WEIGHT = 2.0            # each sleeper counts this much after a Deep roll
RESTLESS_WEIGHT = 0.5        # ... and this much after a Restless roll
LUCK_DEEP = 0.04             # Deep chance per point of combined Luck
LUCK_BAD = 0.15              # share of the bad chances removed per point of Luck

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


def floor(seconds):
    if MAX_SLEEP_SECONDS <= 0:
        return 0.0
    return min(1.0, seconds / MAX_SLEEP_SECONDS)


def roll_night(debt):
    names = list(NIGHT_TYPES)
    weights = [max(lo, base + per * debt) for base, per, lo, _ in NIGHT_TYPES.values()]
    name = random.choices(names, weights=weights)[0]
    return name, NIGHT_TYPES[name][3]


def dream_chances(r, debt, luck=0.0):
    bad = max(0.0, 1 - LUCK_BAD * luck)
    deep = max(0.05, 0.10 + 0.20 * r - 0.02 * debt + LUCK_DEEP * luck)
    restless = (0.30 - 0.20 * r + 0.03 * debt) * bad
    nightmare = 0.06 * (1 - r) * (1 + 0.3 * debt) * bad
    return deep, restless, nightmare


def run_night(sleepers, players, debt, luck=0.0):
    """Seconds to the skip, or None if the night ends on its own."""
    r = sleepers / players
    if sleepers == players:
        return 5
    night, mult = roll_night(debt)
    if night == "haunted" and r < HAUNTED_MIN_RATIO:
        mult *= HAUNTED_BELOW_RATIO_MULTIPLIER
    deep, restless, nightmare = dream_chances(r, debt, luck)
    meter, t = 0.0, 0
    out_until = 0                # one sleeper is out after a Nightmare
    while t < NIGHT_SECONDS:
        # One shared roll for every sleeper.
        x = random.random()
        if x < nightmare:
            meter = max(0.0, meter - NIGHTMARE_METER_LOSS)
            out_until = t + REBED_SECONDS
            weight = 1.0
        elif x < nightmare + restless:
            weight = RESTLESS_WEIGHT
        elif x < nightmare + restless + deep:
            weight = DEEP_WEIGHT
        else:
            weight = 1.0
        for _ in range(ROLL_INTERVAL):
            asleep = sleepers - (1 if t < out_until else 0)
            r_eff = min(1.0, weight * asleep / players)
            if r_eff > 0 and mult > 0:
                meter += mult / fill_time(r_eff) * ramp(t)
            t += 1
            meter = max(meter, floor(t))
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
    ap.add_argument("--luck", type=float, default=0.0, help="the sleepers' combined Luck (-5 to 5)")
    args = ap.parse_args()
    random.seed(args.seed)
    cases = [(3, 4), (2, 3), (1, 2), (2, 4), (1, 3), (1, 4), (1, 8), (1, 12)]
    print("| Asleep | Unrest 0 | Unrest 2 | Unrest 5 |")
    print("|---|---|---|---|")
    for s, n in cases:
        cells = []
        for debt in (0, 2, 5):
            res = [run_night(s, n, debt, args.luck) for _ in range(args.runs)]
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
