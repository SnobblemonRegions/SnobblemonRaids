package com.cobbleraids.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.arena.ArenaMarkers;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.entity.SpawnEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Cached regions only on native hot paths: no chunk queries, scans or SavedData loads there. */
public final class WorldBossArenaProtection {
    private static WorldBossArenaRegion configured, active;
    private static java.util.UUID event;
    private static volatile List<WorldBossArenaRegion> regions = List.of();
    private static boolean registered;
    private static boolean maintenanceLocked = true;
    private static final java.util.concurrent.atomic.AtomicLong refusedNaturalSpawns = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong skippedNaturalSpawnEvaluations = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong skippedNaturalSpawnAreas = new java.util.concurrent.atomic.AtomicLong();
    private WorldBossArenaProtection() {}

    static void refresh(MinecraftServer server, WorldBossState state) {
        var config = WorldBossConfigManager.get();
        boolean running = state != null && state.active() && state.arena();
        maintenanceLocked = state == null || state.active() || state.isQuarantined();
        if (running) {
            double radius = WorldBossRules.zoneRadius(state, config) + WorldBossChunkLease.PLACEMENT_MARGIN;
            if (active != null && state.bossId().equals(event)) radius = Math.max(active.radius(), radius);
            active = new WorldBossArenaRegion(state.dimension(), state.x(), state.z(), radius); event = state.bossId();
        } else { active = null; event = null; }
        publish(); // A bad marker refresh must not drop the valid persisted event's protection.
        if (config.enabled() && config.arena().enabled()) {
            var markers = ArenaMarkers.get(server).list(ArenaMarkers.Kind.SPAWN);
            if (markers.size() == 1) {
                var marker = markers.getFirst();
                configured = new WorldBossArenaRegion(marker.dimension(), marker.pos().getX() + 0.5, marker.pos().getZ() + 0.5, config.arena().radius() + WorldBossChunkLease.PLACEMENT_MARGIN);
            }
            // A malformed/removed marker set must not silently drop the last known protection.
        } else if (!running) configured = null;
        publish();
    }
    private static void publish() {
        regions = active == null ? configured == null ? List.of() : List.of(configured)
                : configured == null || configured.equals(active) ? List.of(active) : List.of(active, configured);
    }
    static void reset() { configured = null; active = null; event = null; regions = List.of(); maintenanceLocked = true; refusedNaturalSpawns.set(0); skippedNaturalSpawnEvaluations.set(0); skippedNaturalSpawnAreas.set(0); }

    public static long refusedNaturalSpawns() { return refusedNaturalSpawns.get(); }
    public static long skippedNaturalSpawnEvaluations() { return skippedNaturalSpawnEvaluations.get(); }
    public static long skippedNaturalSpawnAreas() { return skippedNaturalSpawnAreas.get(); }
    /** Native-only whole-zone rejection. Mixed pools and custom spawners/resolvers retain their normal path. */
    public static boolean skipNaturalSpawnArea(com.cobblemon.mod.common.api.spawning.spawner.Spawner spawner,
            com.cobblemon.mod.common.api.spawning.spawner.SpawningZoneInput area) {
        var snapshot = regions;
        if (snapshot.isEmpty() || spawner.getClass() != com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawner.class
                || area.getCause().getSpawner() != spawner || area.getHeight() <= 0
                || spawner.getGenerator() != com.cobblemon.mod.common.api.spawning.CobblemonSpawningZoneGenerator.INSTANCE
                || !spawner.getResolver().getClass().getName().equals("com.cobblemon.mod.common.Cobblemon$areaSpawnablePositionResolver$1")) return false;
        var world = area.getWorld();
        if (!world.getServer().isSameThread()) return false;
        String dimension = world.dimension().location().toString();
        boolean contained = false;
        for (var region : snapshot) if (region.containsSpawnArea(dimension, area.getBaseX(), area.getBaseZ(), area.getLength(), area.getWidth())) { contained = true; break; }
        if (!contained) return false;
        // An extension may resolve positions beyond the native zone: retain its complete original pass.
        for (var calculator : com.cobblemon.mod.common.api.spawning.position.calculators.SpawnablePositionCalculator.Companion.getPrioritizedAreaCalculators()) {
            String type = calculator.getClass().getName();
            if (!type.equals("com.cobblemon.mod.common.api.spawning.position.calculators.SurfaceSpawnablePositionCalculator")
                    && !type.equals("com.cobblemon.mod.common.api.spawning.position.calculators.GroundedSpawnablePositionCalculator")
                    && !type.equals("com.cobblemon.mod.common.api.spawning.position.calculators.SeafloorSpawnablePositionCalculator")
                    && !type.equals("com.cobblemon.mod.common.api.spawning.position.calculators.LavafloorSpawnablePositionCalculator")
                    && !type.equals("com.cobblemon.mod.common.api.spawning.position.calculators.SubmergedSpawnablePositionCalculator")) return false;
        }
        if (!onlySuppressedSpawnDetails(spawner.getSpawnPool().getDetails())) return false;
        skippedNaturalSpawnAreas.incrementAndGet();
        return true;
    }
    static boolean onlySuppressedSpawnDetails(List<com.cobblemon.mod.common.api.spawning.detail.SpawnDetail> details) {
        if (details.isEmpty()) return false;
        // Inspect the live list each time: reloads and additions cannot leave a stale "Pokemon only" cache.
        for (var detail : details) if (!(detail instanceof com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail)
                && !(detail instanceof com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail)
                && !(detail instanceof com.cobblemon.mod.common.api.spawning.detail.EmptySpawnDetail)) return false;
        return true;
    }
    public static void recordSkippedNaturalSpawnEvaluation() { skippedNaturalSpawnEvaluations.incrementAndGet(); }
    /** Conservative early predicate: all candidate-block corners must already lie in one protected column. */
    public static boolean protectsNaturalSpawnCell(ServerLevel level, BlockPos position) {
        var snapshot = regions;
        if (snapshot.isEmpty()) return false;
        String dimension = level.dimension().location().toString();
        for (var region : snapshot) if (region.contains(dimension, position.getX(), position.getZ(), position.getX() + 1.0, position.getZ() + 1.0)) return true;
        return false;
    }

