package io.github.tidefox.unrest.core;

import io.github.tidefox.unrest.config.UnrestConfig;
import io.github.tidefox.unrest.config.UnrestConfig.Mode;
import io.github.tidefox.unrest.data.UnrestWorldData;
import io.github.tidefox.unrest.display.ActionbarDisplay;
import io.github.tidefox.unrest.display.BossbarDisplay;
import io.github.tidefox.unrest.display.ChatDisplay;
import io.github.tidefox.unrest.display.ParticleDisplay;
import io.github.tidefox.unrest.display.TabListDisplay;
import io.github.tidefox.unrest.log.NightLog;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Counts N and S every second, runs the night lifecycle, the meter, dream
 * rolls and the skip (DESIGN.md §2–§4). Only the Overworld is handled.
 */
public final class SleepTracker {

    public static final int NIGHT_START = 12541;
    public static final int NIGHT_END = 23458;

    /** How a player shows up in the head menu. */
    public enum Status {
        SLEEPING, IN_BED, AWAKE, AFK, ELSEWHERE
    }

    public enum Outcome {
        WHOLE_SERVER("whole_server"), PARTIAL("partial"), NATURAL("natural"), VANILLA_SKIP("vanilla_skip"),
        /** The time was changed by something else, such as {@code /time set}. */
        EXTERNAL("external");

        public final String id;

        Outcome(String id) {
            this.id = id;
        }
    }

    /** The current night (or a daytime thunderstorm, which counts as a Normal night). */
    public static final class Night {
        public final NightType type;
        public final long day;
        public final boolean storm;
        public final double debtBefore;
        public int seconds;
        public int peakSleepers;
        public int nightmares;

        Night(NightType type, long day, boolean storm, double debtBefore) {
            this.type = type;
            this.day = day;
            this.storm = storm;
            this.debtBefore = debtBefore;
        }
    }

    /** Who is where, taken once per second. */
    public static final class Census {
        public final List<EntityPlayerMP> counted = new ArrayList<>();
        public final List<EntityPlayerMP> sleepers = new ArrayList<>();
        public final List<EntityPlayerMP> inBed = new ArrayList<>();
        public final Map<UUID, Status> status = new HashMap<>();
        public int playersOnline;
        public int afk;

        public double ratio() {
            return counted.isEmpty() ? 0.0 : (double) sleepers.size() / counted.size();
        }
    }

    private final MinecraftServer server;
    private final ActivityTracker activity;
    private final NightLog log;
    private final DreamRoller dreams = new DreamRoller();
    private final BossbarDisplay bossbar = new BossbarDisplay();
    private final Random rand = new Random();

    private Census census = new Census();
    private Set<UUID> shownSleepers = new HashSet<>();
    private final Set<UUID> quietWake = new HashSet<>();
    private Night night;
    private long concludedDay = -1;
    private Set<UUID> lastOnline = new HashSet<>();
    private long timeAtTickEnd = -1;

    private int tickCounter;
    private long timeAtTickStart;
    private List<EntityPlayerMP> asleepAtTickStart = Collections.emptyList();
    private int wholeServerCount;
    private int secondsUntilRoll;
    private boolean chimed50;
    private boolean chimed90;
    private int actionbarTimer;
    private int particleTimer;

    public SleepTracker(MinecraftServer server, ActivityTracker activity) {
        this.server = server;
        this.activity = activity;
        this.log = new NightLog(server.getFile("logs/unrest-nights.csv"));
    }

    // ---- accessors for the command, menu and tab list ----

    public ActivityTracker activity() {
        return activity;
    }

    public Census census() {
        return census;
    }

    public Night night() {
        return night;
    }

    public boolean isShownAsSleeper(UUID id) {
        return shownSleepers.contains(id);
    }

    public WorldServer overworld() {
        return server.getWorld(0);
    }

    public UnrestWorldData data() {
        return UnrestWorldData.get(overworld());
    }

    /** The preset in use: easy, normal or hard. */
    public String presetName(WorldServer world) {
        String p = UnrestConfig.difficultyPreset;
        if (!"auto".equals(p)) {
            return p;
        }
        switch (world.getDifficulty()) {
            case PEACEFUL:
            case EASY:
                return "easy";
            case NORMAL:
                return "normal";
            default:
                return "hard";
        }
    }

