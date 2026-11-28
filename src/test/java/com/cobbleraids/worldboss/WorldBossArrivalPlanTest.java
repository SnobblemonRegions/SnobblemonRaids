package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldBossArrivalPlanTest {
    private static final WorldBossArrivalPlan.Venue VENUE = new WorldBossArrivalPlan.Venue("minecraft:overworld", 0.5, 64, 0.5, 96);
    private static WorldBossTravel.Position point(double x, double y, double z) { return new WorldBossTravel.Position("minecraft:overworld", x, y, z, 0, 0); }
    @Test void participantCandidatesAreBoundedUniqueAndUseAuthoredHeightInsideTheRealSphere() {
        var candidates = WorldBossArrivalPlan.candidates(VENUE, List.of(point(80.5, 64, 0.5)), false);
        assertFalse(candidates.isEmpty()); assertTrue(candidates.size() <= 96); assertEquals(candidates.size(), new java.util.HashSet<>(candidates).size());
        for (var candidate : candidates) { assertEquals(64, candidate.y()); assertTrue(VENUE.accepts(candidate, false)); }
        assertTrue(WorldBossArrivalPlan.candidates(VENUE, List.of(), false).isEmpty());
        var rotated = new WorldBossTravel.Position("minecraft:overworld", 80.5, 64, 0.5, 90, 20);
        assertEquals(candidates, WorldBossArrivalPlan.candidates(VENUE, List.of(rotated), false));
    }
    @Test void observerLandingsNeverEnterEnrollmentHysteresisAndRemainInsideProtectedVenue() {
        var candidates = WorldBossArrivalPlan.candidates(VENUE, List.of(point(104.5, 64, 0.5)), true); assertFalse(candidates.isEmpty());
        for (var candidate : candidates) { assertTrue(VENUE.accepts(candidate, true)); assertTrue(VENUE.contains(candidate)); assertFalse(WorldBossZone.inside(0.5, 64, 0.5, 97, candidate.x(), candidate.y(), candidate.z())); }
        assertFalse(VENUE.accepts(point(98.5, 64, 0.5), true)); assertTrue(VENUE.accepts(point(108.5, 64, 0.5), true));
    }
    @Test void dispersionRanksByNearestOccupantAndDoesNotCountOtherDimensions() {
        var near = point(1, 64, 1); var far = point(80, 64, 1); var anchor = point(0, 64, 1);
        assertEquals(far, WorldBossArrivalPlan.spread(List.of(near, far), List.of(anchor)).getFirst());
        var foreign = new WorldBossTravel.Position("minecraft:the_end", 80, 64, 1, 0, 0);
        assertEquals(near, WorldBossArrivalPlan.spread(List.of(near, far), List.of(foreign)).getFirst());
        assertEquals(List.of(near, far), WorldBossArrivalPlan.spread(List.of(near, far), List.of()));
    }
    @Test void markerLimitsAndThreeDimensionalVenueBoundsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> WorldBossArrivalPlan.candidates(VENUE, java.util.Collections.nCopies(26, point(104.5, 64, 0.5)), true));
        assertThrows(IllegalArgumentException.class, () -> WorldBossArrivalPlan.candidates(VENUE, java.util.Collections.nCopies(51, point(80, 64, 0)), false));
        assertFalse(VENUE.contains(point(0.5, 177, 0.5))); assertTrue(VENUE.contains(point(0.5, 176, 0.5)));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossArrivalPlan.Venue("minecraft:overworld", 0, 64, 0, Double.NaN));
    }
    @Test void defaultGridHasAtLeastFiftySeparatedPotentialLanesWithoutClaimingTerrainOrNativeFit() {
        var candidates = WorldBossArrivalPlan.candidates(VENUE, List.of(point(80.5, 64, 0.5)), false); var space = new WorldBossDuelSpace(); int count = 0;
        for (var candidate : candidates) {
            var boss = WorldBossDuelSpot.candidates(candidate.x(), candidate.z(), VENUE.x() - candidate.x(), VENUE.z() - candidate.z()).getFirst();
            var lane = WorldBossDuelSpace.lane(candidate.x(), 64, candidate.z(), boss[0], 64, boss[1]);
            if (space.reserve(new WorldBossAdmission.Token(new java.util.UUID(1, 1), new java.util.UUID(2, count + 1), count + 1, java.util.UUID.randomUUID()), VENUE.dimension(), lane)) count++;
            if (count == 50) break;
        }
        assertEquals(50, count);
    }
}
