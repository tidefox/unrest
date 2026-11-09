package io.github.tidefox.unrest.config;

import io.github.tidefox.unrest.config.UnrestConfig.Mode;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

/**
 * Reads {@code config/unrest.cfg} into {@link UnrestConfig}. A plain Forge
 * {@link Configuration} is used rather than {@code @Config}, because it can be
 * re-read from disk for {@code /unrest reload}.
 */
public final class ConfigLoader {

    private static final String GENERAL = "general";
    private static final String METER = "meter";
    private static final String PRESETS = "presets";
    private static final String NIGHTS = "nights";
    private static final String DEBT = "debt";
    private static final String DREAMS = "dreams";
    private static final String HUNGER = "hungerCost";
    private static final String AFK = "afk";
    private static final String ANTI_DODGE = "antiDodge";
    private static final String INDICATORS = "indicators";
    private static final String COMMANDS = "commands";
    private static final String LOGGING = "logging";

    private static final String[] NIGHT_IDS = {"calm", "normal", "restless", "haunted"};
    private static final double[] DEFAULT_WEIGHT = {25, 45, 22, 8};
    private static final double[] DEFAULT_PER_DEBT = {-4, -3, 4, 3};
    private static final double[] DEFAULT_MIN = {5, 20, 0, 0};
    private static final double[] DEFAULT_MULTIPLIER = {1.3, 1.0, 0.7, 0.6};

    private static Configuration cfg;

    private ConfigLoader() {}

    public static void init(File file) {
        cfg = new Configuration(file);
        read();
    }

    /** Re-reads the file from disk. */
    public static void reload() {
        if (cfg == null) {
            return;
        }
        cfg.load();
        read();
    }

    /** Changes the mode and writes it back to the file. */
    public static void setMode(Mode mode) {
        UnrestConfig.mode = mode;
        if (cfg != null) {
            cfg.get(GENERAL, "mode", "active").set(mode.id());
            cfg.save();
        }
    }

