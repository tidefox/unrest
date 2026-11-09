package io.github.tidefox.unrest.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What awake players have added to the meter tonight by keeping watch
 * (DESIGN.md §4.2). Free of Minecraft classes so it can be unit tested.
 */
public final class NightWatch {

    private final Map<UUID, Double> earned = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, Long> lastChunk = new HashMap<>();
    private final Map<UUID, Set<Long>> visited = new HashMap<>();
    private final Map<UUID, Double> recent = new LinkedHashMap<>();
    private double pending;

    /**
     * Credits a deed worth {@code amount}, up to the per-player cap. Returns
     * what was actually added.
     */
    public double credit(UUID id, String name, double amount) {
        double before = earned(id);
        double add = SleepMath.watchCredit(before, amount);
        if (add <= 0) {
            return 0.0;
        }
        earned.put(id, before + add);
        names.put(id, name);
        recent.merge(id, add, Double::sum);
        pending += add;
        return add;
    }

    /**
     * Notes the chunk a player is standing in. Returns true the first time
     * they step into a chunk tonight, but not for the chunk they were in when
     * the watch first saw them, so standing still earns nothing.
     */
    public boolean enterChunk(UUID id, int chunkX, int chunkZ) {
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        Set<Long> seen = visited.computeIfAbsent(id, k -> new HashSet<>());
        Long prev = lastChunk.put(id, key);
        boolean fresh = seen.add(key);
        return fresh && prev != null;
    }

    /** Meter added since the last call. */
    public double takePending() {
        double p = pending;
        pending = 0;
        return p;
    }

    /** Who added how much since the last call, for the actionbar. */
    public Map<UUID, Double> takeRecent() {
        if (recent.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, Double> r = new LinkedHashMap<>(recent);
        recent.clear();
        return r;
    }

    public double earned(UUID id) {
        Double e = earned.get(id);
        return e == null ? 0.0 : e;
    }

    public double total() {
        double t = 0;
        for (double e : earned.values()) {
            t += e;
        }
        return t;
    }

    /** "Alex +12%, Steve +4%", biggest first, or an empty list if nobody helped. */
    public List<String> summary() {
        List<Map.Entry<UUID, Double>> list = new ArrayList<>(earned.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<String> out = new ArrayList<>();
        for (Map.Entry<UUID, Double> e : list) {
            long pct = Math.round(e.getValue() * 100);
            if (pct > 0) {
                out.add(names.get(e.getKey()) + " +" + pct + "%");
            }
        }
        return out;
    }

    /** Forgets the night. Called at the start of each night and after a skip. */
    public void clear() {
        earned.clear();
        names.clear();
        lastChunk.clear();
        visited.clear();
        recent.clear();
        pending = 0;
    }
}
