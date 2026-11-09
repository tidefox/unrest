#!/usr/bin/env python3
"""Monte Carlo balance check for the Unrest sleep meter.

Models one night per run, starting at the earliest moment players can sleep,
with S of N counted players getting into bed immediately and staying there
(except when a Nightmare wakes one of them). The sleepers roll one shared
dream every 8-22 seconds (3-7 on a Fever Dream), with momentum, the rubber
band, double or nothing and a random moon phase. Nobody keeps watch, so no
Reaper, and nobody has any Luck unless --luck is given. Prints median and 10th-90th percentile
time-to-skip plus the share of nights that end without a skip.

Usage: python3 tools/simulate.py [--runs 4000] [--seed 1] [--luck 0]
"""
import argparse
import random
import statistics

NIGHT_SECONDS = 546          # ticks 12541..23458 at 20 ticks/s
ROLL_MIN, ROLL_MAX = 8, 22   # seconds between shared dream rolls (random)
FEVER_ROLL_MIN, FEVER_ROLL_MAX = 3, 7
FIRST_ROLL = 2               # the first roll comes 2s after falling asleep
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

# Momentum (DESIGN.md §4.3)
TENSION_MAX = 5.0
TENSION_PAYOUT = 0.05        # meter per point of tension a Deep roll releases
CHAIN_MAX_WEIGHT = 4.0       # Deep chains count 2x, 3x, 4x
NIGHT_TERROR_STREAK = 3      # Restless rolls in a row that become a Night terror
RUBBER_BAND = 1.0            # Luck added while riding the 2-minute line
GAMBLE_AT, GAMBLE_COUNTDOWN = 0.5, 5
GAMBLE_WIN, GAMBLE_LOSE = 0.75, 0.25

# Moon (DESIGN.md §3.3): chaos by phase, +1 full moon to -1 new moon
MOON_CHAOS = [1.0, 0.5, 0.0, -0.5, -1.0, -0.5, 0.0, 0.5]
MOON_DEEP, MOON_BAD = 0.05, 0.25

FEVER_INTENSITY = 3.0
FEVER_FULL_MOON_BONUS = 2.0

NIGHT_TYPES = {
    # name: (base weight, weight per debt, minimum weight, meter multiplier)
    "calm":     (25, -4, 5, 1.3),
    "normal":   (40, -3, 20, 1.0),
    "restless": (22, 4, 0, 0.7),
    "haunted":  (8, 3, 0, 0.6),
    "fever":    (5, 0, 0, 1.0),
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


def roll_night(debt, chaos):
    names = list(NIGHT_TYPES)
    weights = [max(lo, base + per * debt) for base, per, lo, _ in NIGHT_TYPES.values()]
    if chaos >= 1.0:
        weights[names.index("fever")] *= FEVER_FULL_MOON_BONUS
    name = random.choices(names, weights=weights)[0]
    return name, NIGHT_TYPES[name][3]


def bands(r, debt, luck, chaos):
    """(nightmare top, restless top, deep from) on 0..1, like SleepMath.bands."""
    bad = max(0.0, 1 - LUCK_BAD * luck) * max(0.0, 1 + MOON_BAD * chaos)
    deep = max(0.05, 0.10 + 0.20 * r - 0.02 * debt + LUCK_DEEP * luck + MOON_DEEP * chaos)
    restless = max(0.0, (0.30 - 0.20 * r + 0.03 * debt) * bad)
    nightmare = max(0.0, 0.06 * (1 - r) * (1 + 0.3 * debt) * bad)
    n = min(1.0, nightmare)
    rs = min(1.0 - n, restless)
    d = min(1.0 - n - rs, deep)
    return n, n + rs, 1.0 - d


def rubber_luck(meter, slept):
    if RUBBER_BAND <= 0:
        return 0.0
    k = (0.25 - (meter - floor(slept))) / 0.25
    return RUBBER_BAND * max(-1.0, min(1.0, k))


def run_night(sleepers, players, debt, luck=0.0):
    """Seconds to the skip, or None if the night ends on its own."""
    r = sleepers / players
    if sleepers == players:
        return 5
    chaos = random.choice(MOON_CHAOS)
    night, mult = roll_night(debt, chaos)
    fever = night == "fever"
    if night == "haunted" and r < HAUNTED_MIN_RATIO:
        mult *= HAUNTED_BELOW_RATIO_MULTIPLIER
    meter, t = 0.0, 0
    out_until = 0                # one sleeper is out after a Nightmare
    next_roll = FIRST_ROLL
    weight = 1.0
    tension, chain, restless_streak, force_deep = 0.0, 0, 0, False
    ramp_bonus = 0.0
    gamble_done, countdown = False, -1
    while t < NIGHT_SECONDS:
        next_roll -= 1
        if next_roll <= 0:
            lo, hi = (FEVER_ROLL_MIN, FEVER_ROLL_MAX) if fever else (ROLL_MIN, ROLL_MAX)
            next_roll = random.randint(lo, hi)
            nb, rb, db = bands(r, debt, luck + rubber_luck(meter, t), chaos)
            x = random.random()
            dream = "nightmare" if x < nb else "restless" if x < rb else "deep" if x >= db else "normal"
            if force_deep:
                dream, force_deep = "deep", False
            if dream == "restless":
                restless_streak += 1
                if NIGHT_TERROR_STREAK and restless_streak >= NIGHT_TERROR_STREAK:
                    dream, restless_streak, force_deep = "nightmare", 0, True
            else:
                restless_streak = 0
            if dream in ("restless", "nightmare"):
                tension = min(TENSION_MAX, tension + (2 if fever else 1))
                chain = 0
            elif dream == "deep":
                chain += 1
                meter = min(1.0, meter + tension * TENSION_PAYOUT)
                tension = 0.0
            else:
                chain = 0
            if dream == "nightmare":
                meter = max(0.0, meter - NIGHTMARE_METER_LOSS)
                out_until = t + REBED_SECONDS
                w = 1.0
            elif dream == "restless":
                w = RESTLESS_WEIGHT
            elif dream == "deep":
                w = min(max(DEEP_WEIGHT, CHAIN_MAX_WEIGHT), DEEP_WEIGHT + chain - 1)
            else:
                w = 1.0
            if fever:
                w = max(0.0, min(6.0, 1 + FEVER_INTENSITY * (w - 1)))
            weight = w
        asleep = sleepers - (1 if t < out_until else 0)
        r_eff = min(1.0, weight * asleep / players)
        if r_eff > 0 and mult > 0:
            meter += mult / fill_time(r_eff) * ramp(t + ramp_bonus)
        t += 1
        meter = min(1.0, max(meter, floor(t)))
        # Double or nothing, once a night.
        if countdown > 0:
            countdown -= 1
        elif countdown == 0:
            countdown = -1
            win = random.random() < max(0.25, min(0.75, 0.5 + 0.05 * luck))
            if win:
                meter = max(meter, GAMBLE_WIN)
            else:
                ramp_bonus += RAMP_DOUBLE_SECONDS
                meter = max(floor(t), min(meter, GAMBLE_LOSE))
        elif not gamble_done and GAMBLE_AT <= meter < 1:
            gamble_done = True
            countdown = GAMBLE_COUNTDOWN - 1
        if meter >= 1:
            return t
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
