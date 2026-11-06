package net.snobblemon.hub;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.snobblemon.hub.gui.HubGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SnobbleHub implements ModInitializer {
    public static final String MOD_ID = "snobblehub";
    public static final Logger LOGGER = LoggerFactory.getLogger("SnobbleHub");

    private static final Map<UUID, Long> LAST_OPEN = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        HubFont.load();
        HubIcons.init();
        HubConfig.load();
        ClientBridge.init();
        ClientGate.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(Teleports::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> HubConfig.saveState());
        LOGGER.info("Snobble Trainer Hub ready (F key {}).", HubConfig.general.f_key_opens_hub ? "opens the hub" : "left alone");
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> hub = Commands.literal("hub").executes(c -> open(c.getSource(), HubGui.Mode.HUB));
        hub.then(Commands.literal("wiki").executes(c -> open(c.getSource(), HubGui.Mode.WIKI)));
        hub.then(Commands.literal("card").executes(c -> open(c.getSource(), HubGui.Mode.CARD)));
        hub.then(Commands.literal("cosmetics").executes(c -> open(c.getSource(), HubGui.Mode.COSMETICS)));
        hub.then(Commands.literal("warps").executes(c -> open(c.getSource(), HubGui.Mode.WARPS)));
        hub.then(Commands.literal("gyms").executes(c -> open(c.getSource(), HubGui.Mode.GYMS)));
        hub.then(Commands.literal("shop").executes(c -> open(c.getSource(), HubGui.Mode.SHOP)));
        hub.then(Commands.literal("updates").executes(c -> open(c.getSource(), HubGui.Mode.UPDATES)));
        hub.then(Commands.literal("reload")
                .requires(s -> Permissions.check(s, "snobblehub.reload", 3))
                .executes(c -> {
                    HubConfig.load();
                    c.getSource().sendSuccess(() -> Fmt.parse("&aSnobble Hub config reloaded."), false);
                    return 1;
                }));
        d.register(hub);
        d.register(Commands.literal("updates").executes(c -> open(c.getSource(), HubGui.Mode.UPDATES)));
        d.register(Commands.literal("trainercard").executes(c -> open(c.getSource(), HubGui.Mode.CARD)));
    }

    private static int open(CommandSourceStack src, HubGui.Mode mode) {
        ServerPlayer p = src.getPlayer();
        if (p == null) {
            src.sendFailure(Fmt.parse("&cOnly players can open the hub."));
            return 0;
        }
        openHub(p, mode);
        return 1;
    }

    /** Opens a page: the owo-ui screen for players with the client mod, the chest menu for everyone else. */
    public static void openHub(ServerPlayer p, HubGui.Mode mode) {
        if (!ClientBridge.open(p, mode)) HubGui.open(p, mode);
    }

    /** Called from the swap-hands packet. Returns true when the packet was used to open the hub. */
    public static boolean onSwapHands(ServerPlayer p) {
        HubConfig.General g = HubConfig.general;
        if (!g.f_key_opens_hub) return false;
        if (g.sneak_f_swaps_hands && p.isShiftKeyDown()) return false;
        if (p.containerMenu != p.inventoryMenu) return true;   // another menu is open: just swallow the key
        long now = System.currentTimeMillis();
        Long last = LAST_OPEN.get(p.getUUID());
        if (last != null && now - last < g.open_cooldown_ms) return true;
        LAST_OPEN.put(p.getUUID(), now);
        openHub(p, HubGui.Mode.HUB);
        return true;
    }
}
