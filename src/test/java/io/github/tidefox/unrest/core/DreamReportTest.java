package io.github.tidefox.unrest.core;

import io.github.tidefox.unrest.config.UnrestConfig.DreamAnnounce;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The chat rules for the shared dream roll (DESIGN.md §4.1). Uses the default config values. */
public class DreamReportTest {

    private static final List<String> STEVE = Collections.singletonList("Steve");
    private static final List<String> TWO = Arrays.asList("Steve", "Alex");

    @Test
    public void changesModeSkipsRepeatsAndFirstNormal() {
        DreamAnnounce m = DreamAnnounce.CHANGES;
        assertFalse(DreamReport.announce(null, Dream.NORMAL, m));
        assertTrue(DreamReport.announce(null, Dream.DEEP, m));
        assertTrue(DreamReport.announce(null, Dream.RESTLESS, m));
        assertFalse(DreamReport.announce(Dream.DEEP, Dream.DEEP, m));
        assertTrue(DreamReport.announce(Dream.RESTLESS, Dream.NORMAL, m));
        assertTrue(DreamReport.announce(Dream.DEEP, Dream.RESTLESS, m));
    }

    @Test
    public void nightmaresAreAlwaysAnnounced() {
        for (DreamAnnounce m : DreamAnnounce.values()) {
            assertTrue(DreamReport.announce(null, Dream.NIGHTMARE, m));
            assertTrue(DreamReport.announce(Dream.NORMAL, Dream.NIGHTMARE, m));
        }
    }

    @Test
    public void allModeAnnouncesEveryRoll() {
        assertTrue(DreamReport.announce(null, Dream.NORMAL, DreamAnnounce.ALL));
        assertTrue(DreamReport.announce(Dream.DEEP, Dream.DEEP, DreamAnnounce.ALL));
    }

    @Test
    public void offModeIsSilentButForNightmares() {
        for (Dream d : Dream.values()) {
            if (d != Dream.NIGHTMARE) {
                assertFalse(DreamReport.announce(null, d, DreamAnnounce.OFF));
                assertFalse(DreamReport.announce(Dream.RESTLESS, d, DreamAnnounce.OFF));
            }
        }
    }

    @Test
    public void namesReadNaturally() {
        assertEquals("Steve", DreamReport.names(STEVE));
        assertEquals("Steve and Alex", DreamReport.names(TWO));
        assertEquals("Steve, Alex and Bob", DreamReport.names(Arrays.asList("Steve", "Alex", "Bob")));
        assertEquals("Steve, Alex and 3 others", DreamReport.names(Arrays.asList("Steve", "Alex", "a", "b", "c")));
    }

    @Test
    public void phrasesFollowThePreviousDreamAndCount() {
        assertEquals("Steve sinks into a deep sleep", DreamReport.phrase(STEVE, null, Dream.DEEP, null));
        assertEquals("Steve and Alex sink into a deep sleep together", DreamReport.phrase(TWO, null, Dream.DEEP, null));
        assertEquals("Steve and Alex stay deeply asleep", DreamReport.phrase(TWO, Dream.DEEP, Dream.DEEP, null));
        assertEquals("Steve tosses and turns", DreamReport.phrase(STEVE, Dream.NORMAL, Dream.RESTLESS, null));
        assertEquals("Steve and Alex keep tossing and turning",
                DreamReport.phrase(TWO, Dream.RESTLESS, Dream.RESTLESS, null));
        assertEquals("Steve and Alex settle down", DreamReport.phrase(TWO, Dream.RESTLESS, Dream.NORMAL, null));
        assertEquals("Steve stirs a little", DreamReport.phrase(STEVE, Dream.DEEP, Dream.NORMAL, null));
        assertEquals("Steve sleeps soundly", DreamReport.phrase(STEVE, null, Dream.NORMAL, null));
        assertEquals("A nightmare grips Steve and Alex! Alex wakes up in a cold sweat",
                DreamReport.phrase(TWO, Dream.NORMAL, Dream.NIGHTMARE, "Alex"));
    }

    @Test
    public void luckTag() {
        assertNull(DreamReport.luckTag(0));
        assertNull(DreamReport.luckTag(0.2));
        assertEquals("luck +2", DreamReport.luckTag(2));
        assertEquals("luck \u22121", DreamReport.luckTag(-1));
    }

    @Test
    public void oddsAddUpTo100AndMatchTheFormulas() {
        // Half the server asleep, no Unrest, no Luck: nightmare 3%, restless 20%, deep 20%.
        int[] o = DreamReport.oddsPercent(0.5, 0);
        assertEquals(3, o[0]);
        assertEquals(20, o[1]);
        assertEquals(20, o[2]);
        for (double r = 0.05; r <= 1.0; r += 0.05) {
            for (double d = 0; d <= 5.0; d += 0.5) {
                for (double l = -5; l <= 5; l += 1) {
                    int[] x = DreamReport.oddsPercent(r, d, l);
                    int sum = x[0] + x[1] + x[2] + x[3];
                    assertTrue("sum " + sum + " at r=" + r + " d=" + d + " l=" + l, sum >= 98 && sum <= 102);
                }
            }
        }
    }

    @Test
    public void announceIdsParse() {
        assertEquals(DreamAnnounce.ALL, DreamAnnounce.fromId("all"));
        assertEquals(DreamAnnounce.OFF, DreamAnnounce.fromId("OFF"));
        assertEquals(DreamAnnounce.CHANGES, DreamAnnounce.fromId("nonsense"));
    }
}
