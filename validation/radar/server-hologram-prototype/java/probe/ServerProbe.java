package probe;

import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.generic.GenericBedrockEntity;
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket;
import com.cobblemon.mod.common.net.messages.client.spawn.SpawnGenericBedrockPacket;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Disposable, command-driven experiment. The server never adds the model to the world. */
public final class ServerProbe implements ModInitializer {
    private int entityId;

    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
            var pack = Commands.literal("radarpack").requires(source -> source.hasPermission(2));
            pack.then(Commands.literal("build").executes(context -> {
                boolean ok = eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils.buildMain(
                        java.nio.file.Path.of("radar-projector-smoke.zip"));
                context.getSource().sendSuccess(() -> Component.literal("pack=" + ok), false);
                return ok ? 1 : 0;
            }));
            pack.then(Commands.literal("offer").then(Commands.argument("url", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .executes(context -> {
                        var player = context.getSource().getPlayerOrException();
                        String url = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "url");
                        player.connection.send(new net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket(
                                eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils.getMainUuid(), url, "", false,
                                java.util.Optional.of(Component.literal("Raid projector test pack"))));
                        return 1;
                    })));
            pack.then(Commands.literal("status").executes(context -> {
                var player = context.getSource().getPlayerOrException();
                boolean ready = eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils.hasMainPack(player);
                context.getSource().sendSuccess(() -> Component.literal("accepted=" + ready), false);
                return 1;
            }));
            dispatcher.register(pack);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(
            Commands.literal("radarprototype").requires(source -> source.hasPermission(2))
                .then(Commands.argument("stage", IntegerArgumentType.integer(0, 3)).executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    String result = show(player, IntegerArgumentType.getInteger(context, "stage"));
                    context.getSource().sendSuccess(() -> Component.literal(result), false);
                    return 1;
                }))));
    }

    private String show(ServerPlayer player, int stage) {
        if (stage == 0) {
            if (entityId != 0) player.connection.send(new ClientboundRemoveEntitiesPacket(entityId));
            entityId = 0;
            return "Projector removed";
        }
        if (entityId == 0) {
            var entity = new GenericBedrockEntity(player.serverLevel());
            var look = player.getLookAngle().multiply(1, 0, 1).normalize();
            var origin = player.getEyePosition().add(look.scale(1.85)).add(0, -.67, 0);
            entityId = entity.getId();
            var spawn = new ClientboundAddEntityPacket(entityId, entity.getUUID(),
                origin.x, origin.y, origin.z, 0, 180, entity.getType(), 0, Vec3.ZERO, 180);
            new SpawnGenericBedrockPacket(ResourceLocation.fromNamespaceAndPath("radarprototype", "projector"),
                Set.of(), PoseType.NONE, 1f, 1.5f, 1.5f, 0, spawn).sendToPlayer(player);
        }
        int[] height = new int[256];
        int min = 0, max = 0, known = 4;
        long sampleMicros = 0;
        if (stage == 3) {
            long start = System.nanoTime();
            Arrays.fill(height, Integer.MIN_VALUE);
            var level = player.serverLevel();
            for (int row = 0; row < 16; row++) {
                for (int column = 0; column < 16; column++) {
                    int x = player.getBlockX() + (column - 8) * 16;
                    int z = player.getBlockZ() + (row - 8) * 16;
                    var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                    if (chunk != null) height[row * 16 + column] = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
                }
            }
            min = Arrays.stream(height).filter(h -> h != Integer.MIN_VALUE).min().orElseThrow();
            max = Arrays.stream(height).max().orElseThrow();
            known = (int) Arrays.stream(height).filter(h -> h != Integer.MIN_VALUE).count();
            for (int i = 0; i < height.length; i++) {
                height[i] = height[i] == Integer.MIN_VALUE ? 0 : 1 + (height[i] - min) * 11 / Math.max(1, max - min);
            }
            sampleMicros = (System.nanoTime() - start) / 1000;
        } else {
            height[119] = stage == 1 ? 2 : 12;
            height[120] = 4;
            height[135] = 7;
            height[136] = 10;
        }
        List<String> values = new ArrayList<>();
        for (int i = 0; i < height.length; i++) values.add("v.h" + i + "=" + height[i] + ";");
        values.add("v.markers=" + (stage == 3 ? 1 : 0) + ";");
        new PlayPosableAnimationPacket(entityId, Set.of(), values).sendToPlayer(player);
        int bytes = 7 + values.stream().mapToInt(value -> value.length() + 1).sum();
        return "stage=" + stage + " entity=" + entityId + " known=" + known + " min=" + min
            + " max=" + max + " sample_us=" + sampleMicros + " estimated_payload_bytes=" + bytes;
    }
}
