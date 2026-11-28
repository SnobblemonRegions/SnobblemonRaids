package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldBossAudienceTest {
    private static UUID id(int value) { return new UUID(0, value); }
    @Test void pendingArrivalsConsumeTheSameTwentyFiveSeatsAsPhysicalViewers() {
        var audience = new WorldBossAudience(); var pending = new java.util.HashSet<UUID>();
        for (int i = 1; i <= 25; i++) { assertTrue(audience.admit(id(i))); pending.add(id(i)); }
        assertFalse(audience.admit(id(26))); assertEquals(Set.of(id(26)), audience.reconcile(List.of(id(26)), pending));
        assertEquals(25, audience.size());
    }
    @Test void existingSeatsKeepPriorityEvenIfPresenceOrderChanges() {
        var audience = new WorldBossAudience(); var viewers = new java.util.ArrayList<UUID>();
        for (int i = 1; i <= 25; i++) { viewers.add(id(i)); audience.admit(id(i)); }
        java.util.Collections.reverse(viewers); viewers.addFirst(id(26));
        assertEquals(Set.of(id(26)), audience.reconcile(viewers, Set.of()));
        for (int i = 1; i <= 25; i++) assertTrue(audience.contains(id(i)));
    }
    @Test void AbsentViewersReleaseCapacityAndPendingViewersAreNotDoubleCounted() {
        var audience = new WorldBossAudience(); audience.admit(id(1)); audience.admit(id(2));
        assertTrue(audience.reconcile(List.of(id(2), id(2), id(3)), Set.of(id(2))).isEmpty());
        assertFalse(audience.contains(id(1))); assertEquals(2, audience.size());
        assertTrue(audience.admit(id(2))); assertEquals(2, audience.size());
        audience.release(id(2)); assertEquals(1, audience.size()); audience.clear(); assertEquals(0, audience.size());
    }
    @Test void ReconciliationDropsCancelledPendingClaims() {
        var audience = new WorldBossAudience(); for (int i = 1; i <= 25; i++) audience.admit(id(i));
        var retained = new java.util.HashSet<UUID>(); for (int i = 2; i <= 25; i++) retained.add(id(i));
        assertTrue(audience.reconcile(List.of(id(26)), retained).isEmpty());
        assertFalse(audience.contains(id(1))); assertTrue(audience.contains(id(26))); assertEquals(25, audience.size());
    }
    @Test void randomPresenceAndPendingChangesNeverOverbookOrApproveOverflow() {
        var audience = new WorldBossAudience(); var random = new java.util.Random(91);
        for (int turn = 0; turn < 5000; turn++) {
            UUID candidate = id(random.nextInt(50)); if (random.nextBoolean()) audience.admit(candidate); else audience.release(candidate);
            var present = new java.util.ArrayList<UUID>(); var pending = new java.util.HashSet<UUID>();
            for (int i = 0; i < 50; i++) { if (random.nextInt(4) == 0) present.add(id(i)); if (audience.contains(id(i)) && random.nextBoolean()) pending.add(id(i)); }
            var overflow = audience.reconcile(present, pending);
            assertTrue(audience.size() <= 25);
            for (UUID rejected : overflow) { assertTrue(present.contains(rejected)); assertFalse(audience.contains(rejected)); }
            for (UUID held : pending) assertTrue(audience.contains(held));
        }
    }
}
