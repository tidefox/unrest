package io.github.tidefox.unrest.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Tension, Deep chains and the Night terror (DESIGN.md §4.3). Uses the default config values. */
public class MomentumTest {

    private static final double EPS = 1e-9;

    @Test
    public void badRollsStoreTensionAndDeepReleasesIt() {
        Momentum m = new Momentum();
        m.next(Dream.RESTLESS, false);
        m.next(Dream.NIGHTMARE, false);
        assertEquals(2.0, m.tension(), EPS);
        Momentum.Step st = m.next(Dream.DEEP, false);
        assertEquals(2.0, st.released, EPS);
        assertEquals(0.10, Momentum.burst(st.released), EPS);
        assertEquals(0.0, m.tension(), EPS);
    }

    @Test
    public void tensionIsCappedAndFeverDoublesIt() {
        Momentum m = new Momentum();
        m.next(Dream.NIGHTMARE, true);
        assertEquals(2.0, m.tension(), EPS);
        for (int i = 0; i < 10; i++) {
            m.next(Dream.NIGHTMARE, false);
        }
        assertEquals(5.0, m.tension(), EPS);
    }

    @Test
    public void normalRollsKeepTension() {
        Momentum m = new Momentum();
        m.next(Dream.RESTLESS, false);
        m.next(Dream.NORMAL, false);
        assertEquals(1.0, m.tension(), EPS);
    }

    @Test
    public void deepChainsEscalateAndCap() {
        Momentum m = new Momentum();
        m.next(Dream.DEEP, false);
        assertEquals(2.0, m.weight(Dream.DEEP, false), EPS);
        m.next(Dream.DEEP, false);
        assertEquals(3.0, m.weight(Dream.DEEP, false), EPS);
        Momentum.Step st = m.next(Dream.DEEP, false);
        assertEquals(3, st.chain);
        assertEquals(4.0, m.weight(Dream.DEEP, false), EPS);
        m.next(Dream.DEEP, false);
        assertEquals(4.0, m.weight(Dream.DEEP, false), EPS);
        m.next(Dream.NORMAL, false);
        assertEquals(0, m.chain());
    }

    @Test
    public void threeRestlessInARowBecomeANightTerrorThenDeep() {
        Momentum m = new Momentum();
        assertFalse(m.next(Dream.RESTLESS, false).nightTerror);
        assertFalse(m.next(Dream.RESTLESS, false).nightTerror);
        Momentum.Step terror = m.next(Dream.RESTLESS, false);
        assertTrue(terror.nightTerror);
        assertEquals(Dream.NIGHTMARE, terror.dream);
        Momentum.Step after = m.next(Dream.NIGHTMARE, false);
        assertTrue(after.forced);
        assertEquals(Dream.DEEP, after.dream);
        // Three bad rolls stored three tension, released by the forced Deep.
        assertEquals(3.0, after.released, EPS);
    }

    @Test
    public void feverMakesEveryEffectStronger() {
        Momentum m = new Momentum();
        assertEquals(1.0, m.weight(Dream.NORMAL, true), EPS);
        assertEquals(0.0, m.weight(Dream.RESTLESS, true), EPS);
        m.next(Dream.DEEP, true);
        assertEquals(4.0, m.weight(Dream.DEEP, true), EPS);
        m.next(Dream.DEEP, true);
        assertEquals(6.0, m.weight(Dream.DEEP, true), EPS);
    }
}
