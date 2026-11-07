package io.github.tidefox.unrest.core;

import io.github.tidefox.unrest.config.UnrestConfig;

import java.util.Locale;

/** The character of a night, rolled at dusk (DESIGN.md §3.1). */
public enum NightType {
    CALM("Calm Night", "The night is calm."),
    NORMAL("Normal Night", "Night falls."),
    RESTLESS("Restless Night", "The night feels restless…"),
    HAUNTED("Haunted Night", "Something is wrong with this night.");

    private final String displayName;
    private final String announcement;

    NightType(String displayName, String announcement) {
        this.displayName = displayName;
        this.announcement = announcement;
    }

    public String displayName() {
        return displayName;
    }

    public String announcement() {
        return announcement;
    }

    /** Lower-case id used in the saved data and the night log. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public double multiplier() {
        return UnrestConfig.nightMultiplier[ordinal()];
    }

    /** Parses an id written by {@link #id()}, or returns null. */
    public static NightType fromId(String id) {
        if (id == null) {
            return null;
        }
        for (NightType t : values()) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }
}
