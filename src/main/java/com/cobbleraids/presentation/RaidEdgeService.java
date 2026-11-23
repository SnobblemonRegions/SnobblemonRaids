package com.cobbleraids.presentation;

import com.cobbleraids.config.CobbleRaidsConfig;
import com.cobbleraids.config.CobbleRaidsConfigManager;
import com.cobbleraids.config.RaidRarityTier;
import com.cobbleraids.duel.RaidDuelService;
import com.cobbleraids.lobby.RaidLobbyManager;
import com.cobbleraids.worldboss.WorldBossBorder;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3f;

/**
 * Draws a raid's edge as a ring of dust in its Rarity Tier's colour, sent only to that raid's own players.
 *
 * <ul>
 *   <li>Lobby: the recruitment radius, drawn whole for each player who joined, who is told to step inside while they stand outside
 *       it (outside it when the Lobby locks, they are left out of the raid).</li>
 *   <li>Raid: the 48-block leash, drawn near a player once they are within {@code show_within_blocks} of it, with an action-bar
 *       warning within {@code warn_within_blocks}. Crossing it still forfeits at once ({@code RaidDuelService}); this only warns.</li>
 * </ul>
 *
 * Vanilla particles sent from the server, so an unmodded client sees it. Runs every {@link #INTERVAL_TICKS}; the geometry is
 * {@link WorldBossBorder} and the thresholds {@link RaidEdge}, both tested without a server.
 */
public final class RaidEdgeService {

    /** One ring to draw: around {@code boss}, at {@code radius}, for {@code players} only. */
    public record Ring(PokemonEntity boss, RaidRarityTier tier, double radius, List<UUID> players) {}

    static final int INTERVAL_TICKS = 10;
    private static long ticks;

    private RaidEdgeService() {}

    public static void tick(MinecraftServer server) {
        if (++ticks % INTERVAL_TICKS != 0) return;
        CobbleRaidsConfig.RaidEdge config = CobbleRaidsConfigManager.get().raidEdge();
        if (!config.enabled()) return;
        for (Ring ring : RaidLobbyManager.edgeRings()) draw(server, ring, config, true);
        for (Ring ring : RaidDuelService.edgeRings()) draw(server, ring, config, false);
    }

    private static void draw(MinecraftServer server, Ring ring, CobbleRaidsConfig.RaidEdge config, boolean lobby) {
        PokemonEntity boss = ring.boss();
        if (boss.isRemoved() || !(boss.level() instanceof ServerLevel level)) return;
        DustParticleOptions dust = dust(ring.tier());
        double view = RaidEdge.viewDistance(ring.radius(), config.showWithinBlocks());
        for (UUID id : ring.players()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || player.level() != level || player.isSpectator()) continue;
            // The same 3D distance the leash and the Lobby's lock-in test use, so the warning and the rule agree.
            double distance = player.distanceTo(boss);
            RaidEdge.Zone zone = RaidEdge.zone(distance, ring.radius(), config.showWithinBlocks(), config.warnWithinBlocks());
            if (!lobby && zone == RaidEdge.Zone.CLEAR) continue;

            for (double[] column : WorldBossBorder.columns(boss.getX(), boss.getZ(), ring.radius(), player.getX(), player.getZ(),
                    view, config.particleSpacing())) {
                int x = (int) Math.floor(column[0]);
                int z = (int) Math.floor(column[1]);
                // Never load a chunk to draw a particle: an unloaded column is drawn at the player's height instead.
                double y = level.hasChunkAt(x, z) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 0.4 : player.getY() + 0.4;
                level.sendParticles(player, dust, true, column[0], y, column[1], 2, 0.05, 0.35, 0.05, 0.0);
            }
            Component warning = lobby ? lobbyMessage(zone) : raidMessage(zone, RaidEdge.blocksLeft(distance, ring.radius()));
            if (warning != null) player.displayClientMessage(warning, true);
        }
    }

    private static Component lobbyMessage(RaidEdge.Zone zone) {
        if (zone != RaidEdge.Zone.OUTSIDE) return null;
        return Component.literal("Step inside the ring to be in the raid when it starts.").withStyle(ChatFormatting.GOLD);
    }

    private static Component raidMessage(RaidEdge.Zone zone, int blocksLeft) {
        return switch (zone) {
            case WARN -> Component.literal("Raid edge: " + blocksLeft + (blocksLeft == 1 ? " block" : " blocks")
                    + " left. Go further and you forfeit the raid.").withStyle(ChatFormatting.YELLOW);
            case OUTSIDE -> Component.literal("You are past the raid edge. Get back inside or you forfeit the raid.")
                    .withStyle(ChatFormatting.RED);
            default -> null;
        };
    }

    /** Dust in the tier's chat colour, so the ring matches the boss's glow and name. */
    private static DustParticleOptions dust(RaidRarityTier tier) {
        Integer rgb = RaidTierPresentation.color(tier).getColor();
        int c = rgb == null ? 0xFFFFFF : rgb;
        return new DustParticleOptions(new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f), 1.2f);
    }
}
