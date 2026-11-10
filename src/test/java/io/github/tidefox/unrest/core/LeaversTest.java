package io.github.tidefox.unrest.core;

import io.github.tidefox.unrest.config.UnrestConfig;
import org.junit.After;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

/** Anti-dodge rules (DESIGN.md §6.1). Uses the default grace of 300 s. */
public class LeaversTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final long T0 = 1_000_000L;

    @After
    public void restoreConfig() {
        UnrestConfig.countLeavers = true;
        UnrestConfig.countDimensionLeavers = true;
        UnrestConfig.leaverGraceSeconds = 300;
    }

    private static Map<UUID, Leavers.Awake> awake(UUID id, String name, long lastActive) {
        Map<UUID, Leavers.Awake> m = new HashMap<>();
        m.put(id, new Leavers.Awake(name, lastActive));
        return m;
    }

    private static Set<UUID> set(UUID... ids) {
        Set<UUID> s = new HashSet<>();
        Collections.addAll(s, ids);
        return s;
    }

    private static Map<UUID, Leavers.Awake> none() {
        return Collections.emptyMap();
    }

    @Test
    public void logoutKeepsCountingUntilTheyWouldHaveGoneAfk() {
        Leavers l = new Leavers();
        l.update(T0, awake(A, "Steve", T0 - 60_000), set(A), set(A));
        l.update(T0 + 1000, none(), set(), set());
        assertEquals(1, l.count());
        assertEquals(Leavers.Reason.LOGGED_OUT, l.list().get(0).reason);
        // Last active 60 s before T0, so 240 s of grace remain at T0.
        assertEquals(239, l.list().get(0).secondsLeft(T0 + 1000));
        l.update(T0 + 239_000, none(), set(), set());
        assertEquals(1, l.count());
        l.update(T0 + 240_000, none(), set(), set());
        assertEquals(0, l.count());
    }

    @Test
    public void playerAlreadyNearlyAfkGainsNothingByLeaving() {
        Leavers l = new Leavers();
        l.update(T0, awake(A, "Steve", T0 - 300_000), set(A), set(A));
        l.update(T0 + 1000, none(), set(), set());
        assertEquals(0, l.count());
    }

    @Test
    public void dimensionLeaverCountsAndReturningEndsIt() {
        Leavers l = new Leavers();
        l.update(T0, awake(A, "Steve", T0), set(A), set(A));
        l.update(T0 + 1000, none(), set(A), set());
        assertEquals(1, l.count());
        assertEquals(Leavers.Reason.OTHER_DIMENSION, l.list().get(0).reason);
        l.update(T0 + 2000, awake(A, "Steve", T0 + 2000), set(A), set(A));
        assertEquals(0, l.count());
    }

    @Test
    public void goingToBedOrAfkIsNotADodge() {
        Leavers l = new Leavers();
        Map<UUID, Leavers.Awake> both = awake(A, "Steve", T0);
        both.put(B, new Leavers.Awake("Alex", T0));
        l.update(T0, both, set(A, B), set(A, B));
        // A fell asleep, B went AFK: both still in the Overworld.
        l.update(T0 + 1000, none(), set(A, B), set(A, B));
        assertEquals(0, l.count());
    }

    @Test
    public void reconnectingClearsTheLeaver() {
        Leavers l = new Leavers();
        l.update(T0, awake(A, "Steve", T0), set(A), set(A));
        l.update(T0 + 1000, none(), set(), set());
        assertEquals(1, l.count());
        // Back online but AFK: in the Overworld, so the census handles them.
        l.update(T0 + 5000, none(), set(A), set(A));
        assertEquals(0, l.count());
    }

    @Test
    public void togglesTurnItOff() {
        UnrestConfig.countLeavers = false;
        UnrestConfig.countDimensionLeavers = false;
        Leavers l = new Leavers();
        Map<UUID, Leavers.Awake> both = awake(A, "Steve", T0);
        both.put(B, new Leavers.Awake("Alex", T0));
        l.update(T0, both, set(A, B), set(A, B));
        l.update(T0 + 1000, none(), set(B), set());
        assertEquals(0, l.count());
    }

    @Test
    public void turningTheToggleOffDropsExistingLeavers() {
        Leavers l = new Leavers();
        l.update(T0, awake(A, "Steve", T0), set(A), set(A));
        l.update(T0 + 1000, none(), set(), set());
        assertEquals(1, l.count());
        UnrestConfig.countLeavers = false;
        l.update(T0 + 2000, none(), set(), set());
        assertEquals(0, l.count());
    }
}
