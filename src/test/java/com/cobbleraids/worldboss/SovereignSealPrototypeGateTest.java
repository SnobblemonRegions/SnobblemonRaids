package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("cobbleraids.worldboss.sovereignPrototype")
class SovereignSealPrototypeGateTest {
    @Test
    void requiresBothExplicitOptInAndATestEncounter() {
        String previous = System.getProperty(SovereignSealPrototype.PROPERTY);
        try {
            var live = new WorldBossState();
            var test = new WorldBossState(); test.markTestMode();
            System.clearProperty(SovereignSealPrototype.PROPERTY);
            assertFalse(SovereignSealPrototype.enabled(live));
            assertFalse(SovereignSealPrototype.enabled(test));
            System.setProperty(SovereignSealPrototype.PROPERTY, "true");
            assertFalse(SovereignSealPrototype.enabled(live), "a prototype flag must never replace a live event's boundary");
            assertTrue(SovereignSealPrototype.enabled(test));
        } finally {
            if (previous == null) System.clearProperty(SovereignSealPrototype.PROPERTY);
            else System.setProperty(SovereignSealPrototype.PROPERTY, previous);
        }
    }
}
