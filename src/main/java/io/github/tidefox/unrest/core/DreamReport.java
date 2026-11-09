package io.github.tidefox.unrest.core;

import io.github.tidefox.unrest.config.UnrestConfig.DreamAnnounce;

import java.util.List;

/**
 * Decides which shared dream rolls are worth a chat line and how they read
 * (DESIGN.md §4.1). Free of Minecraft classes so it can be unit tested.
 */
public final class DreamReport {

    private DreamReport() {}

    /**
     * Whether a roll from {@code before} to {@code after} is announced.
     * {@code before} is null on the first roll, which counts as Normal.
     * Nightmares are always announced, whatever the mode.
     */
    public static boolean announce(Dream before, Dream after, DreamAnnounce mode) {
        if (after == Dream.NIGHTMARE) {
            return true;
        }
        if (mode == DreamAnnounce.OFF) {
            return false;
        }
        if (mode == DreamAnnounce.ALL) {
            return true;
        }
        return after != (before == null ? Dream.NORMAL : before);
    }

    /** "Steve", "Steve and Alex", "Steve, Alex and Bob", "Steve, Alex and 3 others". */
    public static String names(List<String> names) {
        int n = names.size();
        if (n == 0) {
            return "Nobody";
        }
        if (n == 1) {
            return names.get(0);
        }
        if (n == 2) {
            return names.get(0) + " and " + names.get(1);
        }
        if (n == 3) {
            return names.get(0) + ", " + names.get(1) + " and " + names.get(2);
        }
        return names.get(0) + ", " + names.get(1) + " and " + (n - 2) + " others";
    }

    /**
     * The chat phrase for the sleepers, e.g. {@code "Steve and Alex sink into a deep sleep together"}.
     * {@code woken} names the sleeper a Nightmare wakes.
     */
    public static String phrase(List<String> sleepers, Dream before, Dream after, String woken) {
        String who = names(sleepers);
        boolean one = sleepers.size() == 1;
        Dream prev = before == null ? Dream.NORMAL : before;
        switch (after) {
            case DEEP:
                if (prev == Dream.DEEP) {
                    return who + (one ? " stays" : " stay") + " deeply asleep";
                }
                return who + (one ? " sinks into a deep sleep" : " sink into a deep sleep together");
            case RESTLESS:
                if (prev == Dream.RESTLESS) {
                    return who + (one ? " keeps" : " keep") + " tossing and turning";
                }
                return who + (one ? " tosses and turns" : " toss and turn");
            case NIGHTMARE:
                String grip = "A nightmare grips " + who + "! ";
                return grip + (woken == null ? "Someone" : woken) + " wakes up in a cold sweat";
            default:
                if (prev == Dream.RESTLESS) {
                    return who + (one ? " settles" : " settle") + " down";
                }
                if (prev == Dream.DEEP) {
                    return who + (one ? " stirs" : " stir") + " a little";
                }
                return who + (one ? " sleeps" : " sleep") + " soundly";
        }
    }

    /** "luck +2", "luck -1", or null at 0. */
    public static String luckTag(double luck) {
        long l = Math.round(luck);
        if (l == 0) {
            return null;
        }
        return "luck " + (l > 0 ? "+" : "−") + Math.abs(l);
    }

    public static int[] oddsPercent(double r, double debt) {
        return oddsPercent(r, debt, 0);
    }

    public static int[] oddsPercent(double r, double debt, double luck) {
        return oddsPercent(r, debt, luck, 0);
    }

    /** Per-roll odds as whole percentages, in roll order: nightmare, restless, deep, normal. */
    public static int[] oddsPercent(double r, double debt, double luck, double chaos) {
        double nightmare = Math.min(1.0, SleepMath.nightmareChance(r, debt, luck, chaos));
        double restless = Math.min(1.0 - nightmare, SleepMath.restlessChance(r, debt, luck, chaos));
        double deep = Math.min(1.0 - nightmare - restless, SleepMath.deepChance(r, debt, luck, chaos));
        double normal = Math.max(0.0, 1.0 - nightmare - restless - deep);
        return new int[] {
            (int) Math.round(nightmare * 100), (int) Math.round(restless * 100),
            (int) Math.round(deep * 100), (int) Math.round(normal * 100)
        };
    }
}
