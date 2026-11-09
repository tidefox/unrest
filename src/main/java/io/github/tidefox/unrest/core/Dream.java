package io.github.tidefox.unrest.core;

/** The result of one dream roll for one sleeper (DESIGN.md §4). */
public enum Dream {
    NORMAL(1.0, "Sound sleep"),
    DEEP(2.0, "Deep sleep"),
    RESTLESS(0.0, "Restless sleep"),
    NIGHTMARE(0.0, "Nightmare");

    private final double weight;
    private final String label;

    Dream(double weight, String label) {
        this.weight = weight;
        this.label = label;
    }

    /** How much this sleeper counts towards the meter until the next roll. */
    public double weight() {
        return weight;
    }

    /** Short name shown in the head menu and in chat hovers. */
    public String label() {
        return label;
    }

    /** What the dream does to the meter, for chat hovers. */
    public String effect() {
        switch (this) {
            case DEEP:
                return "Counts as 2 sleepers until the next dream";
            case RESTLESS:
                return "Doesn't count towards the meter until the next dream";
            case NIGHTMARE:
                return "Wakes the sleeper and sets the meter back";
            default:
                return "Counts as 1 sleeper";
        }
    }
}
