package com.cobbleraids.renown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbleraids.config.CobbleRaidsConfig.Renown.Affixes;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RenownAffixBattleTest {

    private static final UUID BOSS = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MINE = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static final List<RenownAffixBattle.Entry> RAID = List.of(
            new RenownAffixBattle.Entry(BOSS, RenownAffix.LAST_STAND, true),
            new RenownAffixBattle.Entry(MINE, RenownAffix.BLOODRUSH, false));

    @Test
    void defaultsRunTheBossAndOwnedInRaidsButNotInPvp() {
        Affixes defaults = Affixes.defaults();
        assertEquals(Map.of(BOSS.toString(), "last_stand", MINE.toString(), "bloodrush"),
                RenownAffixBattle.allowed(RenownAffixBattle.Kind.RAID, RAID, defaults));
        List<RenownAffixBattle.Entry> owned = List.of(new RenownAffixBattle.Entry(MINE, RenownAffix.GRIT, false));
        assertTrue(RenownAffixBattle.allowed(RenownAffixBattle.Kind.PVP, owned, defaults).isEmpty());
        assertEquals(Map.of(MINE.toString(), "grit"),
                RenownAffixBattle.allowed(RenownAffixBattle.Kind.OTHER, owned, defaults));
    }

    @Test
    void masterSwitchTurnsOffEvenTheBoss() {
        Affixes off = new Affixes(false, true, true, true);
        assertTrue(RenownAffixBattle.allowed(RenownAffixBattle.Kind.RAID, RAID, off).isEmpty());
    }

    @Test
    void ownedInRaidsOffKeepsTheBossOnly() {
        Affixes bossOnly = new Affixes(true, false, false, false);
        assertEquals(Map.of(BOSS.toString(), "last_stand"),
                RenownAffixBattle.allowed(RenownAffixBattle.Kind.RAID, RAID, bossOnly));
    }

    @Test
    void aRaidBossFlagOutsideARaidNeverRuns() {
        List<RenownAffixBattle.Entry> stray = List.of(new RenownAffixBattle.Entry(BOSS, RenownAffix.GRIT, true));
        Affixes all = new Affixes(true, true, true, true);
        assertTrue(RenownAffixBattle.allowed(RenownAffixBattle.Kind.OTHER, stray, all).isEmpty());
        assertTrue(RenownAffixBattle.allowed(RenownAffixBattle.Kind.PVP, stray, all).isEmpty());
    }

    @Test
    void nullsAndDuplicatesAreSkippedFirstWins() {
        List<RenownAffixBattle.Entry> messy = Arrays.asList(null,
                new RenownAffixBattle.Entry(null, RenownAffix.GRIT, false),
                new RenownAffixBattle.Entry(MINE, null, false),
                new RenownAffixBattle.Entry(MINE, RenownAffix.GRIT, false),
                new RenownAffixBattle.Entry(MINE, RenownAffix.REGROWTH, false));
        assertEquals(Map.of(MINE.toString(), "grit"),
                RenownAffixBattle.allowed(RenownAffixBattle.Kind.OTHER, messy, Affixes.defaults()));
        assertTrue(RenownAffixBattle.allowed(RenownAffixBattle.Kind.OTHER, messy, null).isEmpty());
    }

    @Test
    void jsonIsAnObjectOrNothing() {
        assertNull(RenownAffixBattle.toJson(Map.of()));
        assertEquals("{\"" + MINE + "\":\"grit\"}", RenownAffixBattle.toJson(Map.of(MINE.toString(), "grit")));
    }
}
