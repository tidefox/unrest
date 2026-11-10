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
    public static double baseSeconds = 20.0;
    public static double scaleSeconds = 40.0;
    public static double capSeconds = 240.0;
    public static double wholeServerSeconds = 5.0;
    public static double drainPerSecond = 1.0 / 60.0;
    /** The fill speed doubles every this many seconds someone is asleep (0 = no ramp). */
    public static double rampDoubleSeconds = 40.0;
    /** The ramp never speeds the meter up more than this. */
    public static double rampMax = 8.0;
    /** Longest a partial skip can take while someone stays asleep (0 = no limit). */
    public static double maxSleepSeconds = 120.0;

    // presets
    public static double easyScale = 0.5;
    public static double normalScale = 0.75;
    public static double hardScale = 1.0;

    // nights, indexed by NightType.ordinal(): calm, normal, restless, haunted, fever
    public static final double[] nightWeight = {25, 40, 22, 8, 5};
    public static final double[] nightPerDebt = {-4, -3, 4, 3, 0};
    public static final double[] nightMin = {5, 20, 0, 0, 0};
    public static final double[] nightMultiplier = {1.3, 1.0, 0.7, 0.6, 1.0};
    /** Fever Dream: how much wilder every dream effect is, and the roll interval. */
    public static double feverIntensity = 3.0;
    public static int feverRollMinSeconds = 3;
    public static int feverRollMaxSeconds = 7;
    /** The Fever Dream weight is multiplied by this on a full moon. */
    public static double feverFullMoonBonus = 2.0;
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
    /** Each shared roll comes a random number of seconds after the last, in this range. */
    public static int rollMinSeconds = 8;
    public static int rollMaxSeconds = 22;
    /** Who sees the rolling number as a title: sleepers, all or off. */
    public static String spin = "sleepers";
    public static double deepBase = 0.10;
    public static double deepPerRatio = 0.20;
    public static double deepPerDebt = -0.02;
    public static double deepMin = 0.05;
    public static double restlessBase = 0.30;
    public static double restlessPerRatio = -0.20;
    public static double restlessPerDebt = 0.03;
    public static double nightmareBase = 0.06;
    public static double nightmarePerDebt = 0.3;
    public static double nightmareMeterLoss = 0.10;
    /** How much each sleeper counts on a Deep and on a Restless roll. */
    public static double deepWeight = 2.0;
    public static double restlessWeight = 0.5;
    /** Deep chance added per point of the sleepers' combined Luck. */
    public static double luckDeep = 0.04;
    /** Share of the Restless and Nightmare chances removed per point of Luck. */
    public static double luckBad = 0.15;
    /** The combined Luck counts up to this much either way. */
    public static double luckMax = 5.0;
    public static DreamAnnounce dreamAnnounce = DreamAnnounce.CHANGES;
    public static boolean nightmareSound = true;

    // momentum (DESIGN.md §4.3)
    public static boolean tensionEnabled = true;
    public static double tensionPerBadRoll = 1.0;
    public static double tensionMax = 5.0;
    /** Meter added per point of tension when a Deep roll releases it. */
    public static double tensionPayout = 0.05;
    public static boolean chainEnabled = true;
    /** Deep rolls in a row count 2x, 3x, 4x… up to this. */
    public static double chainMaxWeight = 4.0;
    /** Restless rolls in a row that turn into a Night terror (0 = never). */
    public static int nightTerrorStreak = 3;
    /** Luck points the rubber band adds at most when the meter lags (and takes when it leads). 0 = off. */
    public static double rubberBand = 1.0;
    public static boolean doubleOrNothing = true;
    public static double gambleAt = 0.5;
    public static int gambleCountdownSeconds = 5;
    public static double gambleWinMeter = 0.75;
    public static double gambleLoseMeter = 0.25;
    /** Win chance added per point of Luck, around 50%. */
    public static double gambleLuck = 0.05;

    // moon (DESIGN.md §3.3)
    public static boolean moonEnabled = true;
    /** Deep chance added per point of moon chaos (+1 full moon, −1 new moon). */
    public static double moonDeep = 0.05;
    /** Share added to the bad rolls per point of moon chaos. */
    public static double moonBad = 0.25;

    // reaper (DESIGN.md §4.4)
    public static boolean reaperEnabled = true;
    public static int reaperSeconds = 60;
    public static double reaperReward = 0.20;
    public static double reaperPenalty = 0.05;
    public static double reaperHealth = 30.0;
    /** Registry name of the mob sent as the Reaper, e.g. minecraft:wither_skeleton or lycanitesmobs:reaper. */
    public static String reaperEntity = "minecraft:wither_skeleton";

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
    /** Tag AFK players (afk.timeoutSeconds idle, not in bed) in the tab list, day or night. */
    public static boolean tabAfk = true;
    public static String tabAfkPrefix = "§8[AFK]§r";
    public static boolean particles = true;
    public static boolean sounds = true;
    public static boolean asciiOnly = false;
    /** Harmless lightning (no fire, no damage) for jackpots, the Reaper and Fever Dreams. */
    public static boolean lightning = true;

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
