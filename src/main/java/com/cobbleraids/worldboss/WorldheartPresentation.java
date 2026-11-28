package com.cobbleraids.worldboss;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3f;

/** A reversible appearance layer. Never changes Pokemon aspects, forms, stats, shiny data or storage. */
final class WorldheartPresentation {
    private record Scene(UUID entity, int age) {}
    private static final Map<UUID, Scene> scenes = new HashMap<>();
    private static long ticks;
    static void reset() { scenes.clear(); ticks = 0; }
    static String attune(ServerPlayer player, String seasonId, int slot, int style) {
        var state = WorldBossService.collectionState(); if (state == null) return "Worldheart collection is unavailable.";
        var season = state.campaign().seasons().get(seasonId); if (season == null) return "Unknown Worldheart character.";
        var pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(slot - 1);
        if (pokemon == null) return "That party slot is empty.";
        return attune(player, season.characterId(), pokemon.getUuid(), style);
    }
    static String attune(ServerPlayer player, UUID character, UUID pokemonId, int requestedStyle) {
        var state = WorldBossService.collectionState(); if (state == null) return "Worldheart collection is unavailable.";
        var season = state.campaign().seasons().values().stream().filter(candidate -> candidate.characterId().equals(character)).findFirst().orElse(null);
        if (season == null || !season.definition().equals("cobbleraids:giratina")) return "This Worldheart does not have an authored appearance yet.";
        var progress = state.collectionView().progress(player.getUUID(), character);
        if (!progress.owned()) return "Earn this Worldheart before attuning it.";
        int style = requestedStyle < 0 ? progress.style() : requestedStyle; if (style > progress.style() || style < 0 || style > 2) return "That Worldheart style is not unlocked.";
        Pokemon pokemon = partyPokemon(player, pokemonId);
        if (pokemon == null || !player.getUUID().equals(pokemon.getOwnerUUID())) return "Choose a Pokemon you currently own in your party.";
        var species = pokemon.getSpecies().getResourceIdentifier().toString();
        if (!WorldheartEffects.supported(species, WorldheartEffects.APPEARANCE)) return "This appearance supports Lucario, Charizard, Gengar, Eevee, Gardevoir and Dragonite.";
        var bond = new WorldBossBenefits.Attunement(pokemonId, character, species, WorldheartEffects.APPEARANCE, style);
        return WorldBossService.saveBenefits(player.server, player.getUUID(), state.benefits().attune(player.getUUID(), bond));
    }
    static String detach(ServerPlayer player) {
        var state = WorldBossService.collectionState(); if (state == null) return "Worldheart collection is unavailable.";
        return WorldBossService.saveBenefits(player.server, player.getUUID(), state.benefits().attune(player.getUUID(), null));
    }
    static String effects(ServerPlayer player, String mode) {
        var state = WorldBossService.collectionState(); if (state == null) return "Worldheart collection is unavailable.";
        return WorldBossService.saveBenefits(player.server, player.getUUID(), state.benefits().effects(player.getUUID(), WorldBossBenefits.Effects.valueOf(mode.toUpperCase(java.util.Locale.ROOT))));
    }
    private static Pokemon partyPokemon(ServerPlayer player, UUID id) {
        for (var pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) if (pokemon.getUuid().equals(id)) return pokemon; return null;
    }
    static void tick(MinecraftServer server) {
        var state = WorldBossService.collectionState(); if (state == null) { scenes.clear(); return; }
        var players = server.getPlayerList().getPlayers(); if (players.isEmpty()) { scenes.clear(); return; }
        var preferences = state.benefitsView(); var changed = state.benefits(); boolean dirty = false;
        var viewerBudgets = new HashMap<UUID, Integer>(); int budget = 256;
        // Rotate the first owner so the global visual budget does not always favour the same account.
        int first = (int) Math.floorMod(ticks++, players.size()); var observed = new java.util.HashSet<UUID>();
        for (int index = 0; index < players.size(); index++) {
            var owner = players.get((first + index) % players.size()); var id = owner.getUUID(); observed.add(id);
            var bond = preferences.attunements().get(id); if (bond == null) { scenes.remove(id); continue; }
            var pokemon = partyPokemon(owner, bond.pokemon());
            if (pokemon == null || !bond.validFor(id, pokemon.getOwnerUUID(), pokemon.getUuid(), pokemon.getSpecies().getResourceIdentifier().toString())) {
                scenes.remove(id);
                if (changed.attunements().containsKey(id)) { changed = changed.attune(id, null); dirty = true; }
                continue;
            }
            var entity = pokemon.getEntity();
            if (entity == null || entity.isRemoved() || !entity.isAlive() || entity.isBattleClone() || !entity.getPokemon().getUuid().equals(bond.pokemon()) || !id.equals(entity.getPokemon().getOwnerUUID())) { scenes.remove(id); continue; }
            var previous = scenes.get(id); int age = previous == null || !previous.entity().equals(entity.getUUID()) ? 0 : previous.age() == Integer.MAX_VALUE ? 40 : previous.age() + 1;
            scenes.put(id, new Scene(entity.getUUID(), age));
            if (budget <= 0) continue;
            var full = WorldheartEffects.frame(age, bond.style(), entity.getBbWidth(), entity.getBbHeight(), false);
            var minimal = WorldheartEffects.frame(age, bond.style(), entity.getBbWidth(), entity.getBbHeight(), true);
            for (int viewerIndex = 0; viewerIndex < players.size(); viewerIndex++) {
                var viewer = players.get((first + index + viewerIndex) % players.size());
                var mode = preferences.effects(viewer.getUUID()); if (mode == WorldBossBenefits.Effects.OFF || viewer.level() != entity.level() || viewer.distanceToSqr(entity) > 32 * 32) continue;
                for (var mote : mode == WorldBossBenefits.Effects.MINIMAL ? minimal : full) {
                    int used = viewerBudgets.getOrDefault(viewer.getUUID(), 0); if (budget <= 0 || used >= 16) break;
                    var color = mote.accent() ? bond.style() == 2 ? new Vector3f(0.65f, 0.95f, 1f) : new Vector3f(1f, 0.7f, 0.22f) : new Vector3f(0.62f, 0.2f, 0.9f);
                    viewer.serverLevel().sendParticles(viewer, new DustParticleOptions(color, 0.7f), false, entity.getX() + mote.x(), entity.getY() + mote.y(), entity.getZ() + mote.z(), 1, 0, 0, 0, 0);
                    viewerBudgets.put(viewer.getUUID(), used + 1); budget--;
                }
            }
        }
        scenes.keySet().retainAll(observed);
        if (dirty) {
            state.benefits(changed); WorldBossService.mechanicsChanged();
            if (WorldBossService.flushMechanics(server)) for (var player : players) if (preferences.attunements().containsKey(player.getUUID()) && !changed.attunements().containsKey(player.getUUID()))
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Worldheart detached: its Pokemon changed or left your party. Your Worldheart remains owned.").withStyle(ChatFormatting.AQUA));
        }
    }
    private WorldheartPresentation() {}
}
