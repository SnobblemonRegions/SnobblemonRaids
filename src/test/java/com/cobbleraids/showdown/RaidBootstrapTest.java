package com.cobbleraids.showdown;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RaidBootstrapTest {
    private static final String START = ">start {\"format\":{\"mod\":\"cobblemon\",\"gameType\":\"raid\",\"gen\":9,\"ruleset\":[],\"playerCount\":2}}";
    @Test void nativeEmptyRulesRaidStartsWithoutUnrequestedTeamPreviewChoices() {
        String[] input = {START, ">player p1 {\"team\":\"packed-a\"}", ">player p2 {\"team\":\"packed-b\"}", ">p1 team 1", ">p2 team 1"};
        assertArrayEquals(new String[] {input[0], input[1], input[2]}, RaidBootstrap.withoutUnrequestedTeamChoices(input));
        assertEquals(5, input.length); assertEquals(">p1 team 1", input[3]);
    }
    @Test void otherFormatsExplicitRulesAndUnknownInputsArePreserved() {
        for (String start : new String[] {START.replace("raid", "singles"), START.replace("[]", "[\"Team Preview\"]"),
                START.replace("[]", "[\"Custom Rule\"]"), ">start {}", ">start not-json", ">start {\"format\":null}"}) {
            String[] input = {start, ">p1 team 1"};
            assertSame(input, RaidBootstrap.withoutUnrequestedTeamChoices(input));
        }
    }
    @Test void actorIndicesAndOrderingArePreservedAndMoveChoicesAreNeverFiltered() {
        String[] input = {START, ">p10 team 1,2", ">p10 move 1", ">p51 team 1", null, ">p0 team 1", ">p1 team invalid"};
        assertArrayEquals(new String[] {START, ">p10 move 1", null, ">p0 team 1", ">p1 team invalid"},
                RaidBootstrap.withoutUnrequestedTeamChoices(input));
    }
    @Test void alreadyAdaptedOrUnidentifiedPayloadsKeepTheirIdentity() {
        String[] input = {START, ">player p1 {}", ">player p2 {}"};
        assertSame(input, RaidBootstrap.withoutUnrequestedTeamChoices(input));
        String[] unknown = {">p1 team 1"};
        assertSame(unknown, RaidBootstrap.withoutUnrequestedTeamChoices(unknown));
        String[] ambiguous = {START, START, ">p1 team 1"};
        assertSame(ambiguous, RaidBootstrap.withoutUnrequestedTeamChoices(ambiguous));
        assertNull(RaidBootstrap.withoutUnrequestedTeamChoices(null));
    }
}
