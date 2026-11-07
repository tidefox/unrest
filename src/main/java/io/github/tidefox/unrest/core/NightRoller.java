package io.github.tidefox.unrest.core;

import java.util.Random;

/** Rolls the night type at dusk, weighted by Unrest (DESIGN.md §3.1). */
public final class NightRoller {

    private NightRoller() {}

    public static NightType roll(double debt, Random rand) {
        return SleepMath.rollNight(debt, rand);
    }
}
