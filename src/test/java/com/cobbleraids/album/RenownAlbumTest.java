package com.cobbleraids.album;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The Coin Album's progress and pages. */
class RenownAlbumTest {

    private static Map<String, String> slots() {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("cobblemon:rayquaza", "dragon");
        slots.put("cobblemon:dialga", "steel");
        slots.put("cobblemon:zekrom", "dragon");
        slots.put("cobblemon:kyogre", "water");
        return slots;
    }

    @Test
    void progressCountsFilledSlotsAndFinishedTypePages() {
        RenownAlbum.Progress none = RenownAlbum.progress(slots(), Set.of());
        assertEquals(0, none.pressed());
        assertEquals(4, none.total());
        assertTrue(none.completedTypes().isEmpty());
        assertFalse(none.complete());

        RenownAlbum.Progress dragons = RenownAlbum.progress(slots(), Set.of("cobblemon:rayquaza", "cobblemon:zekrom", "cobblemon:mew"));
        assertEquals(2, dragons.pressed(), "a species outside the album does not count");
        assertEquals(Set.of("dragon"), dragons.completedTypes());
        assertFalse(dragons.complete());

        RenownAlbum.Progress all = RenownAlbum.progress(slots(), slots().keySet());
        assertTrue(all.complete());
        assertEquals(Set.of("dragon", "steel", "water"), all.completedTypes());
        assertFalse(RenownAlbum.progress(Map.of(), Set.of()).complete(), "an empty album is never complete");
    }

    @Test
    void pagesAreTheTypesWithSpeciesInTheGivenOrder() {
        assertEquals(List.of("water", "dragon", "steel"),
                RenownAlbum.pages(slots(), List.of("normal", "fire", "water", "dragon", "steel", "fairy")));
    }
}
