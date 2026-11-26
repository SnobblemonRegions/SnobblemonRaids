package com.cobbleraids.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbleraids.catching.RaidCaptureSession;
import com.cobbleraids.config.RaidRarityTier;
import com.cobbleraids.renown.RenownAffix;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The capture page names the affix a catch keeps: the pending session's for that raid, and only a known affix. */
class CaptureWebPageAffixTest {

    private static final ResourceLocation SILVALLY = ResourceLocation.parse("cobblemon:silvally");
    private static final ResourceLocation DEFINITION = ResourceLocation.parse("cobbleraids:silvally");

    private static RaidCaptureSession session(UUID raidId, String affix) {
        return RaidCaptureSession.offer(UUID.randomUUID(), raidId, DEFINITION, RaidRarityTier.LEGENDARY, SILVALLY, null, 70, false, affix);
    }

    @Test
    void theRaidsOwnSessionGivesItsAffix() {
        UUID raid = UUID.randomUUID();
        List<RaidCaptureSession> pending = List.of(session(UUID.randomUUID(), "last_stand"), session(raid, RenownAffix.RESONANT_WARD.id()));
        assertEquals(Optional.of(RenownAffix.RESONANT_WARD), CaptureWebPage.affixFor(pending, raid), "the session of this raid, not the first one");
    }

    @Test
    void anOrdinaryBossOrAnUnknownValueGivesNone() {
        UUID raid = UUID.randomUUID();
        assertTrue(CaptureWebPage.affixFor(List.of(session(raid, null)), raid).isEmpty(), "no affix: an ordinary boss");
        assertTrue(CaptureWebPage.affixFor(List.of(session(raid, "made_up")), raid).isEmpty(), "a value no affix has is never shown");
        assertTrue(CaptureWebPage.affixFor(List.of(session(UUID.randomUUID(), "last_stand")), raid).isEmpty(), "another raid's session");
        assertTrue(CaptureWebPage.affixFor(null, raid).isEmpty());
    }
}
