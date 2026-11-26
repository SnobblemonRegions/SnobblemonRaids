package com.cobbleraids.renown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RaidPoolAffixTest {

    private static final float MAX = 1000f;

    @Test
    void lastStandHoldsOneHitAtTheNextMarkThenNeverAgain() {
        RaidPoolAffix affix = new RaidPoolAffix(RenownAffix.LAST_STAND);
        assertEquals(150f, affix.adjustDamage(900f, MAX, 400f), 0.001f); // held at 750
        assertTrue(affix.lastStandFired());
        assertEquals(400f, affix.adjustDamage(750f, MAX, 400f), 0.001f); // spent
    }

    @Test
    void lastStandIgnoresHitsThatDoNotCrossTheMark() {
        RaidPoolAffix affix = new RaidPoolAffix(RenownAffix.LAST_STAND);
        assertEquals(100f, affix.adjustDamage(900f, MAX, 100f), 0.001f); // 800 stays above 750
        assertFalse(affix.lastStandFired());
        assertEquals(150f, affix.adjustDamage(900f, MAX, 150f), 0.001f); // lands exactly on 750
        assertFalse(affix.lastStandFired());
    }

    @Test
    void lastStandUsesTheNextMarkBelowCurrentNotAboveIt() {
        RaidPoolAffix affix = new RaidPoolAffix(RenownAffix.LAST_STAND);
        assertEquals(240f, affix.adjustDamage(740f, MAX, 600f), 0.001f); // 740 -> held at 500
    }

    @Test
    void lastStandNeverProtectsTheLastQuarter() {
        RaidPoolAffix affix = new RaidPoolAffix(RenownAffix.LAST_STAND);
        assertEquals(250f, affix.adjustDamage(250f, MAX, 250f), 0.001f);
        assertEquals(500f, affix.adjustDamage(200f, MAX, 500f), 0.001f); // unchanged; the pool clamps overkill
        assertFalse(affix.lastStandFired());
    }

    @Test
    void secondWindHealsOnceBelowAThirdAndNeverAnEmptyPool() {
        RaidPoolAffix affix = new RaidPoolAffix(RenownAffix.SECOND_WIND);
        assertEquals(0f, affix.healAfterDamage(400f, MAX), 0.001f);
        assertEquals(0f, affix.healAfterDamage(0f, MAX), 0.001f);
        assertEquals(50f, affix.healAfterDamage(300f, MAX), 0.001f);
        assertEquals(0f, affix.healAfterDamage(100f, MAX), 0.001f);
    }

    @Test
    void unbrokenEndsExactlyOnceBelowHalf() {
        RaidPoolAffix affix = new RaidPoolAffix(RenownAffix.UNBROKEN);
        assertFalse(affix.unbrokenEndsNow(500f, MAX));
        assertTrue(affix.unbrokenEndsNow(499f, MAX));
        assertFalse(affix.unbrokenEndsNow(100f, MAX));
    }

    @Test
    void otherAffixesAndNoneChangeNothing() {
        for (RaidPoolAffix affix : new RaidPoolAffix[] {RaidPoolAffix.none(), new RaidPoolAffix(RenownAffix.GRIT)}) {
            assertEquals(400f, affix.adjustDamage(900f, MAX, 400f), 0.001f);
            assertEquals(0f, affix.healAfterDamage(100f, MAX), 0.001f);
            assertFalse(affix.unbrokenEndsNow(100f, MAX));
        }
    }

    @Test
    void zeroMaxOrNonPositiveDamageIsLeftAlone() {
        RaidPoolAffix affix = new RaidPoolAffix(RenownAffix.LAST_STAND);
        assertEquals(10f, affix.adjustDamage(5f, 0f, 10f), 0.001f);
        assertEquals(0f, affix.adjustDamage(900f, MAX, 0f), 0.001f);
        assertEquals(Float.NaN, affix.adjustDamage(900f, MAX, Float.NaN));
        assertFalse(affix.lastStandFired());
    }
}
