package com.cobbleraids.renown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RenownAffixTest {

    @Test
    void everyRenownTypeHasExactlyFive() {
        for (RenownAffix.Group group : RenownAffix.Group.values()) {
            assertEquals(5, RenownAffix.forGroup(group).size(), group.name());
        }
    }

    @Test
    void idsRoundTripAndFitTheReadLimit() {
        for (RenownAffix affix : RenownAffix.values()) {
            assertTrue(affix.id().length() <= RenownAffix.MAX_ID_LENGTH, affix.id());
            assertEquals(Optional.of(affix), RenownAffix.byId(affix.id()));
        }
    }

    @Test
    void unknownOrMalformedIdsGiveNoAffix() {
        assertEquals(Optional.empty(), RenownAffix.byId(null));
        assertEquals(Optional.empty(), RenownAffix.byId(""));
        assertEquals(Optional.empty(), RenownAffix.byId("LAST_STAND"));
        assertEquals(Optional.empty(), RenownAffix.byId("last_stand "));
        assertEquals(Optional.empty(), RenownAffix.byId("x".repeat(500)));
    }

    @Test
    void boonsMapToTheirOwnGroup() {
        assertEquals(Optional.of(RenownAffix.Group.HP_POOL), RenownAffix.groupFor(RenownBoon.HP_POOL));
        assertEquals(Optional.of(RenownAffix.Group.ATTACK), RenownAffix.groupFor(RenownBoon.statFocus("attack")));
        assertEquals(Optional.of(RenownAffix.Group.DEFENCE), RenownAffix.groupFor(RenownBoon.statFocus("defence")));
        assertEquals(Optional.of(RenownAffix.Group.SPECIAL_ATTACK), RenownAffix.groupFor(RenownBoon.statFocus("special_attack")));
        assertEquals(Optional.of(RenownAffix.Group.SPECIAL_DEFENCE), RenownAffix.groupFor(RenownBoon.statFocus("special_defence")));
        assertEquals(Optional.of(RenownAffix.Group.SPEED), RenownAffix.groupFor(RenownBoon.statFocus("speed")));
        assertEquals(Optional.empty(), RenownAffix.groupFor(RenownBoon.NONE));
        assertEquals(Optional.empty(), RenownAffix.groupFor(null));
    }

    @Test
    void rollStaysInTheBoonsGroupAndReachesAllFive() {
        SplittableRandom random = new SplittableRandom(7);
        Set<RenownAffix> seen = EnumSet.noneOf(RenownAffix.class);
        for (int i = 0; i < 500; i++) {
            RenownAffix affix = RenownAffix.roll(RenownBoon.statFocus("speed"), random).orElseThrow();
            assertEquals(RenownAffix.Group.SPEED, affix.group());
            seen.add(affix);
        }
        assertEquals(5, seen.size());
        assertEquals(Optional.empty(), RenownAffix.roll(RenownBoon.NONE, random));
    }

    /** Every id must have a handler in the Showdown module, and the module must not know ids Java does not. */
    @Test
    void showdownModuleCoversExactlyTheseIds() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/assets/cobbleraids/showdown/renown-affixes.js"),
                StandardCharsets.UTF_8);
        int start = js.indexOf("const AFFIXES = {");
        int end = js.indexOf("\n};", start);
        assertTrue(start >= 0 && end > start, "renown-affixes.js must declare `const AFFIXES = {` ... `};`");
        Matcher key = Pattern.compile("(?m)^  ([a-z_]+): \\{").matcher(js.substring(start, end));
        Set<String> inJs = new HashSet<>();
        while (key.find()) inJs.add(key.group(1));
        Set<String> inJava = new HashSet<>();
        for (RenownAffix affix : RenownAffix.values()) inJava.add(affix.id());
        assertEquals(inJava, inJs);
    }
}
