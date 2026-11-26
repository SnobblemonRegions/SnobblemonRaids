package com.cobbleraids.duel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RaidHpBarLayoutTest {

    @Test
    void fractionIsClampedAndSafeOnAnEmptyPool() {
        assertEquals(0.62f, RaidHpBarLayout.fraction(2605f, 4201.6f), 0.001f);
        assertEquals(1f, RaidHpBarLayout.fraction(5000f, 4000f));
        assertEquals(0f, RaidHpBarLayout.fraction(-3f, 4000f));
        assertEquals(0f, RaidHpBarLayout.fraction(10f, 0f));
        assertEquals(0f, RaidHpBarLayout.fraction(Float.NaN, 100f));
    }

    @Test
    void aStandingBossNeverReadsZero() {
        assertEquals(1L, RaidHpBarLayout.shownHp(0.2f));
        assertEquals(2605L, RaidHpBarLayout.shownHp(2604.4f));
        assertEquals(0L, RaidHpBarLayout.shownHp(0f));
        assertEquals(0L, RaidHpBarLayout.shownHp(-5f));
    }

    @Test
    void numbersUseThousandsSeparators() {
        assertEquals("2,605 / 4,201", RaidHpBarLayout.hpText(2605, 4201));
        assertEquals("378 / 26,733", RaidHpBarLayout.hpText(378, 26733));
    }

    @Test
    void statusShowsFightersAndAClock() {
        assertEquals("⚔ 3 dueling · 7:42", RaidHpBarLayout.status(3, 462));
        assertEquals("0:05", RaidHpBarLayout.clock(5));
        assertEquals("0:00", RaidHpBarLayout.clock(-20));
        assertEquals("10:00", RaidHpBarLayout.clock(600));
    }

    @Test
    void colourStepsDownWithHp() {
        assertEquals(RaidHpBarLayout.HIGH_COLOR, RaidHpBarLayout.fillColor(0.62f));
        assertEquals(RaidHpBarLayout.MID_COLOR, RaidHpBarLayout.fillColor(0.34f));
        assertEquals(RaidHpBarLayout.LOW_COLOR, RaidHpBarLayout.fillColor(0.09f));
    }

    @Test
    void theFillStretchesTheOnePixelSquareToTheBarWidth() {
        assertEquals(RaidHpBarLayout.WIDTH, RaidHpBarLayout.fillScaleX(1f) * RaidHpBarLayout.PIXEL, 0.0001f);
        assertEquals(RaidHpBarLayout.WIDTH / 2f, RaidHpBarLayout.fillScaleX(0.5f) * RaidHpBarLayout.PIXEL, 0.0001f);
        assertEquals(0f, RaidHpBarLayout.fillScaleX(-1f));
        // Centred on the boss: the left edge is half the width to the left.
        assertEquals(-RaidHpBarLayout.WIDTH / 2f, RaidHpBarLayout.barLeft());
    }

    @Test
    void theTextSitsAboveTheBarWhichSitsAboveTheHead() {
        float head = 2.4f;
        assertTrue(RaidHpBarLayout.barBottom(head) > head);
        assertTrue(RaidHpBarLayout.textBottom(head) > RaidHpBarLayout.barBottom(head) + RaidHpBarLayout.HEIGHT);
    }

    @Test
    void aTallerBossGetsMoreRoomForItsModelOverhang() {
        // A model's flames and horns overhang its hitbox in proportion to its size, so the gap grows with the boss.
        float smallGap = RaidHpBarLayout.barBottom(1f) - 1f;
        float tallGap = RaidHpBarLayout.barBottom(4f) - 4f;
        assertTrue(tallGap > smallGap);
        assertEquals(RaidHpBarLayout.ABOVE_HEAD, RaidHpBarLayout.barBottom(0f), 1e-6f);
    }

    @Test
    void copyDownMessage() {
        assertEquals(" has 2,605 / 4,201 HP left. Next duel in 3s.", RaidHpBarLayout.copyDownTail(2605, 4201, 3));
    }

    @Test
    void trailHoldsAfterAHitThenSlides() {
        RaidHpBarTrail trail = new RaidHpBarTrail(1f);
        assertFalse(trail.update(0.8f, 100));
        assertEquals(1f, trail.shown());
        assertFalse(trail.update(0.7f, 105), "a second hit during the hold does not move it or restart the wait");
        assertTrue(trail.update(0.7f, 100 + RaidHpBarTrail.HOLD_TICKS));
        assertEquals(0.7f, trail.shown());
        assertFalse(trail.update(0.7f, 200), "nothing to do once caught up");
    }

    @Test
    void trailFollowsHpGoingUpAtOnce() {
        RaidHpBarTrail trail = new RaidHpBarTrail(0.5f);
        assertTrue(trail.update(0.6f, 10));
        assertEquals(0.6f, trail.shown());
    }
}
