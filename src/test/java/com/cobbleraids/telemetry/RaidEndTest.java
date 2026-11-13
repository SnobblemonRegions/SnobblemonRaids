package com.cobbleraids.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RaidEndTest {
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    private static RaidEnd end(RaidEndReason reason, double hpLeft, int timeLimit, String owner) {
        return new RaidEnd("raid:boss", owner == null ? "party" : "owned", owner, "cobbleraids:charizard", "legendary", 5,
                "cobblemon:charizard", 70, reason, 3, 1, 184, timeLimit, hpLeft, 2, List.of(A, B));
    }

    @Test void aTimedOutRaidCarriesEverythingNeededForLossRateByTier() {
        var f = end(RaidEndReason.TIMEOUT, 0.4567, 300, null).fields();
        assertEquals("raid:boss", f.get("id"));
        assertEquals("party", f.get("mode"));
        assertEquals("legendary", f.get("tier"));
        assertEquals(5, f.get("threat"));
        assertEquals("cobblemon:charizard", f.get("species"));
        assertEquals(70, f.get("level"));
        assertEquals("defeat", f.get("outcome"));
        assertEquals("timeout", f.get("reason"));
        assertEquals(3, f.get("partySize"));
        assertEquals(1, f.get("activeAtEnd"));
        assertEquals(184, f.get("seconds"));
        assertEquals(300, f.get("timeLimit"));
        assertEquals(0.457, f.get("bossHpLeft"));
        assertEquals(2, f.get("dealers"));
        assertEquals(List.of(A, B), f.get("players"));
        assertFalse(f.containsKey("owner"));
    }

    @Test void unknownValuesAreLeftOutRatherThanWrittenAsZero() {
        var f = new RaidEnd("raid:boss", "owned", "cobbletowers:boss_floor", "x", "unknown", 0, null, 0, RaidEndReason.WIPE,
                1, 0, -5, 0, -1, 0, null).fields();
        assertEquals("cobbletowers:boss_floor", f.get("owner"));
        assertFalse(f.containsKey("threat"));
        assertFalse(f.containsKey("species"));
        assertFalse(f.containsKey("level"));
        assertFalse(f.containsKey("timeLimit"));
        assertFalse(f.containsKey("bossHpLeft"));
        assertEquals(0, f.get("seconds"));
        assertEquals(List.of(), f.get("players"));
    }

    @Test void everyReasonFallsInOneOutcome() {
        for (var reason : RaidEndReason.values()) {
            assertTrue(List.of("victory", "defeat", "aborted").contains(reason.outcome()), reason.name());
            assertEquals(reason.name().toLowerCase(java.util.Locale.ROOT), reason.id());
        }
        assertEquals("victory", RaidEndReason.VICTORY.outcome());
        for (var given : EnumSet.of(RaidEndReason.WIPE, RaidEndReason.TIMEOUT, RaidEndReason.WITHDREW, RaidEndReason.FLED,
                RaidEndReason.DISCONNECT, RaidEndReason.STRANDED)) assertEquals("defeat", given.outcome(), given.name());
        assertEquals("aborted", RaidEndReason.ABORTED.outcome());
        assertEquals("aborted", RaidEndReason.SERVER_STOP.outcome());
    }

    @Test void healthFractionIsClampedAndUnknownWithoutAPool() {
        assertEquals(0.25, RaidEnd.fraction(25f, 100f), 1e-9);
        assertEquals(0.0, RaidEnd.fraction(-3f, 100f), 1e-9);
        assertEquals(-1.0, RaidEnd.fraction(10f, 0f), 1e-9);
        assertEquals(1.0, end(RaidEndReason.WIPE, 1.7, 0, null).fields().get("bossHpLeft"));
    }

    @Test void aLeaverIsOneEventWithTheirReasonAndHowTheFightStood() {
        var f = new RaidLeft("raid:boss", "party", "cobbleraids:charizard", "legendary", "withdrew", 61, 0.8123, 2, A).fields();
        assertEquals("withdrew", f.get("reason"));
        assertEquals(61, f.get("seconds"));
        assertEquals(0.812, f.get("bossHpLeft"));
        assertEquals(2, f.get("stillIn"));
        assertEquals(A, f.get("player"));
    }
}
