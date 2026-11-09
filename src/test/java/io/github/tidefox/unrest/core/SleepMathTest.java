package io.github.tidefox.unrest.core;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Checks the formulas against the numbers in DESIGN.md. Uses the default config values. */
public class SleepMathTest {

    private static final double EPS = 1e-9;

    @Test
    public void fillTimeMatchesDesignTable() {
        assertEquals(20.0 + 40.0 / 3, SleepMath.fillSeconds(3.0 / 4), EPS);
        assertEquals(40.0, SleepMath.fillSeconds(2.0 / 3), EPS);
        assertEquals(60.0, SleepMath.fillSeconds(1.0 / 2), EPS);
        assertEquals(100.0, SleepMath.fillSeconds(1.0 / 3), EPS);
        assertEquals(140.0, SleepMath.fillSeconds(1.0 / 4), EPS);
        assertEquals(240.0, SleepMath.fillSeconds(1.0 / 8), EPS);
        assertEquals(240.0, SleepMath.fillSeconds(1.0 / 20), EPS);
        assertTrue(Double.isInfinite(SleepMath.fillSeconds(0)));
    }

    @Test
    public void meterStepUsesMultiplierAndPreset() {
        assertEquals(1.0 / 60, SleepMath.meterStep(0.5, 1.0, 1.0), EPS);
        assertEquals(0.7 / 60, SleepMath.meterStep(0.5, 0.7, 1.0), EPS);
        assertEquals(1.0 / 30, SleepMath.meterStep(0.5, 1.0, 0.5), EPS);
        assertEquals(0.0, SleepMath.meterStep(0, 1.0, 1.0), EPS);
        assertEquals(30.0, SleepMath.secondsToFull(0.5, 0.5, 1.0, 1.0), 1e-6);
    }

    @Test
    public void rampDoublesAndIsCapped() {
        assertEquals(1.0, SleepMath.ramp(0), EPS);
        assertEquals(2.0, SleepMath.ramp(40), EPS);
        assertEquals(4.0, SleepMath.ramp(80), EPS);
        assertEquals(8.0, SleepMath.ramp(10000), EPS);
    }

    @Test
    public void secondsToFullWithRampIsShorter() {
        // 1/60 a second from half full is 30s flat; the ramp shortens it.
        double flat = SleepMath.secondsToFull(0.5, 0.5, 1.0, 1.0);
        double ramped = SleepMath.secondsToFull(0.5, 0.5, 1.0, 1.0, 0);
        assertTrue(ramped < flat);
        assertTrue(ramped > 15);
        // At the ramp cap it is 8 times as fast (with the time limit off, which would end it at once).
        double limit = io.github.tidefox.unrest.config.UnrestConfig.maxSleepSeconds;
        io.github.tidefox.unrest.config.UnrestConfig.maxSleepSeconds = 0;
        try {
            assertEquals(Math.ceil(30.0 / 8), SleepMath.secondsToFull(0.5, 0.5, 1.0, 1.0, 10000), 1e-6);
            assertTrue(Double.isInfinite(SleepMath.secondsToFull(0.5, 0, 1.0, 1.0, 0)));
        } finally {
            io.github.tidefox.unrest.config.UnrestConfig.maxSleepSeconds = limit;
        }
    }

    @Test
    public void twoMinuteLimit() {
        assertEquals(0.0, SleepMath.meterFloor(0), EPS);
        assertEquals(0.5, SleepMath.meterFloor(60), EPS);
        assertEquals(1.0, SleepMath.meterFloor(500), EPS);
        // Not filling at all: the limit still brings dawn 2 minutes after the first sleeper.
        assertEquals(120.0, SleepMath.secondsToFull(0, 0, 1.0, 1.0, 0), 1e-6);
        assertEquals(60.0, SleepMath.secondsToFull(0, 0, 1.0, 1.0, 60), 1e-6);
        // A lone sleeper on a big server never waits past it.
        assertTrue(SleepMath.secondsToFull(0, 1.0 / 20, 0.3, 1.0, 0) <= 120.0);
    }

    @Test
    public void hauntedBelowRatioIsSlowerNotBlocked() {
        assertEquals(0.6, SleepMath.nightFactor(NightType.HAUNTED, 0.5), EPS);
        assertEquals(0.3, SleepMath.nightFactor(NightType.HAUNTED, 0.25), EPS);
        assertEquals(0.7, SleepMath.nightFactor(NightType.RESTLESS, 0.25), EPS);
    }

