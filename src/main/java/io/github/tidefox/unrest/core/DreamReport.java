package io.github.tidefox.unrest.core;

import io.github.tidefox.unrest.config.UnrestConfig.DreamAnnounce;

/**
 * Decides which dream rolls are worth a chat line and how they read
 * (DESIGN.md §4.1). Free of Minecraft classes so it can be unit tested.
 */
public final class DreamReport {

    private DreamReport() {}

    /**
     * Whether a sleeper going from {@code before} to {@code after} is announced.
     * {@code before} is null on a sleeper's first roll, which counts as Normal.
     * Nightmares have their own message and are never part of the dream line.
     */
    public static boolean announce(Dream before, Dream after, DreamAnnounce mode) {
        if (after == Dream.NIGHTMARE || mode == DreamAnnounce.OFF) {
            return false;
        }
        if (mode == DreamAnnounce.ALL) {
            return true;
        }
        return after != (before == null ? Dream.NORMAL : before);
    }

    /** The chat phrase for one sleeper, e.g. {@code "Steve sinks into a deep sleep"}. */
    public static String phrase(String name, Dream before, Dream after) {
        Dream prev = before == null ? Dream.NORMAL : before;
        switch (after) {
            case DEEP:
                return prev == Dream.DEEP ? name + " stays deeply asleep" : name + " sinks into a deep sleep";
            case RESTLESS:
                return prev == Dream.RESTLESS ? name + " keeps tossing and turning" : name + " tosses and turns";
            case NIGHTMARE:
                return name + " has a nightmare";
            default:
                if (prev == Dream.RESTLESS) {
                    return name + " settles down";
                }
                if (prev == Dream.DEEP) {
                    return name + " stirs a little";
                }
                return name + " sleeps soundly";
        }
    }

    /** Per-roll odds as whole percentages, in roll order: nightmare, restless, deep, normal. */
    public static int[] oddsPercent(double r, double debt) {
        double nightmare = Math.min(1.0, SleepMath.nightmareChance(r, debt));
        double restless = Math.min(1.0 - nightmare, SleepMath.restlessChance(r, debt));
        double deep = Math.min(1.0 - nightmare - restless, SleepMath.deepChance(r, debt));
        double normal = Math.max(0.0, 1.0 - nightmare - restless - deep);
        return new int[] {
            (int) Math.round(nightmare * 100), (int) Math.round(restless * 100),
            (int) Math.round(deep * 100), (int) Math.round(normal * 100)
        };
    }
}
