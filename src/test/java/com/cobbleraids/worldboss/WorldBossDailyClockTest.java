package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WorldBossDailyClockTest {
    private static final long PREP = WorldBossCampaignTest.PREP, COMBAT = WorldBossCampaignTest.COMBAT;
    private WorldBossClock clock() { return WorldBossClock.begin(300_000, PREP); }

    @Test void dailyCombatOpensOnTheWallBoundaryAndPreparationNeverSpendsCombatTime() {
        var clock = clock().scheduledPreparation(WorldBossCampaignTest.SLOT, 299_999, COMBAT - 1, true);
        assertEquals(WorldBossClock.Phase.PREPARING, clock.phase());
        assertEquals(1, clock.phaseRemainingMillis());
        clock = clock.scheduledPreparation(WorldBossCampaignTest.SLOT, 1, COMBAT, true);
        assertTrue(clock.combatOpen());
        assertEquals(900_000, clock.remainingMillis());
    }

    @Test void normalOpeningDelayIsBoundedAndALongerStallSkipsInsteadOfCatchingUp() {
        assertTrue(clock().scheduledPreparation(WorldBossCampaignTest.SLOT, 310_000, COMBAT + 10_000, true).combatOpen());
        var late = clock().scheduledPreparation(WorldBossCampaignTest.SLOT, 310_001, COMBAT + 10_001, true);
        assertEquals(WorldBossClock.Phase.CANCELLED, late.phase());
        assertEquals(WorldBossClock.Reason.SCHEDULE_MISSED, late.reason());
        assertFalse(clock().scheduledPreparation(WorldBossCampaignTest.SLOT, 300_000, COMBAT, false).combatOpen());
    }

    @Test void forwardAndBackwardWallJumpsHoldInsteadOfFabricatingFiveMinutesOfPreparation() {
        assertEquals(WorldBossClock.Reason.CLOCK_UNCERTAIN, clock().scheduledPreparation(WorldBossCampaignTest.SLOT, 1, COMBAT, true).reason());
        assertEquals(WorldBossClock.Reason.CLOCK_UNCERTAIN, clock().scheduledPreparation(WorldBossCampaignTest.SLOT, 1, PREP - 1, true).reason());
    }

    @Test void latePreparationTickAndBootAreDistinctFromANormalBoundaryCrossing() {
        var boot = Instant.ofEpochMilli(PREP - 60_000);
        assertNotNull(WorldBossSchedule.preparationDue(boot, Instant.ofEpochMilli(PREP - 1), Instant.ofEpochMilli(PREP + 9_999), null));
        assertNull(WorldBossSchedule.preparationDue(boot, Instant.ofEpochMilli(PREP - 1), Instant.ofEpochMilli(PREP + 10_001), null));
        assertNull(WorldBossSchedule.preparationDue(Instant.ofEpochMilli(PREP + 1), Instant.ofEpochMilli(PREP + 1), Instant.ofEpochMilli(PREP + 2), null));
        assertNull(WorldBossSchedule.preparationDue(boot, boot, Instant.ofEpochMilli(PREP + 1), null), "A boot/stall crossing the boundary is not proof of normal preparation coverage");
    }
}
