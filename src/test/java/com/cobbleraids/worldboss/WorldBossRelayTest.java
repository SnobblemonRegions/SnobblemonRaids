package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldBossRelayTest {
    private static final String DIM = "minecraft:overworld";
    private static final WorldBossWards.Pylon CACHE = new WorldBossWards.Pylon(DIM, 0, 64, 0), CHECK = new WorldBossWards.Pylon(DIM, 8, 64, 0), PYLON = new WorldBossWards.Pylon(DIM, 8, 64, 8);
    private static final WorldBossRelay.Route ROUTE = new WorldBossRelay.Route(CACHE, CHECK);
    private WorldBossRelay relay() { return new WorldBossRelay(ROUTE, PYLON, WorldBossRelay.at(CACHE), 800_000); }
    private WorldBossRelay.Point at(double x, double z) { return new WorldBossRelay.Point(x + 0.5, 64, z + 0.5); }
    private void arrive(WorldBossRelay relay) {
        for (int x = 1; x <= 8; x++) relay.sample(at(x, 0), 800_000 - x * 50);
        for (int z = 1; z <= 7; z++) relay.sample(at(8, z), 799_600 - z * 50);
        assertEquals(WorldBossRelay.Phase.READY, relay.phase());
        assertTrue(relay.channel(at(8, 7), 799_200));
        assertEquals(WorldBossRelay.Phase.CHANNEL, relay.phase());
    }
    @Test void routeRequiresBothLegsAndSixActiveSecondsAtThePylon() {
        var relay = relay(); arrive(relay); long end = relay.finishRemaining();
        assertEquals(WorldBossRelay.Phase.CHANNEL, relay.sample(at(8, 7), end + 1));
        assertEquals(WorldBossRelay.Phase.COMPLETE, relay.sample(at(8, 7), end));
        assertEquals(WorldBossRelay.Phase.COMPLETE, relay.sample(at(0, 0), 0));
    }
    @Test void cannotSkipCheckpointOrJumpDirectlyToPylon() {
        var relay = relay(); assertEquals(WorldBossRelay.Phase.CANCELLED, relay.sample(at(8, 8), 799_000));
        relay = relay(); relay.sample(at(2, 2), 799_950);
        assertEquals(WorldBossRelay.Phase.CANCELLED, relay.sample(at(4, 4), 799_900));
    }
    @Test void channelRequiresAnExplicitArrivalActionAndRepeatedCommandsCannotResetItsTimer() {
        var relay = relay();
        for (int x = 1; x <= 8; x++) relay.sample(at(x, 0), 800_000 - x * 50);
        for (int z = 1; z <= 7; z++) relay.sample(at(8, z), 799_600 - z * 50);
        assertEquals(WorldBossRelay.Phase.READY, relay.sample(at(8, 7), 790_000));
        assertTrue(relay.channel(at(8, 7), 790_000)); long finish = relay.finishRemaining();
        assertFalse(relay.channel(at(8, 7), 789_000)); assertEquals(finish, relay.finishRemaining());
        assertEquals(WorldBossRelay.Phase.COMPLETE, relay.sample(at(8, 7), finish));
    }
    @Test void leavingTheCorridorOrMovingDuringChannelCancelsWithoutCompleting() {
        var relay = relay(); assertEquals(WorldBossRelay.Phase.CANCELLED, relay.sample(at(0, 2.6), 799_950));
        relay = relay(); arrive(relay); assertEquals(WorldBossRelay.Phase.CANCELLED, relay.sample(at(8.31, 7), 799_000));
        assertEquals(WorldBossRelay.Phase.CANCELLED, relay.sample(at(8, 8), 700_000));
    }
    @Test void recoveryClockRewindCannotContinueAnOldToken() {
        var relay = relay(); relay.sample(at(1, 0), 799_000);
        assertEquals(WorldBossRelay.Phase.CANCELLED, relay.sample(at(2, 0), 799_001));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossRelay(ROUTE, PYLON, at(8, 8), 800_000));
    }
    @Test void malformedOrUnboundedRoutesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WorldBossRelay.Route(CACHE, new WorldBossWards.Pylon(DIM, 7, 64, 0)));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossRelay.Route(CACHE, new WorldBossWards.Pylon(DIM, 25, 64, 0)));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossRelay.Route(CACHE, new WorldBossWards.Pylon(DIM, 8, 65, 0)));
        assertThrows(IllegalArgumentException.class, () -> ROUTE.validate(CACHE));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossRelay.Point(Double.NaN, 0, 0));
    }
    @Test void deliveryCapsChargeButStillAwardsMeritAfterCombatOrFocusFilledTheCap() {
        var wards = new WorldBossWards(PYLON, ROUTE); var player = UUID.randomUUID(); var duel = UUID.randomUUID();
        wards.damaged(100_000, 70_000, 800_000, 5);
        for (int turn = 1; turn <= 3; turn++) wards.combat(duel, player, turn);
        assertTrue(wards.relay(player, 1, 790_000)); assertTrue(wards.focus(player, 1, 790_000));
        assertEquals(3, wards.active().units()); assertEquals(1, wards.active().people());
        assertFalse(wards.relay(player, 1, 790_000));
        var restored = WorldBossContributionCodec.wards(WorldBossContributionCodec.wards(wards));
        assertEquals(wards.snapshot(), restored.snapshot()); assertFalse(restored.relay(player, 1, 790_000));
        assertFalse(restored.relay(UUID.randomUUID(), 1, 755_000));
    }
    @Test void twoWardRoutesQualifyWithoutAnyCombatAndRoundTripWithoutRepeatingCredit() {
        var merit = new WorldBossMerit(); var player = UUID.randomUUID();
        assertTrue(merit.relayed(player, 1)); assertTrue(merit.focused(player, 1)); assertEquals(50, merit.score(player));
        merit = WorldBossContributionCodec.merit(WorldBossContributionCodec.merit(merit));
        assertFalse(merit.relayed(player, 1)); assertFalse(merit.focused(player, 1));
        assertTrue(merit.relayed(player, 2)); assertTrue(merit.focused(player, 2)); assertTrue(merit.qualified(player));
    }
    @Test void legacyContributionFormatsRemainRelayDisabledAndUnknownOrPartialDataIsRejected() {
        var wards = new WorldBossWards(PYLON); var tag = WorldBossContributionCodec.wards(wards);
        tag.putInt("version", 1); tag.remove("relay_enabled");
        var restored = WorldBossContributionCodec.wards(tag); assertNull(restored.route());
        assertFalse(restored.relay(UUID.randomUUID(), 1, 800_000));
        var merit = WorldBossContributionCodec.merit(new WorldBossMerit()); merit.putInt("version", 1); merit.remove("relays");
        assertTrue(WorldBossContributionCodec.merit(merit).snapshot().relays().isEmpty());
        var broken = WorldBossContributionCodec.wards(new WorldBossWards(PYLON, ROUTE)); broken.remove("route");
        assertThrows(IllegalArgumentException.class, () -> WorldBossContributionCodec.wards(broken));
        var missing = WorldBossContributionCodec.merit(new WorldBossMerit()); missing.remove("relays");
        assertThrows(IllegalArgumentException.class, () -> WorldBossContributionCodec.merit(missing));
    }
}
