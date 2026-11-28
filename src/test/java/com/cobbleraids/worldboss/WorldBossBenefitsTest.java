package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldBossBenefitsTest {
    private static final UUID OWNER = new UUID(0, 1), OTHER = new UUID(0, 2), POKEMON = new UUID(0, 3), BOSS = new UUID(0, 4), RAID = new UUID(0, 5);
    private WorldBossBenefits.Attunement bond(UUID pokemon, int style) { return new WorldBossBenefits.Attunement(pokemon, BOSS, "cobblemon:eevee", WorldheartEffects.APPEARANCE, style); }
    private WorldheartCollection unlocked() {
        var collection = new WorldheartCollection(new WorldheartCollection.Snapshot("ab".repeat(32), List.of(), Map.of()));
        for (int win = 1; win <= 12; win++) collection = collection.grant(new UUID(1, win), BOSS, win, win, Set.of(OWNER, OTHER)); return collection;
    }
    @Test void eachAccountHasOneBondAndAChangedOwnerCannotInheritAnotherAccountsAttunement() {
        var benefits = new WorldBossBenefits().attune(OWNER, bond(POKEMON, 0));
        benefits = benefits.attune(OWNER, bond(new UUID(3, 1), 0)); assertEquals(1, benefits.attunements().size());
        benefits = benefits.attune(OTHER, bond(new UUID(3, 1), 0)); assertFalse(benefits.attunements().containsKey(OWNER)); assertEquals(1, benefits.attunements().size());
        benefits.validate(unlocked());
    }
    @Test void tradeEvolutionDifferentPokemonAndMissingAppearanceAllInvalidateBeforeRendering() {
        var bond = bond(POKEMON, 0);
        assertTrue(bond.validFor(OWNER, OWNER, POKEMON, "cobblemon:eevee"));
        assertFalse(bond.validFor(OWNER, OTHER, POKEMON, "cobblemon:eevee"));
        assertFalse(bond.validFor(OWNER, null, POKEMON, "cobblemon:eevee"));
        assertFalse(bond.validFor(OWNER, OWNER, POKEMON, "cobblemon:flareon"));
        assertFalse(bond.validFor(OWNER, OWNER, OTHER, "cobblemon:eevee"));
        assertFalse(new WorldBossBenefits.Attunement(POKEMON, BOSS, "cobblemon:eevee", "missing_art", 0).validFor(OWNER, OWNER, POKEMON, "cobblemon:eevee"));
    }
    @Test void unearnedOwnershipAndStyleCannotBackAnAttunement() {
        assertThrows(IllegalArgumentException.class, () -> new WorldBossBenefits().attune(OWNER, bond(POKEMON, 0)).validate(WorldheartCollection.empty()));
        var collection = unlocked(); assertThrows(IllegalArgumentException.class, () -> new WorldBossBenefits().attune(OWNER, bond(POKEMON, 1)).validate(collection));
        var benefits = new WorldBossBenefits().attune(OWNER, bond(POKEMON, 0)).attune(OWNER, null); benefits.validate(collection);
        assertTrue(collection.progress(OWNER, BOSS).owned()); assertTrue(benefits.attunements().isEmpty());
    }
    @Test void preferencesAndBondsPersistAndUnknownFieldsFailClosed() {
        var benefits = new WorldBossBenefits().attune(OWNER, bond(POKEMON, 0)).effects(OTHER, WorldBossBenefits.Effects.OFF);
        assertSame(benefits, benefits.attune(OWNER, bond(POKEMON, 0))); assertSame(benefits, benefits.effects(OTHER, WorldBossBenefits.Effects.OFF));
        var restored = WorldBossBenefits.load(benefits.save()); assertEquals(benefits.attunements(), restored.attunements());
        assertEquals(WorldBossBenefits.Effects.OFF, restored.effects(OTHER)); assertEquals(WorldBossBenefits.Effects.FULL, restored.effects(OWNER));
        var tag = benefits.save(); tag.remove("preferences"); assertThrows(IllegalArgumentException.class, () -> WorldBossBenefits.load(tag));
    }
    @Test void ordinaryBonusFreezesAtVictoryAndRetryCannotUseNewConfigOrExpiry() {
        var benefits = new WorldBossBenefits().promise(RAID, 1000, 19, Set.of(OWNER, OTHER), true);
        assertEquals(Map.of(OWNER, 1, OTHER, 1), benefits.bonuses().get(RAID).amounts());
        var restored = WorldBossBenefits.load(benefits.save());
        assertSame(restored, restored.promise(RAID, 1000, 500, Set.of(OWNER, OTHER), false));
        assertThrows(IllegalStateException.class, () -> restored.promise(RAID, 1000, 19, Set.of(OWNER), true));
        assertThrows(IllegalStateException.class, () -> restored.promise(RAID, 1001, 19, Set.of(OWNER, OTHER), true));
        assertFalse(restored.bonuses().get(RAID).paid()); assertTrue(restored.pendingBonuses().containsKey(RAID));
        assertTrue(restored.secured(RAID).bonuses().get(RAID).paid()); assertTrue(restored.secured(RAID).pendingBonuses().isEmpty());
    }
    @Test void zeroBonusesRemainFrozenAndClaimKeysCannotAliasOrdinaryBaseRewards() {
        var benefits = new WorldBossBenefits().promise(RAID, 1000, 200, Set.of(OWNER), false);
        assertTrue(benefits.bonuses().get(RAID).paid()); assertEquals(0, benefits.bonuses().get(RAID).amounts().get(OWNER));
        assertSame(benefits, benefits.promise(RAID, 1000, 200, Set.of(OWNER), true));
        assertEquals(WorldBossBenefits.payoutId(RAID), WorldBossBenefits.payoutId(RAID)); assertNotEquals(RAID, WorldBossBenefits.payoutId(RAID));
        assertNotEquals(WorldBossBenefits.payoutId(RAID), WorldBossBenefits.payoutId(OTHER));
    }
    @Test void forgedBonusArithmeticDuplicateAccountsAndPartialPlansAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WorldBossBenefits.Bonus(100, 19, true, Map.of(OWNER, 2), false));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossBenefits(Map.of(OWNER, bond(POKEMON, 0), OTHER, bond(POKEMON, 0)), Map.of(), Map.of()));
        var tag = new WorldBossBenefits().promise(RAID, 1000, 200, Set.of(OWNER), true).save(); tag.getList("bonuses", 10).getCompound(0).remove("paid");
        assertThrows(IllegalArgumentException.class, () -> WorldBossBenefits.load(tag));
    }
}
