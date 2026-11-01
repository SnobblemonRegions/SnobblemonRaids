package com.cobbleraids.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class HalloweenSkinPackTest {
    @Test
    void everyListedFileShipsInTheJar() {
        for (String path : HalloweenSkinPack.FILES) {
            assertNotNull(getClass().getClassLoader().getResource(HalloweenSkinPack.JAR_ROOT + path), "missing from resources: " + path);
        }
    }

    @Test
    void noFileIsListedTwice() {
        assertEquals(HalloweenSkinPack.FILES.size(), new HashSet<>(HalloweenSkinPack.FILES).size());
    }

    @Test
    void everySevenSkinsHaveAResolver() {
        long resolvers = HalloweenSkinPack.FILES.stream().filter(p -> p.contains("/resolvers/")).count();
        assertEquals(7, resolvers);
    }
}
