package io.github.tidefox.unrest.core;

import io.github.tidefox.unrest.config.UnrestConfig.DreamAnnounce;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The chat rules for dream rolls (DESIGN.md §4.1). Uses the default config values. */
public class DreamReportTest {

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
    public void allModeAnnouncesEveryRollButNightmares() {
        assertTrue(DreamReport.announce(null, Dream.NORMAL, DreamAnnounce.ALL));
        assertTrue(DreamReport.announce(Dream.DEEP, Dream.DEEP, DreamAnnounce.ALL));
        assertFalse(DreamReport.announce(null, Dream.NIGHTMARE, DreamAnnounce.ALL));
        assertFalse(DreamReport.announce(Dream.NORMAL, Dream.NIGHTMARE, DreamAnnounce.CHANGES));
    }

    @Test
    public void offModeIsSilent() {
        for (Dream d : Dream.values()) {
            assertFalse(DreamReport.announce(null, d, DreamAnnounce.OFF));
            assertFalse(DreamReport.announce(Dream.RESTLESS, d, DreamAnnounce.OFF));
        }
    }

    @Test
    public void phrasesFollowThePreviousDream() {
        assertEquals("Steve sinks into a deep sleep", DreamReport.phrase("Steve", null, Dream.DEEP));
        assertEquals("Steve stays deeply asleep", DreamReport.phrase("Steve", Dream.DEEP, Dream.DEEP));
        assertEquals("Steve tosses and turns", DreamReport.phrase("Steve", Dream.NORMAL, Dream.RESTLESS));
        assertEquals("Steve keeps tossing and turning", DreamReport.phrase("Steve", Dream.RESTLESS, Dream.RESTLESS));
        assertEquals("Steve settles down", DreamReport.phrase("Steve", Dream.RESTLESS, Dream.NORMAL));
        assertEquals("Steve stirs a little", DreamReport.phrase("Steve", Dream.DEEP, Dream.NORMAL));
        assertEquals("Steve sleeps soundly", DreamReport.phrase("Steve", null, Dream.NORMAL));
    }

    @Test
    public void oddsAddUpTo100AndMatchTheFormulas() {
        // Half the server asleep, no Unrest: nightmare 1.5%, restless 20%, deep 20%.
        int[] o = DreamReport.oddsPercent(0.5, 0);
        assertEquals(2, o[0]);
        assertEquals(20, o[1]);
        assertEquals(20, o[2]);
        for (double r = 0.05; r <= 1.0; r += 0.05) {
            for (double d = 0; d <= 5.0; d += 0.5) {
                int[] x = DreamReport.oddsPercent(r, d);
                int sum = x[0] + x[1] + x[2] + x[3];
                assertTrue("sum " + sum + " at r=" + r + " d=" + d, sum >= 98 && sum <= 102);
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