    @Test
    public void nightWeightsAddUpTo100() {
        for (double d = 0; d <= 5.0; d += 0.25) {
            double sum = 0;
            for (double w : SleepMath.nightWeights(d)) {
                sum += w;
            }
            assertEquals("debt " + d, 100.0, sum, 1e-9);
        }
        double[] w0 = SleepMath.nightWeights(0);
        assertEquals(25, w0[0], EPS);
        assertEquals(40, w0[1], EPS);
        assertEquals(22, w0[2], EPS);
        assertEquals(8, w0[3], EPS);
        assertEquals(5, w0[4], EPS);
        double[] w5 = SleepMath.nightWeights(5);
        assertEquals(5, w5[0], EPS);
        assertEquals(25, w5[1], EPS);
        assertEquals(42, w5[2], EPS);
        assertEquals(23, w5[3], EPS);
        assertEquals(5, w5[4], EPS);
        // A full moon doubles the Fever Dream weight.
        assertEquals(10, SleepMath.nightWeights(0, 0)[4], EPS);
        assertEquals(5, SleepMath.nightWeights(0, 4)[4], EPS);
    }

    @Test
    public void nightRollFollowsWeights() {
        Random rand = new Random(1);
        int[] counts = new int[NightType.values().length];
        int runs = 200000;
        for (int i = 0; i < runs; i++) {
            counts[SleepMath.rollNight(0, rand).ordinal()]++;
        }
        assertEquals(0.25, counts[0] / (double) runs, 0.01);
        assertEquals(0.40, counts[1] / (double) runs, 0.01);
        assertEquals(0.22, counts[2] / (double) runs, 0.01);
        assertEquals(0.08, counts[3] / (double) runs, 0.01);
        assertEquals(0.05, counts[4] / (double) runs, 0.01);
    }

    @Test
    public void dreamChances() {
        assertEquals(0.03, SleepMath.nightmareChance(0.5, 0), EPS);
        assertEquals(0.20, SleepMath.restlessChance(0.5, 0), EPS);
        assertEquals(0.20, SleepMath.deepChance(0.5, 0), EPS);
        assertEquals(0.05, SleepMath.deepChance(0, 5), EPS);
        assertEquals(0.0, SleepMath.nightmareChance(1.0, 5), EPS);
    }

    @Test
    public void dreamRollFrequencies() {
        Random rand = new Random(2);
        int[] counts = new int[Dream.values().length];
        int runs = 200000;
        for (int i = 0; i < runs; i++) {
            counts[SleepMath.rollDream(0.5, 0, rand).ordinal()]++;
        }
        assertEquals(0.03, counts[Dream.NIGHTMARE.ordinal()] / (double) runs, 0.003);
        assertEquals(0.20, counts[Dream.RESTLESS.ordinal()] / (double) runs, 0.005);
        assertEquals(0.20, counts[Dream.DEEP.ordinal()] / (double) runs, 0.005);
        assertEquals(0.57, counts[Dream.NORMAL.ordinal()] / (double) runs, 0.005);
    }

    @Test
    public void luckShiftsTheSharedRoll() {
        // +2 Luck: Deep +8 points, Restless and Nightmare 30% less likely.
        assertEquals(0.28, SleepMath.deepChance(0.5, 0, 2), EPS);
        assertEquals(0.14, SleepMath.restlessChance(0.5, 0, 2), EPS);
        assertEquals(0.021, SleepMath.nightmareChance(0.5, 0, 2), EPS);
        // -2 Luck (Unluck): the bad rolls are 30% more likely.
        assertEquals(0.26, SleepMath.restlessChance(0.5, 0, -2), EPS);
        assertEquals(0.12, SleepMath.deepChance(0.5, 0, -2), EPS);
        // Combined Luck is summed and clamped.
        assertEquals(3.0, SleepMath.groupLuck(new double[] {1, 1, 1}), EPS);
        assertEquals(5.0, SleepMath.groupLuck(new double[] {4, 4}), EPS);
        assertEquals(-5.0, SleepMath.groupLuck(new double[] {-9}), EPS);
        assertEquals(0.0, SleepMath.badLuckFactor(10), EPS);
    }

