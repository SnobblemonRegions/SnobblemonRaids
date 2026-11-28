package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldBossTravelTest {
    private static final UUID PLAYER = new UUID(0, 1);
    private static final WorldBossTravel.Position HOME = new WorldBossTravel.Position("minecraft:the_nether", 12, 70.5, 22, 30, 10), ARENA = new WorldBossTravel.Position("minecraft:overworld", 100, 64, 100, 0, 0);
    @Test void repeatedAndFailedOutboundWarpsNeverOverwriteTheOriginalDimensionOrPosition() {
        var first = UUID.randomUUID(); var later = UUID.randomUUID(); var travel = new WorldBossTravel().outbound(PLAYER, HOME, ARENA, first);
        travel = WorldBossTravel.load(travel.save()).outbound(PLAYER, ARENA, ARENA, later);
        assertEquals(HOME, travel.ticket(PLAYER).origin()); assertEquals(WorldBossTravel.Phase.OUTBOUND, travel.ticket(PLAYER).phase());
        assertSame(travel, travel.arrived(PLAYER, first)); assertEquals(WorldBossTravel.Phase.ARRIVED, travel.arrived(PLAYER, later).ticket(PLAYER).phase());
    }
    @Test void returnIsConsumedOnlyByTheMatchingReturningOperationAndSurvivesRestartPending() {
        var trip = UUID.randomUUID(); var operation = UUID.randomUUID(); var travel = new WorldBossTravel().outbound(PLAYER, HOME, ARENA, trip);
        assertSame(travel, travel.returned(PLAYER, trip));
        travel = travel.returning(PLAYER, ARENA, HOME, operation); var restored = WorldBossTravel.load(travel.save());
        assertEquals(HOME, restored.ticket(PLAYER).origin()); assertSame(restored, restored.returned(PLAYER, trip)); assertNull(restored.returned(PLAYER, operation).ticket(PLAYER));
    }
    @Test void redirectedReturnCanKeepTheOriginalWhilePreparingAValidatedFallback() {
        var origin = UUID.randomUUID(); var fallback = new WorldBossTravel.Position("minecraft:overworld", 0, 80, 0, 0, 0);
        var travel = new WorldBossTravel().outbound(PLAYER, HOME, ARENA, origin).returning(PLAYER, ARENA, fallback, UUID.randomUUID());
        assertEquals(HOME, travel.ticket(PLAYER).origin()); assertEquals(fallback, travel.ticket(PLAYER).destination());
        assertFalse(HOME.matches("minecraft:overworld", HOME.x(), HOME.y(), HOME.z())); assertFalse(HOME.matches(HOME.dimension(), HOME.x() + 1, HOME.y(), HOME.z())); assertTrue(HOME.matches(HOME.dimension(), HOME.x(), HOME.y(), HOME.z()));
    }
    @Test void nonfiniteCoordinatesMissingFieldsAndDuplicateReturnOwnersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WorldBossTravel.Position("minecraft:overworld", Double.NaN, 64, 0, 0, 0));
        var travel = new WorldBossTravel().outbound(PLAYER, HOME, ARENA, UUID.randomUUID()); var tag = travel.save(); var rows = tag.getList("tickets", 10); rows.add(rows.getCompound(0).copy());
        assertThrows(IllegalArgumentException.class, () -> WorldBossTravel.load(tag));
        var missing = travel.save(); missing.getList("tickets", 10).getCompound(0).remove("origin"); assertThrows(IllegalArgumentException.class, () -> WorldBossTravel.load(missing));
    }
    @Test void disconnectGraceUsesThirtyRealSecondsAndHandlesNanoTimeRollover() {
        var grace = new WorldBossDisconnectGrace(10); assertFalse(grace.expired(30_000_000_009L)); assertTrue(grace.expired(30_000_000_010L)); assertTrue(grace.expired(9));
        long start = Long.MAX_VALUE - 10_000_000_000L; grace = new WorldBossDisconnectGrace(start);
        assertFalse(grace.expired(start + 29_999_999_999L)); assertTrue(grace.expired(start + 30_000_000_000L));
    }
}