    public static boolean protects(Level level, BlockPos pos) { return protects(level, pos, 0); }
    public static boolean protects(Level level, BlockPos pos, double padding) {
        if (!(level instanceof ServerLevel) || regions.isEmpty()) return false;
        String dimension = level.dimension().location().toString();
        for (var region : regions) if (region.intersects(dimension, pos.getX(), pos.getZ(), pos.getX() + 1.0, pos.getZ() + 1.0, padding)) return true;
        return false;
    }
    public static boolean protects(Entity entity) {
        if (!(entity.level() instanceof ServerLevel) || regions.isEmpty()) return false;
        var box = entity.getBoundingBox(); String dimension = entity.level().dimension().location().toString();
        for (var region : regions) if (region.intersects(dimension, box.minX, box.minZ, box.maxX, box.maxZ, 0)) return true;
        return false;
    }
    private static boolean maintenance(Player player) { return !maintenanceLocked && player instanceof ServerPlayer server && server.isCreative() && server.hasPermissions(2); }
    private static boolean deny(Player player, Level level, BlockPos target) { return !maintenance(player) && (protects(player) || protects(level, target)); }

    public static void register() {
        if (registered) return; registered = true;
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(WorldBossArenaProtection::onNaturalSpawn);
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, block, entity) -> {
            try { return !deny(player, world, pos); }
            catch (RuntimeException failure) { RaidFaultBarrier.report("worldboss:break", failure); return false; }
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, face) -> {
            try { return deny(player, level, pos) ? InteractionResult.FAIL : InteractionResult.PASS; }
            catch (RuntimeException failure) { RaidFaultBarrier.report("worldboss:attack-block", failure); return InteractionResult.FAIL; }
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            try { return deny(player, level, hit.getBlockPos()) || deny(player, level, hit.getBlockPos().relative(hit.getDirection())) ? InteractionResult.FAIL : InteractionResult.PASS; }
            catch (RuntimeException failure) { RaidFaultBarrier.report("worldboss:use-block", failure); return InteractionResult.FAIL; }
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            try {
                var item = player.getItemInHand(hand);
                boolean food = item.has(net.minecraft.core.component.DataComponents.FOOD) && !item.is(net.minecraft.world.item.Items.CHORUS_FRUIT);
                return !maintenance(player) && protects(player) && !food ? InteractionResultHolder.fail(item) : InteractionResultHolder.pass(item);
            }
            catch (RuntimeException failure) { RaidFaultBarrier.report("worldboss:use-item", failure); return InteractionResultHolder.fail(net.minecraft.world.item.ItemStack.EMPTY); }
        });
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            try { return !maintenance(player) && (protects(player) || protects(entity)) ? InteractionResult.FAIL : InteractionResult.PASS; }
            catch (RuntimeException failure) { RaidFaultBarrier.report("worldboss:attack-entity", failure); return InteractionResult.FAIL; }
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            try { return (entity instanceof ItemFrame || entity instanceof ArmorStand) && !maintenance(player) && (protects(player) || protects(entity)) ? InteractionResult.FAIL : InteractionResult.PASS; }
            catch (RuntimeException failure) { RaidFaultBarrier.report("worldboss:use-decoration", failure); return InteractionResult.FAIL; }
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            try { return !protects(entity); }
            catch (RuntimeException failure) { RaidFaultBarrier.report("worldboss:native-damage", failure); return false; }
        });
    }

    /** SpawnAction emits before world insertion; party send-outs and direct commands use another path. */
    private static void onNaturalSpawn(SpawnEvent<PokemonEntity> spawn) {
        try {
            if (spawn.getEntity().getOwnerUUID() == null && protects(spawn.getEntity())) {
                spawn.cancel();
                refusedNaturalSpawns.incrementAndGet();
            }
        } catch (RuntimeException failure) {
            RaidFaultBarrier.report("worldboss:natural-spawn", failure);
            spawn.cancel();
        }
    }
}
