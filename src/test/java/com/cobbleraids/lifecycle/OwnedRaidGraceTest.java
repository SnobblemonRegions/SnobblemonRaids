package com.cobbleraids.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** A tower boss floor's raid must hold a slot longer than the tower's own 5 minute reconnect window. */
class OwnedRaidGraceTest {

    @Test
    void anOrdinaryRaidKeepsTheConfiguredGrace() {
        assertEquals(300, RaidReconnectService.graceSecondsFor((ResourceLocation) null, 300));
    }

    @Test
    void aDuelCopyIsCobbleRaidsOwnAndKeepsTheConfiguredGrace() {
        assertEquals(300, RaidReconnectService.graceSecondsFor(ResourceLocation.fromNamespaceAndPath("cobbleraids", "duel_raid"), 300));
    }

    @Test
    void aTowerFloorOutlastsTheTowerWindow() {
        int grace = RaidReconnectService.graceSecondsFor(ResourceLocation.fromNamespaceAndPath("cobbletowers", "floor"), 300);
        assertEquals(300 + RaidReconnectService.OWNED_GRACE_MARGIN_SECONDS, grace);
    }
}
