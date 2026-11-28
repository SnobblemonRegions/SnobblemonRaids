package com.cobbleraids.title;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbleraids.catching.RaidPlayerRecord;
import com.cobbleraids.config.RaidRarityTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link TitleService#qualifies}, the one rule that decides whether a raid win or a catch
 * crosses a title's threshold. Driven directly against a plain {@link RaidPlayerRecord}, with no
 * {@code MinecraftServer} needed -- {@link TitleService#checkUnlocks}'s own job (looking a title up,
 * persisting the unlock, messaging the player) is thin enough to cover live instead.
 */
class TitleServiceTest {

    private static final ResourceLocation BLASTOISE = ResourceLocation.parse("cobbleraids:blastoise");

    @Test
    @DisplayName("RAIDS_WON qualifies once total wins reach the threshold, not before")
    void raidsWonThreshold() {
        TitleDefinition title = new TitleDefinition("t", "T", "WHITE", TitleUnlockType.RAIDS_WON, null, 3);
        RaidPlayerRecord below = RaidPlayerRecord.EMPTY
                .withWin(RaidRarityTier.STARTER, BLASTOISE, 1.0)
                .withWin(RaidRarityTier.STARTER, BLASTOISE, 1.0);
        RaidPlayerRecord atThreshold = below.withWin(RaidRarityTier.STARTER, BLASTOISE, 1.0);

        assertFalse(TitleService.qualifies(below, title));
        assertTrue(TitleService.qualifies(atThreshold, title));
    }

    @Test
    @DisplayName("TIER_WINS only counts wins of its own tier, and needs a tier to check at all")
    void tierWinsThresholdIsTierSpecific() {
        TitleDefinition legendaryTitle = new TitleDefinition("t", "T", "WHITE",
                TitleUnlockType.TIER_WINS, RaidRarityTier.LEGENDARY, 2);
        TitleDefinition brokenTitle = new TitleDefinition("broken", "Broken", "WHITE",
                TitleUnlockType.TIER_WINS, null, 2);
        RaidPlayerRecord record = RaidPlayerRecord.EMPTY
                .withWin(RaidRarityTier.STARTER, BLASTOISE, 1.0)
                .withWin(RaidRarityTier.LEGENDARY, BLASTOISE, 1.0)
                .withWin(RaidRarityTier.LEGENDARY, BLASTOISE, 1.0);

        assertTrue(TitleService.qualifies(record, legendaryTitle), "two legendary wins must satisfy a threshold of two");
        assertFalse(TitleService.qualifies(record, brokenTitle), "a null tier must never quietly match");
    }

    @Test
    @DisplayName("BOSSES_CAUGHT reads the catch counter, not the win counter")
    void bossesCaughtThreshold() {
        TitleDefinition title = new TitleDefinition("t", "T", "WHITE", TitleUnlockType.BOSSES_CAUGHT, null, 1);
        RaidPlayerRecord wonNotCaught = RaidPlayerRecord.EMPTY.withWin(RaidRarityTier.STARTER, BLASTOISE, 1.0);
        RaidPlayerRecord caught = wonNotCaught.withCatch();

        assertFalse(TitleService.qualifies(wonNotCaught, title));
        assertTrue(TitleService.qualifies(caught, title));
    }

    @Test
    @DisplayName("album titles follow the Coin Album, and need its progress to unlock")
    void albumTitlesFollowTheAlbum() {
        var album = new com.cobbleraids.album.RenownAlbum.Progress(12, 40, java.util.Set.of("dragon"), false);
        var bearer = new TitleDefinition("album_coin_bearer", "Coin Bearer", "YELLOW", TitleUnlockType.ALBUM_SPECIES, null, 10);
        var numismatist = new TitleDefinition("album_numismatist", "Numismatist", "GOLD", TitleUnlockType.ALBUM_SPECIES, null, 25);
        var dragon = new TitleDefinition("album_curator_dragon", "Dragon Curator", "YELLOW", TitleUnlockType.ALBUM_TYPE, null, 1, "dragon");
        var steel = new TitleDefinition("album_curator_steel", "Steel Curator", "YELLOW", TitleUnlockType.ALBUM_TYPE, null, 1, "steel");
        var renowned = new TitleDefinition("album_renowned", "Renowned", "LIGHT_PURPLE", TitleUnlockType.ALBUM_COMPLETE, null, 1);
        assertTrue(TitleService.qualifies(RaidPlayerRecord.EMPTY, bearer, album));
        assertFalse(TitleService.qualifies(RaidPlayerRecord.EMPTY, numismatist, album));
        assertTrue(TitleService.qualifies(RaidPlayerRecord.EMPTY, dragon, album));
        assertFalse(TitleService.qualifies(RaidPlayerRecord.EMPTY, steel, album));
        assertFalse(TitleService.qualifies(RaidPlayerRecord.EMPTY, renowned, album));
        assertTrue(TitleService.qualifies(RaidPlayerRecord.EMPTY, renowned,
                new com.cobbleraids.album.RenownAlbum.Progress(40, 40, java.util.Set.of("dragon"), true)));
        assertFalse(TitleService.qualifies(RaidPlayerRecord.EMPTY, bearer), "no album progress, no album title");
    }
}
