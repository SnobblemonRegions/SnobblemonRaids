package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbleraids.renown.RaidRenown;
import com.cobbleraids.renown.RenownAffix;
import com.cobbleraids.renown.RenownBoon;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** A daily boss survives a restart: same kind, title, affix, players, pool share, and the day it ran is never run again. */
class WorldBossStateDailyTest {

    private static WorldBossState reload(WorldBossState state) {
        CompoundTag tag = state.save(new CompoundTag(), null);
        return WorldBossState.factory().deserializer().apply(tag, null);
    }

    @Test
    void aDailyBossKeepsItsRenownAffixAndPlayersAcrossARestart() {
        WorldBossState state = new WorldBossState();
        state.markDailyDay("2026-11-25");
        state.begin(WorldBossKind.DAILY, UUID.randomUUID(), "cobbleraids:mewtwo", "minecraft:overworld", 1, 2, 3, 10L, 20L,
                new WorldBossPool(1000f), false);
        RaidRenown renown = new RaidRenown("Kaelen", "the Relentless", RenownBoon.statFocus("attack"));
        state.setRenown(renown, RenownAffix.BLOODRUSH, 1.3f);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(state.addParticipant(first));
        assertFalse(state.addParticipant(first), "a player is counted once");
        state.addParticipant(second);

        WorldBossState loaded = reload(state);
        assertEquals(WorldBossKind.DAILY, loaded.kind());
        assertEquals(Optional.of(renown), loaded.renown());
        assertEquals(Optional.of(RenownAffix.BLOODRUSH), loaded.affix());
        assertEquals(1.3f, loaded.poolMultiplier(), 1e-6f);
        assertEquals(Set.of(first, second), loaded.participants());
        assertEquals("2026-11-25", loaded.dailyLastDay());
    }

    @Test
    void anEventBossSavedBeforeKindsExistedReadsAsAnEventWithNoRenown() {
        WorldBossState state = new WorldBossState();
        state.begin(UUID.randomUUID(), "cobbleraids:gengar", "minecraft:overworld", 0, 0, 0, 0L, 0L, new WorldBossPool(500f), false);
        CompoundTag tag = state.save(new CompoundTag(), null);
        tag.remove("kind");
        WorldBossState loaded = WorldBossState.factory().deserializer().apply(tag, null);
        assertEquals(WorldBossKind.EVENT, loaded.kind());
        assertEquals(Optional.empty(), loaded.renown());
        assertEquals(Optional.empty(), loaded.affix());
        assertEquals(1f, loaded.poolMultiplier(), 1e-6f);
    }

    @Test
    void aNewBossClearsTheLastOnesRenownAndPlayersButNotTheDailyDay() {
        WorldBossState state = new WorldBossState();
        state.markDailyDay("2026-11-25");
        state.begin(WorldBossKind.DAILY, UUID.randomUUID(), "cobbleraids:mewtwo", "minecraft:overworld", 0, 0, 0, 0L, 0L,
                new WorldBossPool(1000f), false);
        state.setRenown(new RaidRenown("Kaelen", "the Relentless", RenownBoon.HP_POOL), RenownAffix.LAST_STAND, 1.45f);
        state.addParticipant(UUID.randomUUID());
        state.finish(5L);
        state.begin(UUID.randomUUID(), "cobbleraids:gengar", "minecraft:overworld", 0, 0, 0, 0L, 0L, new WorldBossPool(500f), false);
        assertEquals(WorldBossKind.EVENT, state.kind());
        assertEquals(Optional.empty(), state.renown());
        assertEquals(Optional.empty(), state.affix());
        assertTrue(state.participants().isEmpty());
        assertEquals("2026-11-25", state.dailyLastDay());
    }

    @Test
    void aHandEditedTitleLoadsWithoutRenownRatherThanFailing() {
        WorldBossState state = new WorldBossState();
        state.begin(WorldBossKind.DAILY, UUID.randomUUID(), "cobbleraids:mewtwo", "minecraft:overworld", 0, 0, 0, 0L, 0L,
                new WorldBossPool(1000f), false);
        state.setRenown(new RaidRenown("Kaelen", "the Relentless", RenownBoon.HP_POOL), null, 1.45f);
        CompoundTag tag = state.save(new CompoundTag(), null);
        tag.putString("renown_name", "not, a name");
        WorldBossState loaded = WorldBossState.factory().deserializer().apply(tag, null);
        assertEquals(Optional.empty(), loaded.renown());
        assertEquals(WorldBossKind.DAILY, loaded.kind());
    }
}
