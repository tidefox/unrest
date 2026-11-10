package io.github.tidefox.unrest.core;

import io.github.tidefox.unrest.config.UnrestConfig;

import java.util.Random;

/**
 * The formulas from DESIGN.md §2–§5, free of Minecraft classes so they can be
 * unit tested and kept in step with {@code tools/simulate.py}.
 */
public final class SleepMath {

    private SleepMath() {}

    /** Fill time T(r) in seconds before the preset scale (§2.1). Infinite for r ≤ 0. */
    public static double fillSeconds(double r) {
        if (r <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        double t = UnrestConfig.baseSeconds + UnrestConfig.scaleSeconds * (1.0 - r) / r;
        return Math.min(t, UnrestConfig.capSeconds);
    }

    /** How much the meter rises in one second (§2.2). */
    public static double meterStep(double rEff, double nightMultiplier, double presetScale) {
        if (rEff <= 0) {
            return 0.0;
        }
        return nightMultiplier / (fillSeconds(rEff) * presetScale);
    }

    /** How much faster the meter fills after someone has been asleep for {@code seconds} (§2.2). */
    public static double ramp(double seconds) {
        if (UnrestConfig.rampDoubleSeconds <= 0) {
            return 1.0;
        }
        double r = Math.pow(2.0, Math.max(0.0, seconds) / UnrestConfig.rampDoubleSeconds);
        return Math.min(Math.max(1.0, UnrestConfig.rampMax), r);
    }

    /** Seconds left until the meter is full at the current rate, without the ramp. */
    public static double secondsToFull(double meter, double rEff, double nightMultiplier, double presetScale) {
        double step = meterStep(rEff, nightMultiplier, presetScale);
        if (step <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0.0, 1.0 - meter) / step;
    }

    /**
     * Seconds left until the meter is full at the current rate, with the ramp
     * carrying on from {@code rampSeconds}. Steps second by second, like the meter.
     */
    public static double secondsToFull(double meter, double rEff, double nightMultiplier, double presetScale,
                                       double rampSeconds) {
        double step = meterStep(rEff, nightMultiplier, presetScale);
        if (step <= 0 && UnrestConfig.maxSleepSeconds <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        double m = meter;
        for (int t = 0; t < 100000; t++) {
            if (m >= 1.0) {
                return t;
            }
            m += step * ramp(rampSeconds + t);
            m = Math.max(m, meterFloor(rampSeconds + t + 1));
        }
        return Double.POSITIVE_INFINITY;
    }

    /** Night multiplier, slowed further on a Haunted night below the required ratio (§3.1). */
    public static double nightFactor(NightType type, double r) {
        double m = type.multiplier();
        if (type == NightType.HAUNTED && r < UnrestConfig.hauntedMinRatio) {
            m *= UnrestConfig.hauntedBelowRatioMultiplier;
        }
        return m;
    }

    /** How much of a watch deed worth {@code amount} a player may still add, given what they've added already (§4.2). */
    public static double watchCredit(double earned, double amount) {
        double room = Math.max(0.0, UnrestConfig.watchMaxPerPlayer - earned);
        return Math.max(0.0, Math.min(room, amount));
    }

    /** Roll weights of the night types for Unrest {@code debt} (§3.1), indexed by ordinal. */
    public static double[] nightWeights(double debt) {
        NightType[] types = NightType.values();
        double[] w = new double[types.length];
        for (int i = 0; i < types.length; i++) {
            w[i] = Math.max(UnrestConfig.nightMin[i], UnrestConfig.nightWeight[i] + UnrestConfig.nightPerDebt[i] * debt);
        }
        return w;
    }

    /** Night weights for Unrest {@code debt} under moon phase {@code moonPhase}: a full moon makes Fever Dreams likelier. */
    public static double[] nightWeights(double debt, int moonPhase) {
        double[] w = nightWeights(debt);
        if (Moon.chaos(moonPhase) >= 1.0) {
            w[NightType.FEVER.ordinal()] *= UnrestConfig.feverFullMoonBonus;
        }
        return w;
    }

    public static NightType rollNight(double debt, Random rand) {
        return rollNight(debt, 2, rand);
    }

    public static NightType rollNight(double debt, int moonPhase, Random rand) {
        double[] w = nightWeights(debt, moonPhase);
        double total = 0;
        for (double x : w) {
            total += x;
        }
        double pick = rand.nextDouble() * total;
        NightType[] types = NightType.values();
        for (int i = 0; i < types.length; i++) {
            pick -= w[i];
            if (pick < 0) {
                return types[i];
            }
        }
        return NightType.NORMAL;
    }

    /** The sleepers' combined Luck, clamped to ±luckMax (§4). */
    public static double groupLuck(double[] luck) {
        double sum = 0;
        for (double l : luck) {
            sum += l;
        }
        return Math.max(-UnrestConfig.luckMax, Math.min(UnrestConfig.luckMax, sum));
    }

    /** Scale on the bad rolls for combined Luck {@code luck}: below 1 when lucky, above 1 when unlucky. */
    public static double badLuckFactor(double luck) {
        return Math.max(0.0, 1.0 - UnrestConfig.luckBad * luck);
    }

    /** Scale on the bad rolls for moon chaos {@code chaos}: above 1 under a full moon, below 1 under a new one. */
    public static double moonBadFactor(double chaos) {
        return Math.max(0.0, 1.0 + UnrestConfig.moonBad * chaos);
    }

    public static double nightmareChance(double r, double debt) {
        return nightmareChance(r, debt, 0, 0);
    }

    public static double nightmareChance(double r, double debt, double luck) {
        return nightmareChance(r, debt, luck, 0);
    }

    public static double nightmareChance(double r, double debt, double luck, double chaos) {
        return Math.max(0.0, UnrestConfig.nightmareBase * (1.0 - r) * (1.0 + UnrestConfig.nightmarePerDebt * debt)
                * badLuckFactor(luck) * moonBadFactor(chaos));
    }

    public static double restlessChance(double r, double debt) {
        return restlessChance(r, debt, 0, 0);
    }

    public static double restlessChance(double r, double debt, double luck) {
        return restlessChance(r, debt, luck, 0);
    }

    public static double restlessChance(double r, double debt, double luck, double chaos) {
        return Math.max(0.0, (UnrestConfig.restlessBase + UnrestConfig.restlessPerRatio * r
                + UnrestConfig.restlessPerDebt * debt) * badLuckFactor(luck) * moonBadFactor(chaos));
    }

    public static double deepChance(double r, double debt) {
        return deepChance(r, debt, 0, 0);
    }

    public static double deepChance(double r, double debt, double luck) {
        return deepChance(r, debt, luck, 0);
    }

    public static double deepChance(double r, double debt, double luck, double chaos) {
        return Math.max(UnrestConfig.deepMin, UnrestConfig.deepBase + UnrestConfig.deepPerRatio * r
                + UnrestConfig.deepPerDebt * debt + UnrestConfig.luckDeep * luck + UnrestConfig.moonDeep * chaos);
    }

    /**
     * The roll's bands on a 0–100 scale, as {nightmareTop, restlessTop, deepFrom}:
     * a roll below nightmareTop is a Nightmare, below restlessTop Restless, at or
     * above deepFrom Deep, and Normal in between. Deep sits at the top so a high
     * roll is a good roll.
     */
    public static double[] bands(double r, double debt, double luck, double chaos) {
        double n = Math.min(1.0, nightmareChance(r, debt, luck, chaos));
        double rs = Math.min(1.0 - n, restlessChance(r, debt, luck, chaos));
        double d = Math.min(1.0 - n - rs, deepChance(r, debt, luck, chaos));
        return new double[] {n * 100, (n + rs) * 100, (1.0 - d) * 100};
    }

    /** The dream for a roll of {@code value} (0–100) against {@code bands}. */
    public static Dream dreamFor(double value, double[] bands) {
        if (value < bands[0]) {
            return Dream.NIGHTMARE;
        }
        if (value < bands[1]) {
            return Dream.RESTLESS;
        }
        if (value >= bands[2]) {
            return Dream.DEEP;
        }
        return Dream.NORMAL;
    }

    /** How close a roll came to the nearest band edge, in points (for "so close!"). */
    public static double nearMiss(double value, double[] bands) {
        double best = Double.POSITIVE_INFINITY;
        for (double b : bands) {
            if (b > 0 && b < 100) {
                best = Math.min(best, Math.abs(value - b));
            }
        }
        return best;
    }

    public static Dream rollDream(double r, double debt, Random rand) {
        return rollDream(r, debt, 0, rand);
    }

    /** The shared dream roll for ratio {@code r}, Unrest {@code debt} and combined Luck {@code luck} (§4). */
    public static Dream rollDream(double r, double debt, double luck, Random rand) {
        return dreamFor(rand.nextDouble() * 100, bands(r, debt, luck, 0));
    }

    /**
     * The rubber band (§4.3): Luck added while the meter barely leads the
     * 2-minute line, taken away when it leads by half the meter or more.
     */
    public static double rubberLuck(double meter, double sleptSeconds) {
        if (UnrestConfig.rubberBand <= 0 || UnrestConfig.maxSleepSeconds <= 0) {
            return 0.0;
        }
        double lead = meter - meterFloor(sleptSeconds);
        double k = (0.25 - lead) / 0.25;
        return UnrestConfig.rubberBand * Math.max(-1.0, Math.min(1.0, k));
    }

    /** Double or nothing: the chance to win with combined Luck {@code luck}, between 25% and 75%. */
    public static double gambleWinChance(double luck) {
        return Math.max(0.25, Math.min(0.75, 0.5 + UnrestConfig.gambleLuck * luck));
    }

    /** Seconds until the next shared roll: random in the configured range, shorter on a Fever Dream. */
    public static int rollInterval(boolean fever, Random rand) {
        int lo = fever ? UnrestConfig.feverRollMinSeconds : UnrestConfig.rollMinSeconds;
        int hi = fever ? UnrestConfig.feverRollMaxSeconds : UnrestConfig.rollMaxSeconds;
        lo = Math.max(1, lo);
        hi = Math.max(lo, hi);
        return lo + rand.nextInt(hi - lo + 1);
    }

    /** The lowest the meter can be after someone has slept {@code sleptSeconds} (§2.2): the 2-minute limit. */
    public static double meterFloor(double sleptSeconds) {
        if (UnrestConfig.maxSleepSeconds <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, sleptSeconds / UnrestConfig.maxSleepSeconds));
    }

    /** Food points a sleeper loses for a skip of {@code ticksSkipped} ticks (§5), before the floor. */
    public static int hungerLoss(long ticksSkipped, Random rand) {
        double hours = ticksSkipped / 1000.0;
        int spread = Math.max(0, UnrestConfig.randomSpread);
        int loss = (int) Math.round(hours / UnrestConfig.hoursPerPoint);
        if (spread > 0) {
            loss += rand.nextInt(2 * spread + 1) - spread;
        }
        return clamp(loss, 0, UnrestConfig.maxLoss);
    }

    /** The new food level after losing {@code loss} points, never going below the floor (§5). */
    public static int applyHunger(int food, int loss) {
        if (food <= UnrestConfig.floor) {
            return food;
        }
        return Math.max(food - loss, UnrestConfig.floor);
    }

    /** Unrest after a skip with headcount ratio {@code r} (§3.2). */
    public static double debtAfterSkip(double debt, double r, boolean wholeServer) {
        double next = wholeServer
                ? debt - UnrestConfig.wholeServerRelief
                : debt + UnrestConfig.partialSkipFactor * (1.0 - r);
        return clampDebt(next);
    }

    /** Unrest after a night that ended on its own (§3.2). */
    public static double debtAfterNaturalNight(double debt) {
        return clampDebt(debt - UnrestConfig.naturalNightRelief);
    }

    public static double clampDebt(double debt) {
        return Math.max(0.0, Math.min(UnrestConfig.debtMax, debt));
    }

    public static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Formats seconds as m:ss. */
    public static String formatDuration(double seconds) {
        long s = Math.max(0, Math.round(seconds));
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }
}
