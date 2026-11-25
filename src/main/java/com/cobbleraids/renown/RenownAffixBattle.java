package com.cobbleraids.renown;

import com.cobbleraids.config.CobbleRaidsConfig;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Which affixes run in one battle, and how they travel to Showdown.
 *
 * <p>They ride the {@code >start} payload's format object as {@code cobbleAffixes}: Pokemon UUID to affix
 * id, the UUID being the one Showdown already knows each Pokemon by ({@code BattlePokemon.getUuid()},
 * which is {@code pokemon.uuid} in the simulator). {@code renown-affixes.js} reads that map and nothing
 * else, so the only things a battle can learn about affixes come from server-side Pokemon data here.
 *
 * <p>Pure, so the rules are unit-tested; the mixin only gathers the entries.
 */
public final class RenownAffixBattle {

    /** The format field name. Must not start with "raid", which ShowdownFormatExtras reserves. */
    public static final String FIELD = "cobbleAffixes";

    public enum Kind { RAID, PVP, OTHER }

    /** One Pokemon in the battle that has an affix. {@code raidBoss} only for the boss of a raid. */
    public record Entry(UUID pokemonId, RenownAffix affix, boolean raidBoss) {}

    private RenownAffixBattle() {}

    /** Pokemon UUID to affix id for the entries allowed to run, in the order given. */
    public static Map<String, String> allowed(Kind kind, List<Entry> entries, CobbleRaidsConfig.Renown.Affixes config) {
        Map<String, String> out = new LinkedHashMap<>();
        if (config == null || !config.enabled()) return out;
        for (Entry entry : entries) {
            if (entry == null || entry.pokemonId() == null || entry.affix() == null) continue;
            if (runs(kind, entry.raidBoss(), config)) out.putIfAbsent(entry.pokemonId().toString(), entry.affix().id());
        }
        return out;
    }

    static boolean runs(Kind kind, boolean raidBoss, CobbleRaidsConfig.Renown.Affixes config) {
        if (raidBoss) return kind == Kind.RAID;
        return switch (kind) {
            case RAID -> config.ownedInRaids();
            case PVP -> config.ownedInPvp();
            case OTHER -> config.ownedInOtherBattles();
        };
    }

    /** The field's raw JSON value, or null when nothing runs (then no field is added at all). */
    public static String toJson(Map<String, String> allowed) {
        if (allowed.isEmpty()) return null;
        JsonObject object = new JsonObject();
        allowed.forEach(object::addProperty);
        return object.toString();
    }
}