    /** Best guess at the seconds until dawn, counting both the meter and the natural end of the night. */
    public double secondsToDawn(WorldServer world) {
        if (night == null) {
            return Double.POSITIVE_INFINITY;
        }
        long tod = world.getWorldTime() % 24000L;
        double natural = night.storm ? Double.POSITIVE_INFINITY : (24000L - tod) / 20.0;
        int n = census.counted.size();
        int s = census.sleepers.size();
        if (n == 0) {
            return natural;
        }
        if (s == n && wholeServerAllowed()) {
            return Math.max(0, Math.ceil(UnrestConfig.wholeServerSeconds) - wholeServerCount);
        }
        if (night.type == NightType.HAUNTED && (double) s / n < UnrestConfig.hauntedMinRatio) {
            return natural;
        }
        double rEff = Math.min(1.0, dreams.weight(census.sleepers) / n);
        double meter = SleepMath.secondsToFull(data().getMeter(), rEff, night.type.multiplier(),
                UnrestConfig.presetScale(presetName(world)));
        return Math.min(meter, natural);
    }

    public void setDebt(double debt) {
        data().setDebt(SleepMath.clampDebt(debt));
    }

    /** Clears everything shown to players, e.g. when the server stops. */
    public void shutdown() {
        bossbar.hide();
        Set<UUID> old = shownSleepers;
        shownSleepers = new HashSet<>();
        refreshTabList(old);
    }

