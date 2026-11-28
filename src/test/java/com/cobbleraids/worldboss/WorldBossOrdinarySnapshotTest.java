package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import com.cobbleraids.lifecycle.RaidOutcome;
import com.cobbleraids.lifecycle.RaidRewardEligibility;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class WorldBossOrdinarySnapshotTest {
    @Test void victoryTimeAndRosterCannotChangeWhileSlowRewardsAreBeingProcessed() {
        var player = UUID.randomUUID(); var contribution = new HashMap<UUID, Float>(); contribution.put(player, 10f);
        var players = new HashSet<UUID>(); players.add(player);
        var victory = new RaidRewardEligibility(UUID.randomUUID(), ResourceLocation.parse("cobbleraids:giratina"), RaidOutcome.VICTORY, contribution, players, 100, "", "", 1000);
        players.clear(); contribution.clear(); assertEquals(1000, victory.committedAtMillis()); assertEquals(java.util.Set.of(player), victory.participants());
        assertEquals(10f, victory.contribution().get(player)); assertThrows(UnsupportedOperationException.class, () -> victory.participants().clear());
    }
    @Test void legacySnapshotsHaveNoInventedBlessingTime() {
        var legacy = new RaidRewardEligibility(UUID.randomUUID(), ResourceLocation.parse("cobbleraids:giratina"), RaidOutcome.VICTORY, java.util.Map.of(), java.util.Set.of(), 100, "", "");
        assertEquals(0, legacy.committedAtMillis());
        assertThrows(IllegalArgumentException.class, () -> new RaidRewardEligibility(legacy.raidId(), legacy.definitionId(), legacy.outcome(), legacy.contribution(), legacy.participants(), 100, "", "", -1));
    }
}
