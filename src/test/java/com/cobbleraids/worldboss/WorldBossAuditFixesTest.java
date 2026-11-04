package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** The decisions behind the exploit-audit fixes: where {@code return} may send a player, how long a warp origin lasts, and the optional daily payout cap. */
class WorldBossAuditFixesTest {

    private static final long HOUR = 60L * 60 * 1000;

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    // ---- return is not a standing teleport to spawn ----

    @Test
    void returnNeedsALiveOriginOrAPlayerStandingInTheArena() {
        assertTrue(WorldBossRules.mayReturn(true, false), "a warp saved where they came from");
        assertTrue(WorldBossRules.mayReturn(false, true), "stranded in the arena after a restart");
        assertTrue(WorldBossRules.mayReturn(true, true));
        assertFalse(WorldBossRules.mayReturn(false, false), "anywhere else it is not a way to move: no boss, no warp, no spawn teleport");
    }

    @Test
    void anOriginGoesStaleAfterSixHoursAndNotBefore() {
        long saved = 1_000_000L;
        assertFalse(WorldBossRules.originExpired(saved, saved));
        assertFalse(WorldBossRules.originExpired(saved, saved + 6 * HOUR), "exactly six hours is still good");
        assertTrue(WorldBossRules.originExpired(saved, saved + 6 * HOUR + 1));
        assertTrue(WorldBossRules.originExpired(saved, saved + 48 * HOUR));
    }

    // ---- the optional daily cap on defeat rewards ----

    @Test
    void aCapOfZeroIsNoCapAndAnyOtherCapStopsAtThatMany() {
        assertTrue(WorldBossRules.mayBePaid(0, 0));
        assertTrue(WorldBossRules.mayBePaid(500, 0), "0 means no cap, whatever was paid");
        assertTrue(WorldBossRules.mayBePaid(-3, 0));
        assertTrue(WorldBossRules.mayBePaid(0, 2));
        assertTrue(WorldBossRules.mayBePaid(1, 2));
        assertFalse(WorldBossRules.mayBePaid(2, 2), "the cap-th reward was the last");
        assertFalse(WorldBossRules.mayBePaid(9, 2));
    }

    @Test
    void theCapIsOffByDefaultSoNothingChangesUntilAnOperatorSetsIt() {
        assertEquals(0, WorldBossConfig.defaults().rewardMaxPerDay());
        assertEquals(0, WorldBossConfig.fromJson(json("{}")).rewardMaxPerDay());
    }

    @Test
    void theCapIsReadFromTheRewardSectionAndWrittenBack() {
        WorldBossConfig config = WorldBossConfig.fromJson(json("{\"reward\":{\"max_per_player_per_day\":3}}"));
        assertEquals(3, config.rewardMaxPerDay());
        assertEquals(3, WorldBossConfig.fromJson(config.toJson()).rewardMaxPerDay(), "it survives a save and reload");
    }

    @Test
    void aNegativeCapIsRefused() {
        assertThrows(RuntimeException.class, () -> WorldBossConfig.fromJson(json("{\"reward\":{\"max_per_player_per_day\":-1}}")));
    }

    @Test
    void thePayoutStoreCountsTodayOnlyAndSurvivesASaveAndLoad() {
        WorldBossPayoutStore store = new WorldBossPayoutStore();
        java.util.UUID player = new java.util.UUID(3, 4);
        assertEquals(0, store.paidOn(player, "2026-11-04"));
        assertEquals(1, store.record(player, "2026-11-04"));
        assertEquals(2, store.record(player, "2026-11-04"));
        assertEquals(0, store.paidOn(player, "2026-11-05"), "a new day starts at nothing");
        store.record(player, "2026-11-05");
        assertEquals(0, store.paidOn(player, "2026-11-04"), "and the old day is forgotten");
        WorldBossPayoutStore restored = WorldBossPayoutStore.load(store.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertEquals(1, restored.paidOn(player, "2026-11-05"));
    }
}
