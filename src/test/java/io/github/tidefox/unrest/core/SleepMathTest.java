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
        assertEquals(50.0, SleepMath.fillSeconds(3.0 / 4), EPS);
        assertEquals(60.0, SleepMath.fillSeconds(2.0 / 3), EPS);
        assertEquals(90.0, SleepMath.fillSeconds(1.0 / 2), EPS);
        assertEquals(150.0, SleepMath.fillSeconds(1.0 / 3), EPS);
        assertEquals(210.0, SleepMath.fillSeconds(1.0 / 4), EPS);
        assertEquals(450.0, SleepMath.fillSeconds(1.0 / 8), EPS);
        assertEquals(520.0, SleepMath.fillSeconds(1.0 / 10), EPS);
        assertEquals(520.0, SleepMath.fillSeconds(1.0 / 20), EPS);
        assertTrue(Double.isInfinite(SleepMath.fillSeconds(0)));
    }

    @Test
    public void meterStepUsesMultiplierAndPreset() {
        assertEquals(1.0 / 90, SleepMath.meterStep(0.5, 1.0, 1.0), EPS);
        assertEquals(0.7 / 90, SleepMath.meterStep(0.5, 0.7, 1.0), EPS);
        assertEquals(1.0 / 45, SleepMath.meterStep(0.5, 1.0, 0.5), EPS);
        assertEquals(0.0, SleepMath.meterStep(0, 1.0, 1.0), EPS);
        assertEquals(45.0, SleepMath.secondsToFull(0.5, 0.5, 1.0, 1.0), 1e-6);
    }

    @Test
    public void rampDoublesAndIsCapped() {
        assertEquals(1.0, SleepMath.ramp(0), EPS);
        assertEquals(2.0, SleepMath.ramp(75), EPS);
        assertEquals(4.0, SleepMath.ramp(150), EPS);
        assertEquals(8.0, SleepMath.ramp(10000), EPS);
    }

    @Test
    public void secondsToFullWithRampIsShorter() {
        // No ramp yet built up: 1/90 a second from half full. The ramp shortens 45s.
        double flat = SleepMath.secondsToFull(0.5, 0.5, 1.0, 1.0);
        double ramped = SleepMath.secondsToFull(0.5, 0.5, 1.0, 1.0, 0);
        assertTrue(ramped < flat);
        assertTrue(ramped > 30);
        // At the ramp cap it is 8 times as fast.
        assertEquals(Math.ceil(45.0 / 8), SleepMath.secondsToFull(0.5, 0.5, 1.0, 1.0, 10000), 1e-6);
        assertTrue(Double.isInfinite(SleepMath.secondsToFull(0.5, 0, 1.0, 1.0, 0)));
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
        assertEquals(45, w0[1], EPS);
        assertEquals(22, w0[2], EPS);
        assertEquals(8, w0[3], EPS);
        double[] w5 = SleepMath.nightWeights(5);
        assertEquals(5, w5[0], EPS);
        assertEquals(30, w5[1], EPS);
        assertEquals(42, w5[2], EPS);
        assertEquals(23, w5[3], EPS);
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
        assertEquals(0.45, counts[1] / (double) runs, 0.01);
        assertEquals(0.22, counts[2] / (double) runs, 0.01);
        assertEquals(0.08, counts[3] / (double) runs, 0.01);
    }

    @Test
    public void dreamChances() {
        assertEquals(0.015, SleepMath.nightmareChance(0.5, 0), EPS);
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
        assertEquals(0.015, counts[Dream.NIGHTMARE.ordinal()] / (double) runs, 0.003);
        assertEquals(0.20, counts[Dream.RESTLESS.ordinal()] / (double) runs, 0.005);
        assertEquals(0.20, counts[Dream.DEEP.ordinal()] / (double) runs, 0.005);
        assertEquals(0.585, counts[Dream.NORMAL.ordinal()] / (double) runs, 0.005);
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
