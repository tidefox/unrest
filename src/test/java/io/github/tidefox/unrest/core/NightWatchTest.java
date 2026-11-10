package io.github.tidefox.unrest.core;

import io.github.tidefox.unrest.config.UnrestConfig;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The night watch: per-player cap, exploring and the summary (DESIGN.md §4.2). */
public class NightWatchTest {

    private static final double EPS = 1e-9;
    private final UUID alex = new UUID(0, 1);
    private final UUID steve = new UUID(0, 2);

    @Test
    public void creditsAreCappedPerPlayer() {
        NightWatch w = new NightWatch();
        double cap = UnrestConfig.watchMaxPerPlayer;
        double total = 0;
        for (int i = 0; i < 100; i++) {
            total += w.credit(alex, "Alex", 0.03);
        }
        assertEquals(cap, total, EPS);
        assertEquals(cap, w.earned(alex), EPS);
        assertEquals(0.0, w.credit(alex, "Alex", 0.03), EPS);
        assertEquals(0.03, w.credit(steve, "Steve", 0.03), EPS);
        assertEquals(cap + 0.03, w.takePending(), EPS);
        assertEquals(0.0, w.takePending(), EPS);
    }

    @Test
    public void onlyNewChunksAfterTheFirstCount() {
        NightWatch w = new NightWatch();
        assertFalse("standing where you started earns nothing", w.enterChunk(alex, 0, 0));
        assertFalse(w.enterChunk(alex, 0, 0));
        assertTrue(w.enterChunk(alex, 1, 0));
        assertFalse("going back doesn't count", w.enterChunk(alex, 0, 0));
        assertFalse(w.enterChunk(alex, 1, 0));
        assertTrue(w.enterChunk(alex, -1, -7));
    }

    @Test
    public void recentIsTakenOnce() {
        NightWatch w = new NightWatch();
        w.credit(alex, "Alex", 0.01);
        w.credit(alex, "Alex", 0.02);
        Map<UUID, Double> r = w.takeRecent();
        assertEquals(0.03, r.get(alex), EPS);
        assertTrue(w.takeRecent().isEmpty());
    }

    @Test
    public void summaryIsBiggestFirstAndClearForgets() {
        NightWatch w = new NightWatch();
        w.credit(alex, "Alex", 0.04);
        w.credit(steve, "Steve", 0.12);
        assertEquals(Arrays.asList("Steve +12%", "Alex +4%"), w.summary());
        assertEquals(0.16, w.total(), EPS);
        w.clear();
        assertTrue(w.summary().isEmpty());
        assertEquals(0.0, w.total(), EPS);
    }
}
