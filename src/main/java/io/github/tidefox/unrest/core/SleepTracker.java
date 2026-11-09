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
import io.github.tidefox.unrest.Unrest;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;

import java.lang.reflect.Field;
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
        public int deepDreams;
        public int restlessDreams;

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
        /** Awake players who left the server or the Overworld and still count (§6.1). */
        public final List<String> leavers = new ArrayList<>();
        public int playersOnline;
        public int afk;

        /** N: counted players, plus leavers who still count as awake. */
        public int n() {
            return counted.size() + leavers.size();
        }

        public double ratio() {
            int n = n();
            return n == 0 ? 0.0 : (double) sleepers.size() / n;
        }
    }

    private final MinecraftServer server;
    private final ActivityTracker activity;
    private final NightLog log;
    private final DreamRoller dreams = new DreamRoller();
    private final Leavers leavers = new Leavers();
    private final NightWatch watch = new NightWatch();
    /** Players who were just shown what their watch added, and until when (epoch millis). */
    private final Map<UUID, Long> watchShownUntil = new HashMap<>();
    /** Seconds someone has been asleep tonight, for the ramp (§2.2). */
    private double rampSeconds;
    private Field allPlayersSleeping;
    private boolean vanillaBlockFailed;
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
    private boolean everyoneAsleepAtTickStart;
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

    /** The sleepers' shared dream, or null if they haven't rolled yet. */
    public Dream dreamOf(UUID id) {
        return dreams.hasRolled() ? dreams.current() : null;
    }

    public NightWatch watch() {
        return watch;
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
        int n = census.n();
        int s = census.sleepers.size();
        if (n == 0) {
            return natural;
        }
        if (s == n && wholeServerAllowed()) {
            return Math.max(0, Math.ceil(UnrestConfig.wholeServerSeconds) - wholeServerCount);
        }
        if (s == 0) {
            return natural;
        }
        double factor = SleepMath.nightFactor(night.type, (double) s / n);
        double rEff = Math.min(1.0, dreams.weight(census.sleepers) / n);
        double meter = SleepMath.secondsToFull(data().getMeter(), rEff, factor,
                UnrestConfig.presetScale(presetName(world)), rampSeconds);
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
            blockVanillaSkipIfDodged(world);
            asleepAtTickStart = fullyAsleep(world);
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
            // Recognise vanilla's skip by what it does, not by vanilla's own rule,
            // because mods such as Quark change who has to be asleep: the sleepers
            // are woken inside the world tick and the time lands on the next
            // morning (time of day 0, then +1 for the tick). Wake-ups that end
            // elsewhere, such as a Comforts hammock nap at dusk, don't count.
            // With doDaylightCycle off the time doesn't move, so only vanilla's own
            // everyone-asleep case can be told apart from someone leaving bed.
            long now = world.getWorldTime();
            boolean skipped = world.getGameRules().getBoolean("doDaylightCycle")
                    ? now % 24000L <= 1 && now - timeAtTickStart > 1
                    : everyoneAsleepAtTickStart;
            if (allWoken && skipped && UnrestConfig.enabled) {
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
     * While a leaver still counts as awake, the night isn't a whole-server sleep,
     * but vanilla only sees who is left in the world and would skip at once. Its
     * "everyone is asleep" flag is cleared just before the world ticks, which is
     * the only place vanilla reads it. If the field can't be found, the vanilla
     * skip still happens but is counted as a partial skip (§2.4).
     */
    private void blockVanillaSkipIfDodged(WorldServer world) {
        if (vanillaBlockFailed || census.leavers.isEmpty() || night == null
                || !UnrestConfig.enabled || UnrestConfig.mode != Mode.ACTIVE) {
            return;
        }
        try {
            if (allPlayersSleeping == null) {
                allPlayersSleeping = ReflectionHelper.findField(WorldServer.class, "allPlayersSleeping", "field_73068_P");
            }
            allPlayersSleeping.setBoolean(world, false);
        } catch (Exception | LinkageError ex) {
            vanillaBlockFailed = true;
            Unrest.LOG.warn("Unrest can't stop vanilla's skip while leavers count as awake; such skips count as partial.", ex);
        }
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

    /** Non-spectators in the Overworld who are fully asleep. Also notes whether that is everyone. */
    private List<EntityPlayerMP> fullyAsleep(WorldServer world) {
        List<EntityPlayerMP> asleep = new ArrayList<>();
        boolean everyone = true;
        for (EntityPlayer p : world.playerEntities) {
            if (p.isSpectator()) {
                continue;
            }
            if (p.isPlayerFullyAsleep() && p instanceof EntityPlayerMP) {
                asleep.add((EntityPlayerMP) p);
            } else {
                everyone = false;
            }
        }
        everyoneAsleepAtTickStart = everyone && !asleep.isEmpty();
        return asleep.isEmpty() ? Collections.<EntityPlayerMP>emptyList() : asleep;
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
        Set<UUID> online = new HashSet<>();
        Set<UUID> inOverworld = new HashSet<>();
        for (EntityPlayerMP p : players) {
            activity.update(p);
            online.add(p.getUniqueID());
            if (p.dimension != 0) {
                c.status.put(p.getUniqueID(), Status.ELSEWHERE);
                continue;
            }
            inOverworld.add(p.getUniqueID());
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
        Map<UUID, Leavers.Awake> awake = new HashMap<>();
        for (EntityPlayerMP p : c.counted) {
            if (!c.sleepers.contains(p)) {
                awake.put(p.getUniqueID(), new Leavers.Awake(p.getName(), activity.lastActiveMillis(p)));
            }
        }
        long now = System.currentTimeMillis();
        leavers.update(now, awake, online, inOverworld);
        // Leavers only change the count in active mode; observe mode logs vanilla as it is.
        if (UnrestConfig.mode == Mode.ACTIVE) {
            for (Leavers.Leaver l : leavers.list()) {
                c.leavers.add(l.name + " (" + SleepMath.formatDuration(l.secondsLeft(now)) + ")");
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
        if (active && UnrestConfig.chat && UnrestConfig.announceNight) {
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
        rampSeconds = 0;
        dreams.clear();
        watch.clear();
        watchShownUntil.clear();
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
        int n = census.n();
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
        int n = census.n();
        int s = census.sleepers.size();
        if (n == 0) {
            // Nobody counts: the meter is paused.
            showDisplays(world, (float) data.getMeter());
            return;
        }
        double r = (double) s / n;
        trackExploring();
        double watched = watch.takePending();

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
                DreamRoller.Roll roll = dreams.roll(census.sleepers, r, data.getDebt(), rand);
                if (roll != null) {
                    if (UnrestConfig.chat) {
                        ChatDisplay.dreams(world, roll, r, data.getDebt());
                    }
                    if (roll.after == Dream.DEEP) {
                        night.deepDreams++;
                    } else if (roll.after == Dream.RESTLESS) {
                        night.restlessDreams++;
                    } else if (roll.after == Dream.NIGHTMARE) {
                        nightmare(world, data, roll.woken);
                    }
                }
            }
            secondsUntilRoll--;
        }

        double rEff = Math.min(1.0, dreams.weight(census.sleepers) / n);
        double factor = SleepMath.nightFactor(night.type, r);
        double meter = data.getMeter();
        double rate = 0.0;
        if (rEff > 0 && factor > 0) {
            rate = SleepMath.meterStep(rEff, factor, UnrestConfig.presetScale(presetName(world)))
                    * SleepMath.ramp(rampSeconds);
            meter += rate;
        } else {
            meter -= UnrestConfig.drainPerSecond;
        }
        // The ramp keeps building while anyone sleeps, even through a restless
        // patch, and winds back down while nobody does.
        rampSeconds = s > 0 ? rampSeconds + 1 : Math.max(0.0, rampSeconds - 1);
        meter += watched;
        // The time limit: the meter never lags behind the share of
        // maxSleepSeconds that someone has been asleep.
        meter = Math.max(meter, SleepMath.meterFloor(rampSeconds));
        meter = Math.max(0.0, Math.min(1.0, meter));
        data.setMeter(meter);
        showWatchFeedback(world, meter, rate);

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

    /** A Nightmare roll: the meter is set back and the unluckiest sleeper wakes. The chat line says so. */
    private void nightmare(WorldServer world, UnrestWorldData data, EntityPlayerMP p) {
        p.wakeUpPlayer(true, true, false);
        data.setMeter(Math.max(0.0, data.getMeter() - UnrestConfig.nightmareMeterLoss));
        night.nightmares++;
        quietWake.add(p.getUniqueID());
        if (UnrestConfig.sounds && UnrestConfig.nightmareSound) {
            ParticleDisplay.nightmare(p);
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
            List<EntityPlayerMP> nudged = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (EntityPlayerMP p : census.counted) {
                Long until = watchShownUntil.get(p.getUniqueID());
                if (until == null || until < now) {
                    nudged.add(p);
                }
            }
            ActionbarDisplay.nudge(nudged, census.sleepers, watchOpen());
        }
        if (UnrestConfig.particles && particleTimer-- <= 0) {
            particleTimer = 1;
            ParticleDisplay.puffs(world, census.sleepers);
        }
    }

    // ---- skipping ----

    private void skip(WorldServer world, UnrestWorldData data, Outcome outcome) {
        List<EntityPlayerMP> sleepers = new ArrayList<>(census.sleepers);
        int n = census.n();
        long ticks = SkipHandler.jumpToMorning(world);
        finish(world, data, outcome, ticks, sleepers, n);
    }

    /**
     * Vanilla's sleep code skipped the night (§2.4). Without other mods that means
     * every player in the Overworld was asleep. Mods like Quark can make it skip
     * with fewer, so the outcome is worked out from Unrest's own count.
     */
    private void onVanillaSkip(WorldServer world, long ticks, List<EntityPlayerMP> inBed) {
        List<EntityPlayerMP> sleepers = new ArrayList<>();
        for (EntityPlayerMP p : inBed) {
            if (!p.isCreative() && p.dimension == 0 && activity.wasActiveBeforeBed(p)) {
                sleepers.add(p);
            }
        }
        UnrestWorldData data = UnrestWorldData.get(world);
        if (night == null) {
            long day = timeAtTickStart / 24000L;
            NightType type = data.getNightType(day);
            night = new Night(type == null ? NightType.NORMAL : type, day, type == null, data.getDebt());
        }
        int n = Math.max(census.n(), sleepers.size());
        Outcome outcome = UnrestConfig.mode == Mode.OBSERVE ? Outcome.VANILLA_SKIP
                : sleepers.size() >= n ? Outcome.WHOLE_SERVER : Outcome.PARTIAL;
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
            ChatDisplay.skipSummary(world, s, n, done.type, done.seconds,
                    done.deepDreams, done.restlessDreams, done.nightmares, watch.summary());
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

    // ---- night watch (§4.2) ----

    /** Whether keeping watch counts right now: a night in active mode with someone asleep. */
    private boolean watchOpen() {
        return UnrestConfig.enabled && UnrestConfig.watchEnabled && UnrestConfig.mode == Mode.ACTIVE
                && night != null && !census.sleepers.isEmpty();
    }

    /** Whether this player's deeds count: awake, counted and in the Overworld. */
    private boolean onWatch(EntityPlayerMP p) {
        return watchOpen() && p.dimension == 0 && !p.isPlayerSleeping()
                && census.counted.contains(p) && !census.sleepers.contains(p);
    }

    private void trackExploring() {
        if (!watchOpen() || UnrestConfig.watchChunk <= 0) {
            return;
        }
        for (EntityPlayerMP p : census.counted) {
            if (census.sleepers.contains(p) || p.isPlayerSleeping()) {
                continue;
            }
            int cx = (int) Math.floor(p.posX) >> 4;
            int cz = (int) Math.floor(p.posZ) >> 4;
            if (watch.enterChunk(p.getUniqueID(), cx, cz)) {
                watch.credit(p.getUniqueID(), p.getName(), UnrestConfig.watchChunk);
            }
        }
    }

    /**
     * Tells each player who kept watch this second what it took off the wait:
     * the meter they added divided by how fast the sleepers fill it right now.
     */
    private void showWatchFeedback(WorldServer world, double meter, double rate) {
        Map<UUID, Double> recent = watch.takeRecent();
        if (!UnrestConfig.watchFeedback || recent.isEmpty() || meter >= 1.0) {
            return;
        }
        double dawn = secondsToDawn(world);
        long until = System.currentTimeMillis() + 3000;
        for (Map.Entry<UUID, Double> e : recent.entrySet()) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(e.getKey());
            if (p != null) {
                double saved = rate > 0 ? e.getValue() / rate : Double.NaN;
                ActionbarDisplay.watch(p, e.getValue(), saved, watch.earned(e.getKey()), dawn);
                watchShownUntil.put(e.getKey(), until);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onMobDeath(LivingDeathEvent e) {
        if (UnrestConfig.watchKill <= 0 || !(e.getEntityLiving() instanceof IMob)
                || e.getEntityLiving().world.isRemote) {
            return;
        }
        Entity killer = e.getSource().getTrueSource();
        if (killer instanceof EntityPlayerMP && onWatch((EntityPlayerMP) killer)) {
            EntityPlayerMP p = (EntityPlayerMP) killer;
            watch.credit(p.getUniqueID(), p.getName(), UnrestConfig.watchKill);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBroken(BlockEvent.BreakEvent e) {
        if (UnrestConfig.watchBlock <= 0 || !(e.getPlayer() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) e.getPlayer();
        if (p.isCreative() || !onWatch(p)) {
            return;
        }
        IBlockState state = e.getState();
        // Blocks that break at once (grass, flowers, torches) are too easy to farm.
        if (state.getBlockHardness(e.getWorld(), e.getPos()) <= 0) {
            return;
        }
        watch.credit(p.getUniqueID(), p.getName(), UnrestConfig.watchBlock);
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
        e.countedN = census.n();
        e.afkCount = census.afk;
        e.peakSleepers = n.peakSleepers;
        e.nightmares = n.nightmares;
        e.watchTotal = watch.total();
        return e;
    }
}