    private static void read() {
        UnrestConfig.enabled = cfg.getBoolean("enabled", GENERAL, true, "Turns the whole mod on or off.");
        String mode = cfg.getString("mode", GENERAL, "active",
                "active: the full Unrest mechanic. observe: vanilla sleep, but every night is logged.",
                new String[] {"active", "observe"});
        UnrestConfig.mode = "observe".equalsIgnoreCase(mode) ? Mode.OBSERVE : Mode.ACTIVE;
        UnrestConfig.difficultyPreset = cfg.getString("difficultyPreset", GENERAL, "auto",
                "auto follows the world difficulty.", new String[] {"auto", "easy", "normal", "hard"});

        UnrestConfig.baseSeconds = d(METER, "baseSeconds", 30.0, 0.0, 10000.0, "BASE in T(r) = BASE + SCALE * (1 - r) / r");
        UnrestConfig.scaleSeconds = d(METER, "scaleSeconds", 60.0, 0.0, 10000.0, "SCALE in T(r)");
        UnrestConfig.capSeconds = d(METER, "capSeconds", 520.0, 1.0, 100000.0, "Longest possible fill time");
        UnrestConfig.wholeServerSeconds = d(METER, "wholeServerSeconds", 5.0, 0.0, 600.0, "Delay before a whole-server sleep skips");
        UnrestConfig.drainPerSecond = d(METER, "drainPerSecond", 0.016667, 0.0, 1.0, "Meter lost per second while nobody counts");

        UnrestConfig.easyScale = d(PRESETS, "easyScale", 0.5, 0.01, 100.0, "Fill time multiplier on easy");
        UnrestConfig.normalScale = d(PRESETS, "normalScale", 0.75, 0.01, 100.0, "Fill time multiplier on normal");
        UnrestConfig.hardScale = d(PRESETS, "hardScale", 1.0, 0.01, 100.0, "Fill time multiplier on hard");

        for (int i = 0; i < NIGHT_IDS.length; i++) {
            String id = NIGHT_IDS[i];
            UnrestConfig.nightWeight[i] = cfg.getInt(id + "Weight", NIGHTS, (int) DEFAULT_WEIGHT[i], 0, 1000, "Base roll weight");
            UnrestConfig.nightPerDebt[i] = d(NIGHTS, id + "PerDebt", DEFAULT_PER_DEBT[i], -1000.0, 1000.0, "Weight change per point of Unrest");
            UnrestConfig.nightMin[i] = cfg.getInt(id + "Min", NIGHTS, (int) DEFAULT_MIN[i], 0, 1000, "Lowest weight");
            UnrestConfig.nightMultiplier[i] = d(NIGHTS, id + "Multiplier", DEFAULT_MULTIPLIER[i], 0.0, 100.0, "Meter speed multiplier");
        }
        UnrestConfig.hauntedMinRatio = d(NIGHTS, "hauntedMinRatio", 0.5, 0.0, 1.0, "Share of the server that must sleep on a Haunted night");
        UnrestConfig.hauntedAllowsWholeServerSleep = cfg.getBoolean("hauntedAllowsWholeServerSleep", NIGHTS, true,
                "Whether a whole-server sleep skips a Haunted night");

        UnrestConfig.debtMax = d(DEBT, "max", 5.0, 0.0, 100.0, "Highest Unrest");
        UnrestConfig.partialSkipFactor = d(DEBT, "partialSkipFactor", 1.0, 0.0, 100.0, "D += factor * (1 - r) on a partial skip");
        UnrestConfig.wholeServerRelief = d(DEBT, "wholeServerRelief", 1.5, 0.0, 100.0, "D lost on a whole-server sleep");
        UnrestConfig.naturalNightRelief = d(DEBT, "naturalNightRelief", 1.0, 0.0, 100.0, "D lost when a night ends on its own");

        UnrestConfig.dreamsEnabled = cfg.getBoolean("enabled", DREAMS, true, "Turns dream rolls on or off");
        UnrestConfig.rollIntervalSeconds = cfg.getInt("rollIntervalSeconds", DREAMS, 15, 1, 3600, "Seconds between dream rolls");
        UnrestConfig.deepBase = d(DREAMS, "deepBase", 0.10, 0.0, 1.0, "");
        UnrestConfig.deepPerRatio = d(DREAMS, "deepPerRatio", 0.20, -1.0, 1.0, "");
        UnrestConfig.deepPerDebt = d(DREAMS, "deepPerDebt", -0.02, -1.0, 1.0, "");
        UnrestConfig.deepMin = d(DREAMS, "deepMin", 0.05, 0.0, 1.0, "");
        UnrestConfig.restlessBase = d(DREAMS, "restlessBase", 0.30, 0.0, 1.0, "");
        UnrestConfig.restlessPerRatio = d(DREAMS, "restlessPerRatio", -0.20, -1.0, 1.0, "");
        UnrestConfig.restlessPerDebt = d(DREAMS, "restlessPerDebt", 0.03, -1.0, 1.0, "");
        UnrestConfig.nightmareBase = d(DREAMS, "nightmareBase", 0.03, 0.0, 1.0, "");
        UnrestConfig.nightmarePerDebt = d(DREAMS, "nightmarePerDebt", 0.3, -10.0, 10.0, "");
        UnrestConfig.nightmareMeterLoss = d(DREAMS, "nightmareMeterLoss", 0.05, 0.0, 1.0, "Meter lost on a Nightmare");
        UnrestConfig.dreamAnnounce = UnrestConfig.DreamAnnounce.fromId(cfg.getString("announce", DREAMS, "changes",
                "Dream rolls in chat. off: never. changes: when a sleeper's dream changes (a first roll counts as a"
                        + " change from Normal). all: every roll. Nightmares are always announced while chat is on.",
                new String[] {"off", "changes", "all"}));
        UnrestConfig.nightmareSound = cfg.getBoolean("nightmareSound", DREAMS, true,
                "Play a sound to a player woken by a Nightmare (also needs indicators.sounds)");

        UnrestConfig.hungerEnabled = cfg.getBoolean("enabled", HUNGER, true, "Sleepers lose food when the night is skipped");
        UnrestConfig.hoursPerPoint = d(HUNGER, "hoursPerPoint", 2.0, 0.1, 100.0, "Skipped hours per food point");
        UnrestConfig.randomSpread = cfg.getInt("randomSpread", HUNGER, 1, 0, 20, "Random +/- added to the loss");
        UnrestConfig.maxLoss = cfg.getInt("maxLoss", HUNGER, 6, 0, 20, "Most food points lost in one skip");
        UnrestConfig.floor = cfg.getInt("floor", HUNGER, 3, 0, 20, "A skip never takes anyone below this");

        UnrestConfig.afkTimeoutSeconds = cfg.getInt("timeoutSeconds", AFK, 300, 1, 86400, "Seconds without activity before a player is AFK");
        UnrestConfig.activeBeforeBedSeconds = cfg.getInt("activeBeforeBedSeconds", AFK, 180, 0, 86400,
                "A sleeper must have been active this many seconds before getting into bed");

        UnrestConfig.countLeavers = cfg.getBoolean("countLeavers", ANTI_DODGE, true,
                "An awake player who logs out at night keeps counting as awake for graceSeconds after their last"
                        + " activity, so leaving the server doesn't speed up the skip");
        UnrestConfig.countDimensionLeavers = cfg.getBoolean("countDimensionLeavers", ANTI_DODGE, true,
                "The same for an awake player who leaves the Overworld (e.g. steps into the Nether)");
        UnrestConfig.leaverGraceSeconds = cfg.getInt("graceSeconds", ANTI_DODGE, 300, 0, 86400,
                "How long a leaver keeps counting, from their last activity. Matches afk.timeoutSeconds by default,"
                        + " so leaving is never better than standing still");

        UnrestConfig.bossbar = cfg.getBoolean("bossbar", INDICATORS, true, "");
        UnrestConfig.bossbarAlwaysAtNight = cfg.getBoolean("alwaysAtNight", INDICATORS, false, "Show the bossbar all night");
        UnrestConfig.bossbarMaxNames = cfg.getInt("maxNames", INDICATORS, 3, 0, 20, "Sleeper names on the bossbar");
        UnrestConfig.chat = cfg.getBoolean("chat", INDICATORS, true, "");
        UnrestConfig.announceNight = cfg.getBoolean("announceNight", INDICATORS, false,
                "Post the night type in chat at dusk (\"Night falls.\"). The bossbar and /unrest show it either way.");
        UnrestConfig.actionbar = cfg.getBoolean("actionbar", INDICATORS, true, "");
        UnrestConfig.actionbarIntervalSeconds = cfg.getInt("actionbarIntervalSeconds", INDICATORS, 5, 1, 3600, "");
        UnrestConfig.tabList = cfg.getBoolean("tabList", INDICATORS, true, "");
        UnrestConfig.tabPrefix = cfg.getString("tabPrefix", INDICATORS, "§7[Zz]§r", "Shown before sleepers' names in the tab list");
        UnrestConfig.particles = cfg.getBoolean("particles", INDICATORS, true, "");
        UnrestConfig.sounds = cfg.getBoolean("sounds", INDICATORS, true, "");
        UnrestConfig.asciiOnly = cfg.getBoolean("asciiOnly", INDICATORS, false, "Replace the moon and sun symbols with text");

        UnrestConfig.registerSleepAlias = cfg.getBoolean("registerSleepAlias", COMMANDS, true, "Also register /sleep (needs a restart)");

        UnrestConfig.nightLog = cfg.getBoolean("nightLog", LOGGING, true, "Append every night to logs/unrest-nights.csv");

        if (cfg.hasChanged()) {
            cfg.save();
        }
    }

    private static double d(String category, String key, double def, double min, double max, String comment) {
        return cfg.get(category, key, def, comment, min, max).getDouble(def);
    }
}
