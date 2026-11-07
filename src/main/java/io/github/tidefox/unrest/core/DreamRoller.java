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

    private final Map<UUID, Dream> dreams = new HashMap<>();

    /** Rolls once for every sleeper and returns the ones who had a Nightmare. */
    public List<EntityPlayerMP> roll(List<EntityPlayerMP> sleepers, double r, double debt, Random rand) {
        dreams.clear();
        List<EntityPlayerMP> woken = new ArrayList<>();
        if (!UnrestConfig.dreamsEnabled) {
            return woken;
        }
        for (EntityPlayerMP p : sleepers) {
            Dream d = SleepMath.rollDream(r, debt, rand);
            dreams.put(p.getUniqueID(), d);
            if (d == Dream.NIGHTMARE) {
                woken.add(p);
            }
        }
        return woken;
    }

    /** The current dream of a sleeper. Sleepers who haven't rolled yet sleep normally. */
    public Dream dreamOf(UUID id) {
        if (!UnrestConfig.dreamsEnabled) {
            return Dream.NORMAL;
        }
        Dream d = dreams.get(id);
        return d == null ? Dream.NORMAL : d;
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
