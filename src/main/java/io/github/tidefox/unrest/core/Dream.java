package io.github.tidefox.unrest.core;

/** The result of one dream roll for one sleeper (DESIGN.md §4). */
public enum Dream {
    NORMAL(1.0),
    DEEP(2.0),
    RESTLESS(0.0),
    NIGHTMARE(0.0);

    private final double weight;

    Dream(double weight) {
        this.weight = weight;
    }

    /** How much this sleeper counts towards the meter until the next roll. */
    public double weight() {
        return weight;
    }
}
