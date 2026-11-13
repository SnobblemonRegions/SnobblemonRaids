package com.cobbleraids.duel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbleraids.telemetry.RaidEndReason;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A duel raid that a server stop or an operator ends is called off: no loss, and the charges go back. */
class CalledOffRaidTest {

    private static final UUID FOUGHT = new UUID(0, 1);
    private static final UUID WALKED_AWAY = new UUID(0, 2);
    private static final UUID NEVER_STARTED = new UUID(0, 3);
    private static final List<UUID> PLAYERS = List.of(FOUGHT, WALKED_AWAY, NEVER_STARTED);

    @Test
    void onlyAServerStopOrAnAbortIsCalledOff() {
        assertTrue(RaidDuelRules.isCalledOff(RaidEndReason.SERVER_STOP));
        assertTrue(RaidDuelRules.isCalledOff(RaidEndReason.ABORTED));
        for (RaidEndReason reason : RaidEndReason.values()) {
            if (reason == RaidEndReason.SERVER_STOP || reason == RaidEndReason.ABORTED) continue;
            assertFalse(RaidDuelRules.isCalledOff(reason), reason + " is a real ending");
        }
    }

    @Test
    void aCalledOffRaidRefundsWhoFoughtAndStayed() {
        Set<UUID> refunded = RaidDuelRules.refundedWhenCalledOff(PLAYERS, Set.of(FOUGHT, WALKED_AWAY), Set.of(WALKED_AWAY));
        assertEquals(Set.of(FOUGHT), refunded, "walking away keeps the charge spent; a duel that never began was refunded at the start");
    }

    @Test
    void aLossCountsOnlyForPlayersWhoseDuelBegan() {
        assertEquals(Set.of(FOUGHT, WALKED_AWAY), RaidDuelRules.losers(PLAYERS, Set.of(FOUGHT, WALKED_AWAY)));
    }
}
