package io.github.tidefox.unrest.config;

/**
 * Current configuration values (DESIGN.md §8). Plain static fields with the
 * default values, so the core maths can be used and tested without Forge.
 * {@link ConfigLoader} fills them from {@code config/unrest.cfg}.
 */
public final class UnrestConfig {

    public enum Mode {
        ACTIVE, OBSERVE;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Which dream rolls are announced in chat (DESIGN.md §4.1). */
    public enum DreamAnnounce {
        OFF, CHANGES, ALL;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public static DreamAnnounce fromId(String id) {
            for (DreamAnnounce a : values()) {
                if (a.id().equalsIgnoreCase(id)) {
                    return a;
                }
            }
            return CHANGES;
        }
    }

    private UnrestConfig() {}

    // general
    public static boolean enabled = true;
    public static Mode mode = Mode.ACTIVE;
    /** auto | easy | normal | hard */
    public static String difficultyPreset = "auto";

    // meter
    public static double baseSeconds = 30.0;
    public static double scaleSeconds = 60.0;
    public static double capSeconds = 520.0;
    public static double wholeServerSeconds = 5.0;
    public static double drainPerSecond = 1.0 / 60.0;
    /** The fill speed doubles every this many seconds someone is asleep (0 = no ramp). */
    public static double rampDoubleSeconds = 75.0;
    /** The ramp never speeds the meter up more than this. */
    public static double rampMax = 8.0;

    // presets
    public static double easyScale = 0.5;
    public static double normalScale = 0.75;
    public static double hardScale = 1.0;

    // nights, indexed by NightType.ordinal(): calm, normal, restless, haunted
    public static final double[] nightWeight = {25, 45, 22, 8};
    public static final double[] nightPerDebt = {-4, -3, 4, 3};
    public static final double[] nightMin = {5, 20, 0, 0};
    public static final double[] nightMultiplier = {1.3, 1.0, 0.7, 0.6};
    public static double hauntedMinRatio = 0.5;
    /** Extra fill multiplier on a Haunted night below hauntedMinRatio (0 = the meter doesn't fill). */
    public static double hauntedBelowRatioMultiplier = 0.5;
    public static boolean hauntedAllowsWholeServerSleep = true;

    // debt
    public static double debtMax = 5.0;
    public static double partialSkipFactor = 1.0;
    public static double wholeServerRelief = 1.5;
    public static double naturalNightRelief = 1.0;

    // dreams
    public static boolean dreamsEnabled = true;
    public static int rollIntervalSeconds = 15;
    public static double deepBase = 0.10;
    public static double deepPerRatio = 0.20;
    public static double deepPerDebt = -0.02;
    public static double deepMin = 0.05;
    public static double restlessBase = 0.30;
    public static double restlessPerRatio = -0.20;
    public static double restlessPerDebt = 0.03;
    public static double nightmareBase = 0.03;
    public static double nightmarePerDebt = 0.3;
    public static double nightmareMeterLoss = 0.05;
    public static DreamAnnounce dreamAnnounce = DreamAnnounce.CHANGES;
    public static boolean nightmareSound = true;

    // nightWatch: awake players help the sleepers
    public static boolean watchEnabled = true;
    public static double watchKill = 0.03;
    public static double watchBlock = 0.002;
    public static double watchChunk = 0.005;
    public static double watchMaxPerPlayer = 0.20;
    public static boolean watchFeedback = true;

    // hungerCost
    public static boolean hungerEnabled = true;
    public static double hoursPerPoint = 2.0;
    public static int randomSpread = 1;
    public static int maxLoss = 6;
    public static int floor = 3;

    // afk
    public static int afkTimeoutSeconds = 300;
    public static int activeBeforeBedSeconds = 180;

    // antiDodge
    public static boolean countLeavers = true;
    public static boolean countDimensionLeavers = true;
    public static int leaverGraceSeconds = 300;

    // indicators
    public static boolean bossbar = true;
    public static boolean bossbarAlwaysAtNight = false;
    public static int bossbarMaxNames = 3;
    public static boolean chat = true;
    public static boolean announceNight = false;
    public static boolean actionbar = true;
    public static int actionbarIntervalSeconds = 5;
    public static boolean tabList = true;
    public static String tabPrefix = "§7[Zz]§r";
    public static boolean particles = true;
    public static boolean sounds = true;
    public static boolean asciiOnly = false;

    // commands
    public static boolean registerSleepAlias = true;

    // logging
    public static boolean nightLog = true;

    /** Scale applied to the fill time for a resolved preset name (easy, normal or hard). */
    public static double presetScale(String preset) {
        if ("easy".equals(preset)) {
            return easyScale;
        }
        if ("normal".equals(preset)) {
            return normalScale;
        }
        return hardScale;
    }

    /** The moon symbol, or its ASCII replacement. */
    public static String moon() {
        return asciiOnly ? "(night)" : "🌙";
    }

    /** The dream symbol, or its ASCII replacement. */
    public static String dreamSymbol() {
        return asciiOnly ? "(dream)" : "💭";
    }

    /** The sun symbol, or its ASCII replacement. */
    public static String sun() {
        return asciiOnly ? "(dawn)" : "☀";
    }
}
