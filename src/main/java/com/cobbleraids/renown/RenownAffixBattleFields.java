package com.cobbleraids.renown;

import com.cobbleraids.config.CobbleRaidsConfigManager;
import com.cobbleraids.showdown.ShowdownFormatExtras;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Gathers the affixes in a battle that is about to start and adds them to its {@code >start} payload.
 *
 * <p>Thin on purpose: which ones run is {@link RenownAffixBattle}'s decision. Read from the server's own
 * Pokemon, never from anything a client sent. A raid fights with clones of the players' Pokemon
 * (toBattleTeam(clone = true)), so the original is checked first and the clone second; the key is always
 * the battle Pokemon's own UUID, the one Showdown knows it by.
 */
public final class RenownAffixBattleFields {

    private RenownAffixBattleFields() {}

    /** Called from the startShowdown mixin inside its fault barrier; returns {@code messages} when nothing runs. */
    public static String[] apply(PokemonBattle battle, String[] messages, boolean raid) {
        RenownAffixBattle.Kind kind = raid ? RenownAffixBattle.Kind.RAID
                : battle.isPvP() ? RenownAffixBattle.Kind.PVP : RenownAffixBattle.Kind.OTHER;

        Set<BattleActor> bossActors = new HashSet<>();
        if (raid) for (BattleActor actor : battle.getSide2().getActors()) bossActors.add(actor);

        List<RenownAffixBattle.Entry> entries = new ArrayList<>();
        for (BattleActor actor : battle.getActors()) {
            boolean boss = bossActors.contains(actor);
            for (BattlePokemon pokemon : actor.getPokemonList()) {
                Optional<RenownAffix> affix = RenownAffixData.read(pokemon.getOriginalPokemon())
                        .or(() -> RenownAffixData.read(pokemon.getEffectedPokemon()));
                affix.ifPresent(found -> entries.add(new RenownAffixBattle.Entry(pokemon.getUuid(), found, boss)));
            }
        }
        if (entries.isEmpty()) return messages;

        String json = RenownAffixBattle.toJson(RenownAffixBattle.allowed(kind, entries,
                CobbleRaidsConfigManager.get().renown().affixes()));
        if (json == null) return messages;
        return ShowdownFormatExtras.apply(messages, battle.getBattleId(), List.of(),
                List.of((battleId, players) -> Map.of(RenownAffixBattle.FIELD, json)));
    }
}
