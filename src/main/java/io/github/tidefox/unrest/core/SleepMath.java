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

    /** Seconds left until the meter is full at the current rate, or infinity if it isn't filling. */
    public static double secondsToFull(double meter, double rEff, double nightMultiplier, double presetScale) {
        double step = meterStep(rEff, nightMultiplier, presetScale);
        if (step <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0.0, 1.0 - meter) / step;
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

    public static double nightmareChance(double r, double debt) {
        return Math.max(0.0, UnrestConfig.nightmareBase * (1.0 - r) * (1.0 + UnrestConfig.nightmarePerDebt * debt));
    }

    public static double restlessChance(double r, double debt) {
        return Math.max(0.0, UnrestConfig.restlessBase + UnrestConfig.restlessPerRatio * r + UnrestConfig.restlessPerDebt * debt);
    }

    public static double deepChance(double r, double debt) {
        return Math.max(UnrestConfig.deepMin, UnrestConfig.deepBase + UnrestConfig.deepPerRatio * r + UnrestConfig.deepPerDebt * debt);
    }

    /** One dream roll for headcount ratio {@code r} and Unrest {@code debt} (§4). */
    public static Dream rollDream(double r, double debt, Random rand) {
        double x = rand.nextDouble();
        double nightmare = nightmareChance(r, debt);
        if (x < nightmare) {
            return Dream.NIGHTMARE;
        }
        double restless = restlessChance(r, debt);
        if (x < nightmare + restless) {
            return Dream.RESTLESS;
        }
        if (x < nightmare + restless + deepChance(r, debt)) {
            return Dream.DEEP;
        }
        return Dream.NORMAL;
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
