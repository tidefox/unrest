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

    public static NightType rollNight(double debt, Random rand) {
        double[] w = nightWeights(debt);
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

    public static double nightmareChance(double r, double debt) {
        return nightmareChance(r, debt, 0);
    }

    public static double nightmareChance(double r, double debt, double luck) {
        return Math.max(0.0, UnrestConfig.nightmareBase * (1.0 - r) * (1.0 + UnrestConfig.nightmarePerDebt * debt)
                * badLuckFactor(luck));
    }

    public static double restlessChance(double r, double debt) {
        return restlessChance(r, debt, 0);
    }

    public static double restlessChance(double r, double debt, double luck) {
        return Math.max(0.0, (UnrestConfig.restlessBase + UnrestConfig.restlessPerRatio * r
                + UnrestConfig.restlessPerDebt * debt) * badLuckFactor(luck));
    }

    public static double deepChance(double r, double debt) {
        return deepChance(r, debt, 0);
    }

    public static double deepChance(double r, double debt, double luck) {
        return Math.max(UnrestConfig.deepMin, UnrestConfig.deepBase + UnrestConfig.deepPerRatio * r
                + UnrestConfig.deepPerDebt * debt + UnrestConfig.luckDeep * luck);
    }

    public static Dream rollDream(double r, double debt, Random rand) {
        return rollDream(r, debt, 0, rand);
    }

    /** The shared dream roll for ratio {@code r}, Unrest {@code debt} and combined Luck {@code luck} (§4). */
    public static Dream rollDream(double r, double debt, double luck, Random rand) {
        double x = rand.nextDouble();
        double nightmare = nightmareChance(r, debt, luck);
        if (x < nightmare) {
            return Dream.NIGHTMARE;
        }
        double restless = restlessChance(r, debt, luck);
        if (x < nightmare + restless) {
            return Dream.RESTLESS;
        }
        if (x < nightmare + restless + deepChance(r, debt, luck)) {
            return Dream.DEEP;
        }
        return Dream.NORMAL;
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