    @Test
    public void moonPhaseMakesDreamsWildOrCalm() {
        assertEquals(1.0, Moon.chaos(0), EPS);
        assertEquals(-1.0, Moon.chaos(4), EPS);
        assertEquals(0.0, Moon.chaos(2), EPS);
        assertEquals(1.0, Moon.chaos(8), EPS);
        assertEquals("Full Moon", Moon.name(0));
        assertEquals("wild", Moon.mood(0));
        assertEquals("calm", Moon.mood(4));
        // Full moon: +5 points of Deep, bad rolls 25% likelier. New moon: the reverse.
        assertEquals(0.25, SleepMath.deepChance(0.5, 0, 0, 1), EPS);
        assertEquals(0.25, SleepMath.restlessChance(0.5, 0, 0, 1), EPS);
        assertEquals(0.15, SleepMath.restlessChance(0.5, 0, 0, -1), EPS);
    }

    @Test
    public void bandsPutDeepOnTop() {
        double[] b = SleepMath.bands(0.5, 0, 0, 0);
        assertEquals(3.0, b[0], 1e-6);
        assertEquals(23.0, b[1], 1e-6);
        assertEquals(80.0, b[2], 1e-6);
        assertEquals(Dream.NIGHTMARE, SleepMath.dreamFor(2.9, b));
        assertEquals(Dream.RESTLESS, SleepMath.dreamFor(3.0, b));
        assertEquals(Dream.NORMAL, SleepMath.dreamFor(50, b));
        assertEquals(Dream.DEEP, SleepMath.dreamFor(80, b));
        assertEquals(1.0, SleepMath.nearMiss(79, b), 1e-6);
    }

    @Test
    public void rubberBandHelpsWhenBehindAndHurtsWhenFarAhead() {
        // Riding the 2-minute line: +2 Luck.
        assertEquals(2.0, SleepMath.rubberLuck(0.5, 60), EPS);
        // A quarter ahead: neutral.
        assertEquals(0.0, SleepMath.rubberLuck(0.75, 60), EPS);
        // Half or more ahead: −2 Luck.
        assertEquals(-2.0, SleepMath.rubberLuck(0.9, 0), EPS);
    }

    @Test
    public void gambleAndIntervals() {
        assertEquals(0.5, SleepMath.gambleWinChance(0), EPS);
        assertEquals(0.6, SleepMath.gambleWinChance(2), EPS);
        assertEquals(0.75, SleepMath.gambleWinChance(20), EPS);
        assertEquals(0.25, SleepMath.gambleWinChance(-20), EPS);
        Random rand = new Random(4);
        for (int i = 0; i < 1000; i++) {
            int n = SleepMath.rollInterval(false, rand);
            assertTrue(n >= 8 && n <= 22);
            int f = SleepMath.rollInterval(true, rand);
            assertTrue(f >= 3 && f <= 7);
        }
    }

    @Test
    public void hungerCost() {
        Random rand = new Random(3);
        for (int i = 0; i < 1000; i++) {
            int loss = SleepMath.hungerLoss(10000, rand);   // 10 hours -> 5 +/- 1
            assertTrue(loss >= 4 && loss <= 6);
            int big = SleepMath.hungerLoss(24000, rand);    // capped at 6
            assertTrue(big <= 6);
            int none = SleepMath.hungerLoss(0, rand);
            assertTrue(none >= 0 && none <= 1);
        }
        assertEquals(14, SleepMath.applyHunger(20, 6));
        assertEquals(3, SleepMath.applyHunger(5, 6));
        assertEquals(2, SleepMath.applyHunger(2, 6));
        assertEquals(3, SleepMath.applyHunger(3, 1));
    }

    @Test
    public void debtChanges() {
        assertEquals(0.5, SleepMath.debtAfterSkip(0, 0.5, false), EPS);
        assertEquals(1.0, SleepMath.debtAfterSkip(2.5, 1.0, true), EPS);
        assertEquals(0.0, SleepMath.debtAfterSkip(1.0, 1.0, true), EPS);
        assertEquals(5.0, SleepMath.debtAfterSkip(4.9, 0.1, false), EPS);
        assertEquals(0.0, SleepMath.debtAfterNaturalNight(0.4), EPS);
        assertEquals(2.0, SleepMath.debtAfterNaturalNight(3.0), EPS);
    }

    @Test
    public void formatDuration() {
        assertEquals("1:42", SleepMath.formatDuration(102));
        assertEquals("0:05", SleepMath.formatDuration(5));
        assertEquals("8:40", SleepMath.formatDuration(520));
    }
}
