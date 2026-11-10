package com.cobbleraids.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/** A shop click buys nothing by itself: the same entry has to be clicked again, soon, by the same player. */
class ShopPurchaseConfirmationTest {

    private final ShopPurchaseConfirmation confirmation = new ShopPurchaseConfirmation();
    private final UUID player = UUID.randomUUID();
    private static final long NOW = 1_000_000L;

    @Test
    @DisplayName("the first click only asks; the second click on the same entry confirms")
    void secondClickConfirms() {
        assertFalse(confirmation.confirm(player, "potion", NOW));
        assertEquals("potion", confirmation.pending(player, NOW));
        assertTrue(confirmation.confirm(player, "potion", NOW + 2_000));
        assertNull(confirmation.pending(player, NOW + 2_000), "a confirmation is used once");
        assertFalse(confirmation.confirm(player, "potion", NOW + 3_000), "the next purchase asks again");
    }

    @Test
    @DisplayName("clicking a different entry asks about that one instead")
    void differentEntryStartsOver() {
        confirmation.confirm(player, "potion", NOW);
        assertFalse(confirmation.confirm(player, "garchomp", NOW + 500));
        assertEquals("garchomp", confirmation.pending(player, NOW + 500));
        assertFalse(confirmation.confirm(player, "potion", NOW + 1_000));
    }

    @Test
    @DisplayName("a confirmation left too long has to be asked for again")
    void expires() {
        confirmation.confirm(player, "potion", NOW);
        long late = NOW + ShopPurchaseConfirmation.WINDOW_MILLIS + 1;
        assertNull(confirmation.pending(player, late));
        assertFalse(confirmation.confirm(player, "potion", late));
    }

    @Test
    @DisplayName("one player's click never confirms another's, and clearing forgets it")
    void perPlayerAndClear() {
        UUID other = UUID.randomUUID();
        confirmation.confirm(player, "potion", NOW);
        assertFalse(confirmation.confirm(other, "potion", NOW));
        confirmation.clear(player);
        assertFalse(confirmation.confirm(player, "potion", NOW + 100));
    }
}