    // ---- ticking ----

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.side != Side.SERVER || e.world.provider.getDimension() != 0) {
            return;
        }
        WorldServer world = (WorldServer) e.world;
        if (e.phase == TickEvent.Phase.START) {
            timeAtTickStart = world.getWorldTime();
            // Nothing in vanilla changes the time between two world ticks, so a
            // difference here comes from a command or another mod.
            if (timeAtTickEnd >= 0 && timeAtTickStart != timeAtTickEnd && UnrestConfig.enabled) {
                onExternalTimeChange(world, timeAtTickStart);
            }
            asleepAtTickStart = vanillaWouldSkip(world);
            return;
        }
        if (!asleepAtTickStart.isEmpty()) {
            boolean allWoken = true;
            for (EntityPlayerMP p : asleepAtTickStart) {
                if (p.isPlayerSleeping()) {
                    allWoken = false;
                    break;
                }
            }
            if (allWoken && UnrestConfig.enabled) {
                long ticks = Math.max(0, world.getWorldTime() - timeAtTickStart - 1);
                onVanillaSkip(world, ticks, asleepAtTickStart);
            }
            asleepAtTickStart = Collections.emptyList();
        }
        if (++tickCounter >= 20) {
            tickCounter = 0;
            secondTick(world);
        }
        timeAtTickEnd = world.getWorldTime();
    }

    /**
     * The time was set from outside (e.g. {@code /time set day}). If it is no
     * longer the same night, the night ends without a skip and without the
     * natural-night relief, and is logged as {@code external}.
     */
    private void onExternalTimeChange(WorldServer world, long time) {
        if (night == null) {
            return;
        }
        UnrestWorldData data = UnrestWorldData.get(world);
        long tod = time % 24000L;
        boolean sameNight = !night.storm && time / 24000L == night.day && tod >= NIGHT_START && tod <= NIGHT_END;
        if (sameNight) {
            return;
        }
        if (!night.storm) {
            NightLog.Entry e = baseEntry(night, Outcome.EXTERNAL, data.getDebt());
            e.sleepersAtSkip = census.sleepers.size();
            e.ratioAtSkip = census.ratio();
            log.write(e);
            concludedDay = night.day;
        }
        night = null;
        data.setMeter(0);
        resetMeterState();
        bossbar.hide();
        Set<UUID> old = shownSleepers;
        shownSleepers = new HashSet<>();
        refreshTabList(old);
    }

    /** The players vanilla is about to wake if every non-spectator in the Overworld is fully asleep, else empty. */
    private static List<EntityPlayerMP> vanillaWouldSkip(WorldServer world) {
        List<EntityPlayerMP> asleep = new ArrayList<>();
        for (EntityPlayer p : world.playerEntities) {
            if (p.isSpectator()) {
                continue;
            }
            if (!p.isPlayerFullyAsleep() || !(p instanceof EntityPlayerMP)) {
                return Collections.emptyList();
            }
            asleep.add((EntityPlayerMP) p);
        }
        return asleep;
    }

    private void secondTick(WorldServer world) {
        UnrestWorldData data = UnrestWorldData.get(world);
        long time = world.getWorldTime();
        long day = time / 24000L;
        long tod = time % 24000L;
        boolean nightWindow = tod >= NIGHT_START && tod <= NIGHT_END;
        boolean sleepWindow = nightWindow || world.isThundering();
        boolean active = UnrestConfig.enabled && UnrestConfig.mode == Mode.ACTIVE;

        census = takeCensus();

        if (!UnrestConfig.enabled) {
            night = null;
            data.setMeter(0);
            updateShownSleepers(world, false);
            bossbar.hide();
            return;
        }

        updateNight(world, data, day, tod, nightWindow, sleepWindow, active);
        updateShownSleepers(world, active && night != null);

        if (night == null) {
            data.setMeter(0);
            bossbar.hide();
            return;
        }
        night.seconds++;
        night.peakSleepers = Math.max(night.peakSleepers, census.sleepers.size());

        if (!active) {
            bossbar.hide();
            return;
        }
        runMeter(world, data);
    }

    private Census takeCensus() {
        Census c = new Census();
        List<EntityPlayerMP> players = server.getPlayerList().getPlayers();
        c.playersOnline = players.size();
        for (EntityPlayerMP p : players) {
            activity.update(p);
            if (p.dimension != 0) {
                c.status.put(p.getUniqueID(), Status.ELSEWHERE);
                continue;
            }
            boolean inBed = p.isPlayerSleeping();
            if (inBed) {
                c.inBed.add(p);
            }
            if (p.isSpectator() || p.isCreative()) {
                c.status.put(p.getUniqueID(), inBed ? Status.IN_BED : Status.AWAKE);
                continue;
            }
            if (activity.isAfk(p)) {
                c.afk++;
                c.status.put(p.getUniqueID(), Status.AFK);
                continue;
            }
            c.counted.add(p);
            if (p.isPlayerFullyAsleep() && activity.wasActiveBeforeBed(p)) {
                c.sleepers.add(p);
                c.status.put(p.getUniqueID(), Status.SLEEPING);
            } else {
                c.status.put(p.getUniqueID(), inBed ? Status.IN_BED : Status.AWAKE);
            }
        }
        return c;
    }

    // ---- night lifecycle ----

    private void updateNight(WorldServer world, UnrestWorldData data, long day, long tod,
                             boolean nightWindow, boolean sleepWindow, boolean active) {
        if (night != null) {
            if (night.storm) {
                if (nightWindow || !sleepWindow) {
                    night = null;
                    data.setMeter(0);
                    resetMeterState();
                }
            } else if (!nightWindow || day != night.day) {
                endNaturally(data, active);
            }
        }
        if (night != null) {
            return;
        }
        if (nightWindow) {
            if (day != concludedDay) {
                beginNight(world, data, day, tod, active);
            }
        } else if (world.isThundering()) {
            night = new Night(NightType.NORMAL, day, true, data.getDebt());
            data.setMeter(0);
            resetMeterState();
        }
    }

    private void beginNight(WorldServer world, UnrestWorldData data, long day, long tod, boolean active) {
        NightType saved = data.getNightType(day);
        NightType type = saved != null ? saved : NightRoller.roll(data.getDebt(), rand);
        night = new Night(type, day, false, data.getDebt());
        resetMeterState();
        if (saved != null) {
            // Server restarted mid-night: keep the type and the meter.
            night.seconds = (int) ((tod - NIGHT_START) / 20);
            return;
        }
        data.setNight(type, day);
        data.setMeter(0);
        if (active && UnrestConfig.chat) {
            ChatDisplay.announceNight(world, type);
        }
    }

    private void endNaturally(UnrestWorldData data, boolean active) {
        Night n = night;
        double after = data.getDebt();
        if (active) {
            after = SleepMath.debtAfterNaturalNight(after);
            data.setDebt(after);
        }
        NightLog.Entry e = baseEntry(n, Outcome.NATURAL, after);
        e.sleepersAtSkip = census.sleepers.size();
        e.ratioAtSkip = census.ratio();
        log.write(e);
        concludedDay = n.day;
        night = null;
        data.setMeter(0);
        resetMeterState();
    }

    private void resetMeterState() {
        wholeServerCount = 0;
        secondsUntilRoll = 0;
        chimed50 = false;
        chimed90 = false;
        actionbarTimer = 0;
        particleTimer = 0;
        dreams.clear();
    }

    // ---- sleepers shown in chat and the tab list ----

    private void updateShownSleepers(WorldServer world, boolean show) {
        Set<UUID> now = new HashSet<>();
        if (show) {
            for (EntityPlayerMP p : census.sleepers) {
                now.add(p.getUniqueID());
            }
        }
        Set<UUID> online = new HashSet<>();
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            online.add(p.getUniqueID());
        }
        boolean joined = !lastOnline.containsAll(online);
        lastOnline = online;
        if (now.equals(shownSleepers)) {
            quietWake.clear();
            if (joined) {
                // Someone new joined: their client hasn't seen the tags yet.
                refreshTabList(shownSleepers);
            }
            return;
        }
        Set<UUID> changed = new HashSet<>();
        int s = census.sleepers.size();
        int n = census.counted.size();
        for (EntityPlayerMP p : census.sleepers) {
            UUID id = p.getUniqueID();
            if (now.contains(id) && !shownSleepers.contains(id)) {
                changed.add(id);
                if (UnrestConfig.chat) {
                    ChatDisplay.liesDown(world, p, s, n, census, secondsToDawn(world));
                }
            }
        }
        for (UUID id : shownSleepers) {
            if (!now.contains(id)) {
                changed.add(id);
                EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
                if (show && p != null && UnrestConfig.chat && !quietWake.contains(id) && p.dimension == 0) {
                    ChatDisplay.getsUp(world, p, s, n);
                }
            }
        }
        quietWake.clear();
        shownSleepers = now;
        if (joined) {
            changed.addAll(now);
        }
        refreshTabList(changed);
    }

    private void refreshTabList(Set<UUID> ids) {
        List<EntityPlayerMP> players = new ArrayList<>();
        for (UUID id : ids) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null) {
                players.add(p);
            }
        }
        TabListDisplay.update(server, players, shownSleepers);
    }

    // ---- the meter ----

    private boolean wholeServerAllowed() {
        return night == null || night.type != NightType.HAUNTED || UnrestConfig.hauntedAllowsWholeServerSleep;
    }

    private void runMeter(WorldServer world, UnrestWorldData data) {
        int n = census.counted.size();
        int s = census.sleepers.size();
        if (n == 0) {
            // Nobody counts: the meter is paused.
            showDisplays(world, (float) data.getMeter());
            return;
        }
        double r = (double) s / n;

        if (s == n && wholeServerAllowed()) {
            dreams.clear();
            wholeServerCount++;
            if (wholeServerCount >= Math.ceil(UnrestConfig.wholeServerSeconds)) {
                skip(world, data, Outcome.WHOLE_SERVER);
                return;
            }
            float countdown = (float) (wholeServerCount / Math.max(1.0, Math.ceil(UnrestConfig.wholeServerSeconds)));
            showDisplays(world, Math.max((float) data.getMeter(), countdown));
            return;
        }
        wholeServerCount = 0;

        if (s == 0) {
            secondsUntilRoll = 0;
        } else if (UnrestConfig.dreamsEnabled) {
            if (secondsUntilRoll <= 0) {
                secondsUntilRoll = Math.max(1, UnrestConfig.rollIntervalSeconds);
                for (EntityPlayerMP p : dreams.roll(census.sleepers, r, data.getDebt(), rand)) {
                    nightmare(world, data, p);
                }
            }
            secondsUntilRoll--;
        }

        double rEff = Math.min(1.0, dreams.weight(census.sleepers) / n);
        boolean blocked = night.type == NightType.HAUNTED && r < UnrestConfig.hauntedMinRatio;
        double meter = data.getMeter();
        if (rEff > 0 && !blocked) {
            meter += SleepMath.meterStep(rEff, night.type.multiplier(), UnrestConfig.presetScale(presetName(world)));
        } else {
            meter -= UnrestConfig.drainPerSecond;
        }
        meter = Math.max(0.0, Math.min(1.0, meter));
        data.setMeter(meter);

        if (UnrestConfig.sounds) {
            if (!chimed50 && meter >= 0.5) {
                chimed50 = true;
                ParticleDisplay.chime(world, 1.0f);
            }
            if (!chimed90 && meter >= 0.9) {
                chimed90 = true;
                ParticleDisplay.chime(world, 1.5f);
            }
        }
        if (meter < 0.5) {
            chimed50 = false;
        }
        if (meter < 0.9) {
            chimed90 = false;
        }

        if (meter >= 1.0) {
            skip(world, data, Outcome.PARTIAL);
            return;
        }
        showDisplays(world, (float) meter);
    }

    private void nightmare(WorldServer world, UnrestWorldData data, EntityPlayerMP p) {
        p.wakeUpPlayer(true, true, false);
        data.setMeter(Math.max(0.0, data.getMeter() - UnrestConfig.nightmareMeterLoss));
        night.nightmares++;
        quietWake.add(p.getUniqueID());
        if (UnrestConfig.chat) {
            ChatDisplay.nightmare(world, p);
        }
    }

    private void showDisplays(WorldServer world, float progress) {
        boolean visible = UnrestConfig.bossbar && (!census.inBed.isEmpty() || UnrestConfig.bossbarAlwaysAtNight);
        if (visible) {
            bossbar.show(census.counted, night.type, census.sleepers, progress);
        } else {
            bossbar.hide();
        }
        if (census.sleepers.isEmpty()) {
            actionbarTimer = 0;
            particleTimer = 0;
            return;
        }
        if (UnrestConfig.actionbar && actionbarTimer-- <= 0) {
            actionbarTimer = Math.max(1, UnrestConfig.actionbarIntervalSeconds) - 1;
            ActionbarDisplay.nudge(census.counted, census.sleepers);
        }
        if (UnrestConfig.particles && particleTimer-- <= 0) {
            particleTimer = 1;
            ParticleDisplay.puffs(world, census.sleepers);
        }
    }

    // ---- skipping ----

    private void skip(WorldServer world, UnrestWorldData data, Outcome outcome) {
        List<EntityPlayerMP> sleepers = new ArrayList<>(census.sleepers);
        int n = census.counted.size();
        long ticks = SkipHandler.jumpToMorning(world);
        finish(world, data, outcome, ticks, sleepers, n);
    }

    /** Vanilla skipped on its own because every player in the Overworld was asleep (§2.4). */
    private void onVanillaSkip(WorldServer world, long ticks, List<EntityPlayerMP> sleepers) {
        UnrestWorldData data = UnrestWorldData.get(world);
        if (night == null) {
            long day = timeAtTickStart / 24000L;
            NightType type = data.getNightType(day);
            night = new Night(type == null ? NightType.NORMAL : type, day, type == null, data.getDebt());
        }
        Outcome outcome = UnrestConfig.mode == Mode.OBSERVE ? Outcome.VANILLA_SKIP : Outcome.WHOLE_SERVER;
        int n = Math.max(census.counted.size(), sleepers.size());
        finish(world, data, outcome, ticks, sleepers, n);
    }

    private void finish(WorldServer world, UnrestWorldData data, Outcome outcome, long ticks,
                        List<EntityPlayerMP> sleepers, int n) {
        Night done = night;
        boolean active = UnrestConfig.mode == Mode.ACTIVE;
        int s = sleepers.size();
        double r = n > 0 ? Math.min(1.0, (double) s / n) : 1.0;

        int hungerLost = 0;
        double after = data.getDebt();
        if (active) {
            hungerLost = SkipHandler.applyHunger(sleepers, ticks, rand);
            after = SleepMath.debtAfterSkip(after, r, outcome == Outcome.WHOLE_SERVER);
            data.setDebt(after);
        }

        NightLog.Entry e = baseEntry(done, outcome, after);
        e.secondsToSkip = done.seconds;
        e.countedN = n;
        e.sleepersAtSkip = s;
        e.ratioAtSkip = r;
        e.peakSleepers = Math.max(done.peakSleepers, s);
        e.hungerLostTotal = hungerLost;
        log.write(e);

        if (active && UnrestConfig.chat) {
            ChatDisplay.skipSummary(world, s, n, done.type, done.seconds);
        }

        if (!done.storm) {
            concludedDay = done.day;
        }
        night = null;
        data.setMeter(0);
        resetMeterState();
        bossbar.hide();
        Set<UUID> old = shownSleepers;
        shownSleepers = new HashSet<>();
        refreshTabList(old);
    }

    private NightLog.Entry baseEntry(Night n, Outcome outcome, double debtAfter) {
        NightLog.Entry e = new NightLog.Entry();
        e.mode = UnrestConfig.mode.id();
        e.day = n.day;
        e.nightType = n.type.id();
        e.debtBefore = n.debtBefore;
        e.debtAfter = debtAfter;
        e.outcome = outcome.id;
        e.playersOnline = census.playersOnline;
        e.countedN = census.counted.size();
        e.afkCount = census.afk;
        e.peakSleepers = n.peakSleepers;
        e.nightmares = n.nightmares;
        return e;
    }
}
