package com.cobbleraids.worldboss;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /cobbleraids worldboss ...}: players join, leave and check the fight; operators start,
 * stop and reload it.
 *
 * <p>Registered under the shared {@code cobbleraids} root by
 * {@link com.cobbleraids.command.RaidCommands}, which explains why each operator subcommand carries
 * its own permission check: a requirement on a shared root would be discarded when same-named
 * nodes merge.
 */
public final class WorldBossCommand {
    private static final int ADMIN_PERMISSION_LEVEL = 2;

    private WorldBossCommand() {}

    public static void build(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobbleraids")
                .then(Commands.literal("worldboss")
                        .then(Commands.literal("join").executes(WorldBossCommand::join))
                        .then(Commands.literal("leave").executes(WorldBossCommand::leave))
                        .then(Commands.literal("pause").executes(WorldBossCommand::leave))
                        .then(Commands.literal("resume").executes(WorldBossCommand::join))
                        .then(Commands.literal("claim").executes(WorldBossCommand::claim))
                        .then(Commands.literal("scene")
                                .then(Commands.literal("preview").requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                                        .then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> scenePreview(ctx, 1))
                                                .then(Commands.argument("awakening", com.mojang.brigadier.arguments.LongArgumentType.longArg(1)).executes(ctx -> scenePreview(ctx, com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "awakening"))))))
                                .then(Commands.literal("catalogue").requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL)).executes(ctx -> {
                                    for (var script : WorldBossSceneScript.catalogue()) ctx.getSource().sendSuccess(() -> Component.literal(script.id() + " | script_sha256=" + script.fingerprint() + " | maximum recording=" + (script.durationMillis() - 1000) + "ms"), false);
                                    return 1;
                                }))
                                .then(Commands.literal("skip").executes(ctx -> { WorldBossScenes.skip(ctx.getSource().getPlayerOrException()); return 1; }))
                                .then(Commands.literal("voice").then(Commands.argument("volume", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> presentationResult(ctx, WorldBossScenes.options(ctx.getSource().getPlayerOrException(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "volume"), null, null)))))
                                .then(Commands.literal("subtitles")
                                        .then(Commands.literal("on").executes(ctx -> presentationResult(ctx, WorldBossScenes.options(ctx.getSource().getPlayerOrException(), null, true, null))))
                                        .then(Commands.literal("off").executes(ctx -> presentationResult(ctx, WorldBossScenes.options(ctx.getSource().getPlayerOrException(), null, false, null)))))
                                .then(Commands.literal("effects")
                                        .then(Commands.literal("full").executes(ctx -> presentationResult(ctx, WorldBossScenes.options(ctx.getSource().getPlayerOrException(), null, null, WorldBossBenefits.Effects.FULL))))
                                        .then(Commands.literal("minimal").executes(ctx -> presentationResult(ctx, WorldBossScenes.options(ctx.getSource().getPlayerOrException(), null, null, WorldBossBenefits.Effects.MINIMAL))))
                                        .then(Commands.literal("off").executes(ctx -> presentationResult(ctx, WorldBossScenes.options(ctx.getSource().getPlayerOrException(), null, null, WorldBossBenefits.Effects.OFF)))))
                                .then(Commands.literal("transcript").then(Commands.argument("season", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> sceneHistory(ctx, false, null))
                                                .then(Commands.argument("awakening", com.mojang.brigadier.arguments.LongArgumentType.longArg(1)).executes(ctx -> sceneHistory(ctx, false, com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "awakening")))))))
                                .then(Commands.literal("replay").then(Commands.argument("season", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> sceneHistory(ctx, true, null))
                                                .then(Commands.argument("awakening", com.mojang.brigadier.arguments.LongArgumentType.longArg(1)).executes(ctx -> sceneHistory(ctx, true, com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "awakening"))))))))
                        .then(Commands.literal("collection").executes(ctx -> {
                            String problem = WorldheartCollectionMenu.open(ctx.getSource().getPlayerOrException());
                            if (problem != null) { ctx.getSource().sendFailure(Component.literal(problem)); return 0; }
                            return 1;
                        }).then(Commands.literal("attune").then(Commands.argument("season", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(Commands.argument("slot", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 6))
                                        .executes(ctx -> attune(ctx, -1))
                                        .then(Commands.argument("style", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 2))
                                                .executes(ctx -> attune(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "style")))))))
                                .then(Commands.literal("detach").executes(ctx -> presentationResult(ctx, WorldheartPresentation.detach(ctx.getSource().getPlayerOrException()))))
                                .then(Commands.literal("effects")
                                        .then(Commands.literal("full").executes(ctx -> presentationResult(ctx, WorldheartPresentation.effects(ctx.getSource().getPlayerOrException(), "full"))))
                                        .then(Commands.literal("minimal").executes(ctx -> presentationResult(ctx, WorldheartPresentation.effects(ctx.getSource().getPlayerOrException(), "minimal"))))
                                        .then(Commands.literal("off").executes(ctx -> presentationResult(ctx, WorldheartPresentation.effects(ctx.getSource().getPlayerOrException(), "off")))))
                                .then(Commands.literal("legacy")
                                .then(Commands.literal("clear").executes(ctx -> collectionLegacy(ctx, null)))
                                .then(Commands.literal("set").then(Commands.argument("season", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(ctx -> collectionLegacy(ctx, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "season")))))))
                        .then(Commands.literal("ward").then(Commands.literal("relay").executes(ctx -> {
                            String problem = WorldBossWardService.relay(ctx.getSource().getPlayerOrException());
                            if (problem != null) { ctx.getSource().sendFailure(Component.literal(problem)); return 0; }
                            return 1;
                        })).then(Commands.literal("focus").executes(ctx -> {
                            String problem = WorldBossWardService.focus(ctx.getSource().getPlayerOrException());
                            if (problem != null) { ctx.getSource().sendFailure(Component.literal(problem)); return 0; }
                            return 1;
                        })))
                        .then(Commands.literal("warp").executes(WorldBossCommand::warp))
                        .then(Commands.literal("fight").executes(ctx -> {
                            String problem = WorldBossService.fight(ctx.getSource().getPlayerOrException());
                            if (problem != null) { ctx.getSource().sendFailure(Component.literal(problem)); return 0; }
                            return 1;
                        }))
                        .then(Commands.literal("observe").executes(ctx -> {
                            String problem = WorldBossService.observe(ctx.getSource().getPlayerOrException());
                            if (problem != null) { ctx.getSource().sendFailure(Component.literal(problem)); return 0; }
                            return 1;
                        }))
                        .then(Commands.literal("return").executes(WorldBossCommand::returnHome))
                        .then(Commands.literal("arena")
                                .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                                .then(Commands.literal("list").executes(WorldBossCommand::arenaList))
                                .then(Commands.literal("clear").executes(WorldBossCommand::arenaClear))
                                .then(Commands.literal("scan").executes(ctx -> arenaScan(ctx, 64))
                                        .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(8, 128))
                                                .executes(ctx -> arenaScan(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius"))))))
                        .then(Commands.literal("status").executes(WorldBossCommand::status))
                        .then(Commands.literal("me").executes(WorldBossCommand::me))
                        .then(Commands.literal("start")
                                .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                                .executes(WorldBossCommand::start))
                        .then(Commands.literal("preflight")
                                .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                                .executes(ctx -> {
                                    for (String line : WorldBossService.preflight(ctx.getSource().getServer())) ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                                    return 1;
                                }))
                        .then(Commands.literal("season")
                                .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                                .then(Commands.literal("status").executes(ctx -> {
                                    for (String line : WorldBossDailyService.status()) ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                                    return 1;
                                }))
                                .then(Commands.literal("stage")
                                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                        .executes(ctx -> season(ctx, "stage")))))
                                .then(Commands.literal("activate").executes(ctx -> season(ctx, "activate")))
                                .then(Commands.literal("pause").executes(ctx -> season(ctx, "pause")))
                                .then(Commands.literal("resume").executes(ctx -> season(ctx, "resume"))))
                        .then(Commands.literal("test")
                                .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                                .then(Commands.literal("start").executes(WorldBossCommand::startTest)))
                        .then(Commands.literal("stop")
                                .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                                .executes(WorldBossCommand::stop))
                        .then(Commands.literal("control")
                                .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                                .then(Commands.literal("hold").executes(ctx -> control(ctx, false)))
                                .then(Commands.literal("recover").executes(ctx -> control(ctx, true))))
                        .then(Commands.literal("win")
                                .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                                .executes(ctx -> win(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .executes(ctx -> win(ctx, net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                                .executes(WorldBossCommand::reload))));
    }

    private static int join(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String problem = WorldBossService.join(player);
        if (problem != null) {
            player.sendSystemMessage(Component.literal(problem).withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    private static int control(CommandContext<CommandSourceStack> ctx, boolean recover) {
        String problem = recover ? WorldBossService.recoverEvent(ctx.getSource().getServer())
                : WorldBossService.holdEvent(ctx.getSource().getServer());
        com.cobbleraids.RaidLog.info("World Boss control {} requested by {}: {}", recover ? "recover" : "hold",
                ctx.getSource().getTextName(), problem == null ? "queued for acknowledgement" : problem);
        if (problem != null) {
            ctx.getSource().sendFailure(Component.literal(problem));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(recover
                ? "World Boss recovery queued. Regroup begins after durable acknowledgement; HP and remaining combat time are preserved."
                : "World Boss combat is suspended. Hold save queued; status confirms durable acknowledgement."), true);
        return 1;
    }

    private static int season(CommandContext<CommandSourceStack> ctx, String action) {
        var server = ctx.getSource().getServer();
        String problem = switch (action) {
            case "stage" -> WorldBossDailyService.stage(server,
                    com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"),
                    com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name"));
            case "activate" -> WorldBossDailyService.activate(server);
            case "pause" -> WorldBossDailyService.daily(server, false);
            case "resume" -> WorldBossDailyService.daily(server, true);
            default -> throw new IllegalArgumentException("Unknown season action");
        };
        com.cobbleraids.RaidLog.info("World Boss season {} requested by {}: {}", action, ctx.getSource().getTextName(), problem == null ? "committed" : problem);
        if (problem != null) { ctx.getSource().sendFailure(Component.literal(problem)); return 0; }
        ctx.getSource().sendSuccess(() -> Component.literal("Season " + action + " saved. Daily launches use 12:00 and 20:00 America/New_York; missed preparation is skipped."), true);
        return 1;
    }

    private static int warp(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String problem = WorldBossService.warp(player);
        if (problem != null) {
            player.sendSystemMessage(Component.literal(problem).withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    private static int returnHome(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String problem = WorldBossService.returnHome(player);
        if (problem != null) {
            player.sendSystemMessage(Component.literal(problem).withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    /** Admin test tool: wins the active fight, crediting the rest of the pool to the chosen player (default: you). */
    private static int win(CommandContext<CommandSourceStack> ctx, ServerPlayer credit) {
        String problem = WorldBossService.forceWin(ctx.getSource().getServer(), credit);
        if (problem != null) {
            ctx.getSource().sendFailure(Component.literal(problem));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("World Boss won; the remaining damage went to " + credit.getGameProfile().getName()
                + ". Test completed without live rewards or outcome callbacks.").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int arenaList(CommandContext<CommandSourceStack> ctx) {
        var markers = com.cobbleraids.worldboss.arena.ArenaMarkers.get(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("Protected arena natural Pokemon spawns refused this server session: "
                + WorldBossArenaProtection.refusedNaturalSpawns()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Protected arena natural Pokemon position evaluations skipped this server session: "
                + WorldBossArenaProtection.skippedNaturalSpawnEvaluations()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Protected arena natural Pokemon whole-area generations skipped this server session: "
                + WorldBossArenaProtection.skippedNaturalSpawnAreas()), false);
        for (var kind : com.cobbleraids.worldboss.arena.ArenaMarkers.Kind.values()) {
            var spots = markers.list(kind);
            ctx.getSource().sendSuccess(() -> Component.literal(kind.name().toLowerCase() + " markers: " + spots.size()).withStyle(ChatFormatting.YELLOW), false);
            for (var spot : spots) {
                ctx.getSource().sendSuccess(() -> Component.literal("  " + spot.dimension() + " " + spot.pos().getX() + " " + spot.pos().getY() + " "
                        + spot.pos().getZ()), false);
            }
        }
        return markers.list(com.cobbleraids.worldboss.arena.ArenaMarkers.Kind.SPAWN).size();
    }

    private static int collectionLegacy(CommandContext<CommandSourceStack> ctx, String seasonId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException(); var state = WorldBossService.collectionState();
        if (state == null) { ctx.getSource().sendFailure(Component.literal("Worldheart collection is unavailable while saved data needs repair.")); return 0; }
        var season = seasonId == null ? null : state.campaign().seasons().get(seasonId);
        if (seasonId != null && season == null) { ctx.getSource().sendFailure(Component.literal("Unknown archived season.")); return 0; }
        String problem = WorldBossService.selectLegacy(player.server, player.getUUID(), season == null ? null : season.characterId());
        if (problem != null) { ctx.getSource().sendFailure(Component.literal(problem)); return 0; }
        player.sendSystemMessage(Component.literal(season == null ? "Legacy project cleared. Your progress is preserved." : "Legacy project saved: " + season.name() + ". Future qualified victories also advance it.").withStyle(ChatFormatting.GREEN));
        return 1;
    }
    private static int attune(CommandContext<CommandSourceStack> ctx, int style) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return presentationResult(ctx, WorldheartPresentation.attune(ctx.getSource().getPlayerOrException(),
                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "season"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "slot"), style));
    }
    private static int sceneHistory(CommandContext<CommandSourceStack> ctx, boolean replay, Long awakening) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        WorldBossSceneScript.Kind kind;
        try { kind = WorldBossSceneScript.Kind.valueOf(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind").toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { ctx.getSource().sendFailure(Component.literal("Choose entrance, victory or escape.")); return 0; }
        String problem = WorldBossScenes.history(ctx.getSource().getPlayerOrException(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "season"), kind, replay, awakening);
        if (problem != null) { ctx.getSource().sendFailure(Component.literal(problem)); return 0; } return 1;
    }
    private static int scenePreview(CommandContext<CommandSourceStack> ctx, long awakening) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        WorldBossSceneScript.Kind kind;
        try { kind = WorldBossSceneScript.Kind.valueOf(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind").toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { ctx.getSource().sendFailure(Component.literal("Choose entrance, victory or escape.")); return 0; }
        String problem = WorldBossScenes.preview(ctx.getSource().getPlayerOrException(), kind, awakening);
        if (problem != null) { ctx.getSource().sendFailure(Component.literal(problem)); return 0; } return 1;
    }
    private static int presentationResult(CommandContext<CommandSourceStack> ctx, String problem) {
        if (problem != null) { ctx.getSource().sendFailure(Component.literal(problem)); return 0; }
        ctx.getSource().sendSuccess(() -> Component.literal("Worldheart preference saved.").withStyle(ChatFormatting.AQUA), false); return 1;
    }

    private static int arenaClear(CommandContext<CommandSourceStack> ctx) {
        var markers = com.cobbleraids.worldboss.arena.ArenaMarkers.get(ctx.getSource().getServer());
        markers.clear(com.cobbleraids.worldboss.arena.ArenaMarkers.Kind.SPAWN);
        markers.clear(com.cobbleraids.worldboss.arena.ArenaMarkers.Kind.ENTRANCE);
        markers.clear(com.cobbleraids.worldboss.arena.ArenaMarkers.Kind.WARD_PYLON);
        markers.clear(com.cobbleraids.worldboss.arena.ArenaMarkers.Kind.RELAY_CACHE);
        markers.clear(com.cobbleraids.worldboss.arena.ArenaMarkers.Kind.RELAY_CHECKPOINT);
        markers.clear(com.cobbleraids.worldboss.arena.ArenaMarkers.Kind.OBSERVER);
        markers.flush(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("Forgot every arena marker (the blocks stay where they are; /cobbleraids worldboss arena scan finds them again).")
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    /** Finds marker blocks that arrived without being placed by hand (a pasted schematic) in the loaded chunks round the operator and registers them. */
    private static int arenaScan(CommandContext<CommandSourceStack> ctx, int radius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var level = player.serverLevel();
        var markers = com.cobbleraids.worldboss.arena.ArenaMarkers.get(ctx.getSource().getServer());
        String dimension = level.dimension().location().toString();
        int minY = Math.max(level.getMinBuildHeight(), player.getBlockY() - 96), maxY = Math.min(level.getMaxBuildHeight() - 1, player.getBlockY() + 96);
        int added = 0;
        var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        int minX = player.getBlockX() - radius, maxX = player.getBlockX() + radius;
        int minZ = player.getBlockZ() - radius, maxZ = player.getBlockZ() + radius;
        // Walk the loaded chunks and skip every section that is only air: a marker is a block, so it is never in one. A radius of 128 is otherwise
        // some twelve million block reads on the server thread, nearly all of them air.
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                var sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    if (sections[i].hasOnlyAir()) continue;
                    int baseY = (chunk.getMinSection() + i) << 4;
                    if (baseY + 15 < minY || baseY > maxY) continue;
                    for (int x = Math.max(minX, cx << 4); x <= Math.min(maxX, (cx << 4) + 15); x++) {
                        for (int z = Math.max(minZ, cz << 4); z <= Math.min(maxZ, (cz << 4) + 15); z++) {
                            for (int y = Math.max(minY, baseY); y <= Math.min(maxY, baseY + 15); y++) {
                                pos.set(x, y, z);
                                if (level.getBlockState(pos).getBlock() instanceof com.cobbleraids.worldboss.arena.ArenaMarkerBlock marker
                                        && markers.add(marker.kind(), new com.cobbleraids.worldboss.arena.ArenaMarkers.Spot(dimension, pos.immutable()))) added++;
                            }
                        }
                    }
                }
            }
        }
        if (added > 0) markers.flush(ctx.getSource().getServer());
        int found = added;
        ctx.getSource().sendSuccess(() -> Component.literal("Scan done: " + found + " new marker(s) registered (loaded chunks within " + radius + " blocks).")
                .withStyle(ChatFormatting.YELLOW), true);
        return found;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean was = WorldBossService.leave(player);
        var message = Component.literal(was
                        ? "Battles paused. Your current duel will finish normally, and your contribution remains. "
                        : "There is no active World Boss to pause. ").withStyle(ChatFormatting.YELLOW);
        if (was) {
            if (!WorldBossService.controlIsSaved()) message.append(Component.literal("Saving failed; the pause is active in this session. Retry before reconnecting. ")
                    .withStyle(ChatFormatting.RED));
            message.append(Component.literal("[Resume Battles]")
                    .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                    net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/cr worldboss resume"))));
        }
        player.sendSystemMessage(message);
        return was ? 1 : 0;
    }

    private static int claim(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        try {
            int amount = com.cobbleraids.catching.RaidPlayerRecords.claimPendingCredits(player.server, player.getUUID());
            player.sendSystemMessage(Component.literal(amount > 0
                    ? "Claimed " + amount + " pending Raid Points."
                    : "No pending Raid Points fit in your balance. Spend points before retrying if your balance is full.")
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (java.io.IOException | IllegalStateException failure) {
            com.cobbleraids.RaidLog.error("Could not claim pending World Boss points for {}", player.getUUID(), failure);
            player.sendSystemMessage(Component.literal("Your pending rewards could not be saved. They remain owed; try again later.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        for (String line : WorldBossService.status()) {
            ctx.getSource().sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    private static int me(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String share = WorldBossService.myShare(player.getUUID());
        player.sendSystemMessage(Component.literal(share == null
                        ? "There is no World Boss right now." : "You have dealt " + share + ".")
                .withStyle(ChatFormatting.YELLOW));
        return share == null ? 0 : 1;
    }

    private static int start(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer near = ctx.getSource().getPlayer();
        String problem = WorldBossService.start(ctx.getSource().getServer(), near);
        if (problem != null) {
            ctx.getSource().sendFailure(Component.literal("Could not start the World Boss: " + problem));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("World Boss started."), true);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        var result = WorldBossService.stop(ctx.getSource().getServer());
        if (!result.wasActive() && result.swept() == 0) {
            ctx.getSource().sendFailure(Component.literal("No World Boss is active, and no leftover bosses stand at its last spot."));
            return 0;
        }
        String swept = result.swept() == 0 ? "" : " Removed " + result.swept() + " leftover boss(es) at its spot.";
        String message = (result.wasActive() ? "World Boss stopped." : "No World Boss was active.") + swept;
        ctx.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int startTest(CommandContext<CommandSourceStack> ctx) {
        String problem = WorldBossService.startTest(ctx.getSource().getServer(), ctx.getSource().getPlayer());
        if (problem != null) {
            ctx.getSource().sendFailure(Component.literal("World Boss test not started: " + problem));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("World Boss TEST preparation queued. Status confirms durable start. No live rewards or outcome callbacks will be issued.")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        try {
            WorldBossConfig config = WorldBossConfigManager.reload();
            WorldBossService.refreshVisuals(ctx.getSource().getServer());
            ctx.getSource().sendSuccess(() -> Component.literal("World Boss config reloaded (enabled="
                    + config.enabled() + ")."), true);
            return 1;
        } catch (RuntimeException ex) {
            ctx.getSource().sendFailure(Component.literal("World Boss config not reloaded: " + ex.getMessage()));
            return 0;
        }
    }
}
