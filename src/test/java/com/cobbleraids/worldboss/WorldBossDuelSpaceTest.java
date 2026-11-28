package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldBossDuelSpaceTest {
    private static final UUID EVENT = new UUID(1, 1);
    private static WorldBossAdmission.Token token(int player, long generation) { return new WorldBossAdmission.Token(EVENT, new UUID(2, player), generation, new UUID(3, player)); }

    @Test void pendingReservationsBlockIntersectingStartsUntilExactOwnerReleases() {
        var space = new WorldBossDuelSpace(); var first = token(1, 1); var second = token(2, 2);
        var box = WorldBossDuelSpace.lane(0, 64, 0, 6, 64, 0);
        assertTrue(space.reserve(first, "minecraft:overworld", box));
        assertTrue(space.reserve(first, "minecraft:overworld", box)); assertEquals(1, space.size());
        assertFalse(space.reserve(second, "minecraft:overworld", WorldBossDuelSpace.lane(1, 64, 0, 7, 64, 0)));
        assertFalse(space.release(new WorldBossAdmission.Token(EVENT, first.playerId(), 99, first.encounterId())));
        assertTrue(space.owns(first)); assertTrue(space.release(first)); assertFalse(space.release(first));
        assertTrue(space.reserve(second, "minecraft:overworld", box));
    }

    @Test void staleGenerationCannotRemoveAReusedEncounterOrAPlayerSuccessor() {
        var space = new WorldBossDuelSpace(); var old = token(1, 1); var next = token(1, 2);
        var box = WorldBossDuelSpace.lane(-16.5, 64, -16.5, -10.5, 64, -16.5);
        assertTrue(space.reserve(old, "minecraft:overworld", box)); assertTrue(space.release(old));
        assertTrue(space.reserve(next, "minecraft:overworld", box)); assertFalse(space.release(old)); assertTrue(space.owns(next));
        assertFalse(space.reserve(new WorldBossAdmission.Token(UUID.randomUUID(), next.playerId(), 2, next.encounterId()), "minecraft:overworld", box));
    }

    @Test void dimensionsAndVerticalLayersAreIndependentButOnePlayerCannotOwnTwoLanes() {
        var space = new WorldBossDuelSpace(); var box = WorldBossDuelSpace.lane(0, 64, 0, 6, 64, 0);
        assertTrue(space.reserve(token(1, 1), "minecraft:overworld", box));
        assertTrue(space.reserve(token(2, 2), "minecraft:the_end", box));
        assertTrue(space.reserve(token(3, 3), "minecraft:overworld", WorldBossDuelSpace.lane(0, 72, 0, 6, 72, 0)));
        var duplicate = new WorldBossAdmission.Token(EVENT, token(1, 1).playerId(), 4, UUID.randomUUID());
        assertFalse(space.reserve(duplicate, "minecraft:the_nether", box));
        space.clear(); assertEquals(0, space.size()); assertFalse(space.owns(token(1, 1)));
    }

    @Test void fiftySeparatedLanesFitTheDefaultBubbleGeometricallyAndCapacityIsHardBounded() {
        var space = new WorldBossDuelSpace(); int count = 0;
        for (int x = -72; x <= 72 && count < 50; x += 18) for (int z = -72; z <= 72 && count < 50; z += 18) {
            if (!WorldBossZone.inside(0, 64, 0, 96, x, 64, z)) continue;
            var lane = WorldBossDuelSpace.lane(x, 64, z, x + 6, 64, z);
            assertTrue(space.reserve(token(++count, count), "minecraft:overworld", lane));
        }
        assertEquals(50, count); assertEquals(50, space.size());
        assertFalse(space.reserve(token(51, 51), "minecraft:the_end", WorldBossDuelSpace.lane(0, 64, 0, 6, 64, 0)));
    }

    @Test void corridorContainsBothActorBodiesAndPreservesASeparationMargin() {
        var lane = WorldBossDuelSpace.lane(0, 64, 0, 10, 64, 0);
        assertTrue(lane.contains(new WorldBossDuelSpace.Box(-0.3, 64, -0.3, 0.3, 65.8, 0.3)));
        assertTrue(lane.contains(new WorldBossDuelSpace.Box(8, 64, -2, 12, 72, 2)));
        assertFalse(lane.contains(new WorldBossDuelSpace.Box(7, 64, -4, 13, 72, 4)));
        assertTrue(lane.overlaps(WorldBossDuelSpace.lane(0, 64, 1, 10, 64, 1)));
        assertFalse(lane.overlaps(WorldBossDuelSpace.lane(0, 64, 6, 10, 64, 6)));
    }

    @Test void everyBoundedCandidateLaneFitsTheRetentionAndProtectionMarginsAtBubbleEdges() {
        var region = new WorldBossArenaRegion("minecraft:overworld", 0, 0, 96 + WorldBossChunkLease.PLACEMENT_MARGIN);
        var plan = WorldBossChunkLease.plan(0, 0, 96); var chunks = new java.util.HashSet<>(plan.retained());
        for (int angle = 0; angle < 360; angle += 5) {
            double px = 97 * Math.cos(Math.toRadians(angle)), pz = 97 * Math.sin(Math.toRadians(angle));
            for (var spot : WorldBossDuelSpot.candidates(px, pz, px, pz)) {
                var lane = WorldBossDuelSpace.lane(px, 64, pz, spot[0], 64, spot[1]);
                for (double x : new double[]{lane.minX(), lane.maxX()}) for (double z : new double[]{lane.minZ(), lane.maxZ()}) {
                    assertTrue(region.intersects("minecraft:overworld", x, z, x, z, 0));
                    assertTrue(chunks.contains(new WorldBossChunkLease.Chunk((int) Math.floor(x / 16), (int) Math.floor(z / 16))));
                }
            }
        }
    }

    @Test void invalidGeometryAndUnownedTokensCannotEnterTheCoordinator() {
        assertThrows(IllegalArgumentException.class, () -> WorldBossDuelSpace.lane(0, 64, 0, 0, 64, 0));
        assertThrows(IllegalArgumentException.class, () -> WorldBossDuelSpace.lane(0, 64, 0, 11, 64, 0));
        assertThrows(IllegalArgumentException.class, () -> WorldBossDuelSpace.lane(0, 64, 0, 6, 66, 0));
        assertThrows(IllegalArgumentException.class, () -> WorldBossDuelSpace.lane(Double.NaN, 64, 0, 6, 64, 0));
        var box = WorldBossDuelSpace.lane(0, 64, 0, 6, 64, 0); var space = new WorldBossDuelSpace();
        assertThrows(IllegalArgumentException.class, () -> space.reserve(token(1, 0), "minecraft:overworld", box));
        assertFalse(space.release(null)); assertEquals(0, space.size());
    }
}
