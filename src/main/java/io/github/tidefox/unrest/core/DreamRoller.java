package io.github.tidefox.unrest.core;

import io.github.tidefox.unrest.config.UnrestConfig;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Per-sleeper dream rolls (DESIGN.md §4). */
public final class DreamRoller {

    /** One sleeper's roll: the dream they had before (null if none) and the new one. */
    public static final class Roll {
        public final EntityPlayerMP player;
        public final Dream before;
        public final Dream after;

        Roll(EntityPlayerMP player, Dream before, Dream after) {
            this.player = player;
            this.before = before;
            this.after = after;
        }
    }

    private final Map<UUID, Dream> dreams = new HashMap<>();

    /** Rolls once for every sleeper, in sleeper order. */
    public List<Roll> roll(List<EntityPlayerMP> sleepers, double r, double debt, Random rand) {
        Map<UUID, Dream> previous = new HashMap<>(dreams);
        dreams.clear();
        List<Roll> rolls = new ArrayList<>();
        if (!UnrestConfig.dreamsEnabled) {
            return rolls;
        }
        for (EntityPlayerMP p : sleepers) {
            Dream d = SleepMath.rollDream(r, debt, rand);
            dreams.put(p.getUniqueID(), d);
            rolls.add(new Roll(p, previous.get(p.getUniqueID()), d));
        }
        return rolls;
    }

    /** The current dream of a sleeper. Sleepers who haven't rolled yet sleep normally. */
    public Dream dreamOf(UUID id) {
        if (!UnrestConfig.dreamsEnabled) {
            return Dream.NORMAL;
        }
        Dream d = dreams.get(id);
        return d == null ? Dream.NORMAL : d;
    }

    /** Whether the sleeper has rolled since getting into bed (or since the last reset). */
    public boolean hasRolled(UUID id) {
        return UnrestConfig.dreamsEnabled && dreams.containsKey(id);
    }

    /** Sum of the dream weights of the sleepers. */
    public double weight(List<EntityPlayerMP> sleepers) {
        double w = 0;
        for (EntityPlayerMP p : sleepers) {
            w += dreamOf(p.getUniqueID()).weight();
        }
        return w;
    }

    public void clear() {
        dreams.clear();
    }
}
