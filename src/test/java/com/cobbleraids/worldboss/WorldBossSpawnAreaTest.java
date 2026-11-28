package com.cobbleraids.worldboss;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldBossSpawnAreaTest {
    @Test void mixedAndReloadedPoolsMustFallBackToNativeGeneration() {
        var pokemon = new com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail();
        var other = new com.cobblemon.mod.common.api.spawning.detail.SpawnDetail() {
            public String getType() { return "fixture-non-pokemon"; }
            protected com.cobblemon.mod.common.api.spawning.detail.SpawnAction<?> createSpawnAction(
                    com.cobblemon.mod.common.api.spawning.position.SpawnablePosition position, String bucket,
                    com.cobblemon.mod.common.api.spawning.selection.SpawnSelectionData selection) { throw new AssertionError("Must not spawn in classification test"); }
        };
        var details = new java.util.ArrayList<com.cobblemon.mod.common.api.spawning.detail.SpawnDetail>();
        assertFalse(WorldBossArenaProtection.onlySuppressedSpawnDetails(details));
        details.add(pokemon); assertTrue(WorldBossArenaProtection.onlySuppressedSpawnDetails(details));
        details.add(new com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail());
        details.add(new com.cobblemon.mod.common.api.spawning.detail.EmptySpawnDetail());
        assertTrue(WorldBossArenaProtection.onlySuppressedSpawnDetails(details));
        details.add(other); assertFalse(WorldBossArenaProtection.onlySuppressedSpawnDetails(details));
        details.remove(other); assertTrue(WorldBossArenaProtection.onlySuppressedSpawnDetails(details));
    }
    @Test void onlyWholePositiveAreasInsideOneRegionCanSkipNativeGeneration() {
        var region = new WorldBossArenaRegion("minecraft:overworld", 0, 0, 100);
        assertTrue(region.containsSpawnArea("minecraft:overworld", -8, -8, 16, 16));
        assertFalse(region.containsSpawnArea("minecraft:overworld", 90, 0, 16, 16));
        assertFalse(region.containsSpawnArea("minecraft:the_end", -8, -8, 16, 16));
        assertFalse(region.containsSpawnArea("minecraft:overworld", 0, 0, 0, 16));
        assertFalse(region.containsSpawnArea("minecraft:overworld", 0, 0, 16, -1));
        assertFalse(region.containsSpawnArea("minecraft:overworld", Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 16));
    }
    @Test void areaContainmentCoversEveryCandidateCellIncludingTheLastCorner() {
        var region = new WorldBossArenaRegion("minecraft:overworld", -31.5, 15.25, 40);
        for (int x=-80; x<30; x+=7) for (int z=-30; z<80; z+=7) {
            boolean everyCell=true;
            for (int dx=0; dx<8; dx++) for (int dz=0; dz<6; dz++)
                everyCell &= region.contains("minecraft:overworld",x+dx,z+dz,x+dx+1,z+dz+1);
            assertEquals(everyCell,region.containsSpawnArea("minecraft:overworld",x,z,8,6));
        }
    }
}
