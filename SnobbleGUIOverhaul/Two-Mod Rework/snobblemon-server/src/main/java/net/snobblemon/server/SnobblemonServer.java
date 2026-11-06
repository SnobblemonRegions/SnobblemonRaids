package net.snobblemon.server;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.snobblemon.proto.SnobbleProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Snobblemon Server: delivers the server's GUI art through the existing Polymer resource pack, and server-defined menus to
 * Snobblemon Client. Authoritative: the client only asks, this mod checks and decides.
 */
public final class SnobblemonServer implements ModInitializer {
    public static final String MOD_ID = "snobblemon_server";
    public static final Logger LOGGER = LoggerFactory.getLogger("SnobblemonServer");

    @Override
    public void onInitialize() {
        SnobbleProto.registerTypes();
        ThemePack.init();
        GuiService.init();
        PageViews.register();
        SnobbleUi.reload();
        SnobbleUi.initTick();

        ServerPlayNetworking.registerGlobalReceiver(SnobbleProto.Hello.TYPE, (p, ctx) -> ctx.server().execute(() -> GuiService.onHello(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(SnobbleProto.PackState.TYPE, (p, ctx) -> ctx.server().execute(() -> GuiService.onPackState(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(SnobbleProto.GuiAction.TYPE, (p, ctx) -> ctx.server().execute(() -> GuiService.onAction(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(SnobbleProto.UiMsg.TYPE, (p, ctx) -> ctx.server().execute(() -> SnobbleUi.onMsg(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(SnobbleProto.SpriteReq.TYPE, (p, ctx) -> ctx.server().execute(() -> SpriteService.onReq(ctx.player(), p)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> { GuiService.forget(handler.getPlayer().getUUID()); SnobbleUi.forget(handler.getPlayer().getUUID()); SpriteService.forget(handler.getPlayer().getUUID()); });

        CommandRegistrationCallback.EVENT.register((d, access, env) -> {
            d.register(Commands.literal("snobblemon")
                    .then(Commands.literal("demo").executes(c -> {
                        ServerPlayer pl = c.getSource().getPlayerOrException();
                        var r = GuiService.open(pl, "demo", false);
                        c.getSource().sendSystemMessage(Component.literal(r == GuiService.OpenResult.OK ? "Opened the demo menu." : "Could not open the menu: " + r));
                        return r == GuiService.OpenResult.OK ? 1 : 0;
                    }).then(Commands.literal("force").requires(s -> s.hasPermission(3)).executes(c -> {
                        ServerPlayer pl = c.getSource().getPlayerOrException();
                        var r = GuiService.open(pl, "demo", true);
                        c.getSource().sendSystemMessage(Component.literal("Forced open: " + r));
                        return r == GuiService.OpenResult.OK ? 1 : 0;
                    })))
                    .then(Commands.literal("status").requires(s -> s.hasPermission(3)).executes(c -> {
                        c.getSource().sendSystemMessage(Component.literal(GuiService.status()));
                        return 1;
                    }))
                    .then(Commands.literal("uisample").requires(s -> s.hasPermission(3) && System.getProperty("snobble.rig") != null)
                            .then(Commands.argument("page", com.mojang.brigadier.arguments.StringArgumentType.word())
                                    .then(Commands.argument("event", com.mojang.brigadier.arguments.StringArgumentType.word())
                                            .then(Commands.argument("file", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                                                // TEST RIGS ONLY (needs -Dsnobble.rig): replays a saved payload into the page the player has open, so screens can be checked with filled-in data
                                                ServerPlayer pl = c.getSource().getPlayerOrException();
                                                String file = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "file");
                                                try {
                                                    String json = java.nio.file.Files.readString(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("snobblemon").resolve("samples").resolve(file + ".json"));
                                                    SnobbleUi.emitJson(pl, com.mojang.brigadier.arguments.StringArgumentType.getString(c, "page"), com.mojang.brigadier.arguments.StringArgumentType.getString(c, "event"), json);
                                                    return 1;
                                                } catch (java.io.IOException e) {
                                                    c.getSource().sendSystemMessage(Component.literal("No sample " + file));
                                                    return 0;
                                                }
                                            })))))
                    .then(Commands.literal("uiopen").requires(s -> s.hasPermission(3) && System.getProperty("snobble.rig") != null)
                            .then(Commands.argument("page", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                                // TEST RIGS ONLY (needs -Dsnobble.rig): opens a native page directly, without the mod that normally opens it
                                ServerPlayer pl = c.getSource().getPlayerOrException();
                                boolean ok = SnobbleUi.open(pl, com.mojang.brigadier.arguments.StringArgumentType.getString(c, "page"));
                                c.getSource().sendSystemMessage(Component.literal(ok ? "Opened." : "Could not open that page."));
                                return ok ? 1 : 0;
                            })))
                    .then(Commands.literal("uihud").requires(s -> s.hasPermission(3) && System.getProperty("snobble.rig") != null)
                            .then(Commands.argument("page", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                                // TEST RIGS ONLY (needs -Dsnobble.rig): shows a HUD page directly
                                ServerPlayer pl = c.getSource().getPlayerOrException();
                                boolean ok = SnobbleUi.openHud(pl, com.mojang.brigadier.arguments.StringArgumentType.getString(c, "page"));
                                c.getSource().sendSystemMessage(Component.literal(ok ? "HUD shown." : "Could not show that HUD page."));
                                return ok ? 1 : 0;
                            })))
                    .then(Commands.literal("reload").requires(s -> s.hasPermission(3)).executes(c -> {
                        c.getSource().sendSystemMessage(Component.literal(GuiService.reload() + " | " + SnobbleUi.reload() + " | " + HubViews.reload()));
                        return 1;
                    })));
        });
        LOGGER.info("Snobblemon Server loaded (protocol {})", SnobbleProto.PROTOCOL);
    }
}
