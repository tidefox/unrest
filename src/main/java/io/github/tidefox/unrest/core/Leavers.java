package io.github.tidefox.unrest.core;

import io.github.tidefox.unrest.config.UnrestConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Anti-dodge (DESIGN.md §6.1). An awake, counted player who logs out or leaves
 * the Overworld keeps counting as awake until they would have gone AFK had
 * they stayed: {@code graceSeconds} after their last activity. Leaving the
 * server is then never better than standing still. Free of Minecraft classes
 * so it can be unit tested.
 */
public final class Leavers {

    public enum Reason {
        LOGGED_OUT, OTHER_DIMENSION
    }

    /** An awake, counted player as seen in one census. */
    public static final class Awake {
        final String name;
        final long lastActiveMillis;

        public Awake(String name, long lastActiveMillis) {
            this.name = name;
            this.lastActiveMillis = lastActiveMillis;
        }
    }

    /** A player who left and still counts as awake. */
    public static final class Leaver {
        public final String name;
        public final Reason reason;
        public final long untilMillis;

        Leaver(String name, Reason reason, long untilMillis) {
            this.name = name;
            this.reason = reason;
            this.untilMillis = untilMillis;
        }

        public long secondsLeft(long now) {
            return Math.max(0, (untilMillis - now + 999) / 1000);
        }
    }

    private final Map<UUID, Leaver> leavers = new LinkedHashMap<>();
    private Map<UUID, Awake> lastAwake = new HashMap<>();

    /**
     * Called once per census.
     *
     * @param awakeNow     counted players who are awake right now
     * @param online       everyone online
     * @param inOverworld  everyone online and in the Overworld, in any state
     */
    public void update(long now, Map<UUID, Awake> awakeNow, Set<UUID> online, Set<UUID> inOverworld) {
        long grace = Math.max(0, UnrestConfig.leaverGraceSeconds) * 1000L;
        for (Map.Entry<UUID, Awake> e : lastAwake.entrySet()) {
            UUID id = e.getKey();
            if (awakeNow.containsKey(id)) {
                continue;
            }
            Reason reason = null;
            if (!online.contains(id)) {
                reason = UnrestConfig.countLeavers ? Reason.LOGGED_OUT : null;
            } else if (!inOverworld.contains(id)) {
                reason = UnrestConfig.countDimensionLeavers ? Reason.OTHER_DIMENSION : null;
            }
            // Anyone still in the Overworld went to bed, went AFK or changed game mode: not a dodge.
            long until = e.getValue().lastActiveMillis + grace;
            if (reason != null && until > now) {
                leavers.put(id, new Leaver(e.getValue().name, reason, until));
            }
        }
        for (Iterator<Map.Entry<UUID, Leaver>> it = leavers.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Leaver> e = it.next();
            Leaver l = e.getValue();
            boolean disabled = l.reason == Reason.LOGGED_OUT ? !UnrestConfig.countLeavers : !UnrestConfig.countDimensionLeavers;
            // Back in the Overworld: the census counts them normally again.
            if (disabled || inOverworld.contains(e.getKey()) || l.untilMillis <= now) {
                it.remove();
            }
        }
        lastAwake = new HashMap<>(awakeNow);
    }

    public int count() {
        return leavers.size();
    }

    public List<Leaver> list() {
        return new ArrayList<>(leavers.values());
    }

    public void clear() {
        leavers.clear();
        lastAwake.clear();
    }
}
