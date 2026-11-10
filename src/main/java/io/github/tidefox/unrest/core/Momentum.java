package io.github.tidefox.unrest.core;

import io.github.tidefox.unrest.config.UnrestConfig;

/**
 * Momentum between shared dream rolls (DESIGN.md §4.3): tension stored by bad
 * rolls and released by Deep ones, Deep chains, and the Night terror after a
 * Restless streak. Free of Minecraft classes so it can be unit tested.
 */
public final class Momentum {

    /** What one roll turned into after momentum. */
    public static final class Step {
        /** The dream that takes effect (a Night terror turns Restless into Nightmare). */
        public Dream dream;
        /** Deep rolls in a row, including this one (0 if this isn't Deep). */
        public int chain;
        /** Tension this roll released (only on Deep). */
        public double released;
        /** Tension stored after this roll. */
        public double tension;
        /** A Restless streak turned into a Nightmare. */
        public boolean nightTerror;
        /** This roll was the guaranteed Deep after a Night terror. */
        public boolean forced;
    }

    private int deepChain;
    private int restlessStreak;
    private double tension;
    private boolean forceDeep;

    /** Applies momentum to a {@code rolled} dream. {@code fever} doubles the tension stored. */
    public Step next(Dream rolled, boolean fever) {
        Step st = new Step();
        Dream d = rolled;
        if (forceDeep) {
            d = Dream.DEEP;
            forceDeep = false;
            st.forced = true;
        }
        if (d == Dream.RESTLESS) {
            restlessStreak++;
            int streak = UnrestConfig.nightTerrorStreak;
            if (streak > 0 && restlessStreak >= streak) {
                d = Dream.NIGHTMARE;
                st.nightTerror = true;
                restlessStreak = 0;
                forceDeep = true;
            }
        } else {
            restlessStreak = 0;
        }
        if (d == Dream.RESTLESS || d == Dream.NIGHTMARE) {
            if (UnrestConfig.tensionEnabled) {
                tension = Math.min(UnrestConfig.tensionMax, tension + UnrestConfig.tensionPerBadRoll * (fever ? 2 : 1));
            }
            deepChain = 0;
        } else if (d == Dream.DEEP) {
            deepChain++;
            st.released = tension;
            tension = 0;
        } else {
            deepChain = 0;
        }
        st.dream = d;
        st.chain = d == Dream.DEEP ? deepChain : 0;
        st.tension = tension;
        return st;
    }

    /** Meter a release of {@code released} tension adds. */
    public static double burst(double released) {
        return UnrestConfig.tensionEnabled ? released * UnrestConfig.tensionPayout : 0.0;
    }

    /**
     * How much each sleeper counts under {@code dream}, with the current Deep
     * chain and, on a Fever Dream, every effect made {@code feverIntensity} times as strong.
     */
    public double weight(Dream dream, boolean fever) {
        double w = dream.weight();
        if (dream == Dream.DEEP && UnrestConfig.chainEnabled && deepChain > 1) {
            w = Math.min(Math.max(w, UnrestConfig.chainMaxWeight), w + (deepChain - 1));
        }
        if (fever) {
            w = Math.max(0.0, Math.min(6.0, 1.0 + UnrestConfig.feverIntensity * (w - 1.0)));
        }
        return w;
    }

    public int chain() {
        return deepChain;
    }

    public double tension() {
        return tension;
    }

    public int restlessStreak() {
        return restlessStreak;
    }

    public void clear() {
        deepChain = 0;
        restlessStreak = 0;
        tension = 0;
        forceDeep = false;
    }
}
