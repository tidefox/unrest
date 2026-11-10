package io.github.tidefox.unrest.core;

import io.github.tidefox.unrest.config.UnrestConfig;

/** The result of the sleepers' shared dream roll (DESIGN.md §4). */
public enum Dream {
    NORMAL("Sound sleep"),
    DEEP("Deep sleep"),
    RESTLESS("Restless sleep"),
    NIGHTMARE("Nightmare");

    private final String label;

    Dream(String label) {
        this.label = label;
    }

    /** How much each sleeper counts towards the meter until the next roll. */
    public double weight() {
        switch (this) {
            case DEEP:
                return UnrestConfig.deepWeight;
            case RESTLESS:
                return UnrestConfig.restlessWeight;
            default:
                return 1.0;
        }
    }

    /** Short name shown in the head menu and in chat hovers. */
    public String label() {
        return label;
    }

    /** What the dream does to the meter, for chat. */
    public String effect() {
        switch (this) {
            case DEEP:
                return "every sleeper counts " + fmt(UnrestConfig.deepWeight) + "x";
            case RESTLESS:
                return "every sleeper counts " + fmt(UnrestConfig.restlessWeight) + "x";
            case NIGHTMARE:
                return "dawn \u2212" + Math.round(UnrestConfig.nightmareMeterLoss * 100) + "%, the unluckiest sleeper wakes";
            default:
                return "every sleeper counts 1x";
        }
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }
}
