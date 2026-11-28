package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import com.cobbleraids.showdown.RaidDamageEvidence;
import com.cobbleraids.api.encounter.EncounterListener;
import com.cobbleraids.api.encounter.EncounterResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldBossDamageEvidenceTest {
    @Test void evidenceIsExplicitAndBoundedInsteadOfReadingAnAnimationTurn() {
        assertEquals(new RaidDamageEvidence(5, 100), RaidDamageEvidence.parse("5", "100"));
        assertEquals(new RaidDamageEvidence(0, 1), RaidDamageEvidence.parse("0", "1"));
        assertNull(RaidDamageEvidence.parse(null, null)); assertNull(RaidDamageEvidence.parse("5", "0"));
        assertNull(RaidDamageEvidence.parse("-1", "5")); assertNull(RaidDamageEvidence.parse("1", "9007199254740992"));
    }
    @Test void oldEncounterListenersStillReceiveExactlyOneDamageCallback() {
        int[] called = {0}; float[] received = {0};
        EncounterListener listener = new EncounterListener() {
            @Override public void onDamage(UUID encounter, UUID player, float amount) { called[0]++; received[0] = amount; }
            @Override public void onEnded(EncounterResult result) {}
        };
        listener.onDamageAction(UUID.randomUUID(), UUID.randomUUID(), 12, 5, 100);
        assertEquals(1, called[0]); assertEquals(12, received[0]);
    }
}
