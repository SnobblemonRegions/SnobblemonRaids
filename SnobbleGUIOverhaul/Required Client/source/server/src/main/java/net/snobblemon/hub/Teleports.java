package net.snobblemon.hub;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Warp requests from the menus. The server checks cooldown, battle state and a warm-up that cancels when the player
 * moves or is hurt, then runs the configured command AS THE PLAYER, so the warp mod's own permission and safety checks
 * still apply. The menu never teleports anyone itself.
 */
public final class Teleports {
    private record Pending(String label, String command, Vec3 start, float health, long runAtTick) {}

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new ConcurrentHashMap<>();
    private static long tick;

    private Teleports() {}

    public static void request(ServerPlayer p, String label, String command) {
        HubConfig.Teleport cfg = HubConfig.general.teleport;
        if (command == null || command.isBlank()) { say(p, "&cThat destination is not set up yet."); return; }
        if (PENDING.containsKey(p.getUUID())) { say(p, "&cYou are already teleporting."); return; }
        long now = System.currentTimeMillis();
        Long until = COOLDOWN.get(p.getUUID());
        if (until != null && until > now) {
            say(p, "&cYou can teleport again in &e" + ((until - now + 999) / 1000) + "s&c.");
            return;
        }
        if (cfg.block_in_battle && inBattle(p)) { say(p, "&cYou can't teleport during a battle."); return; }
        if (cfg.warmup_seconds <= 0) { run(p, command, label); return; }
        PENDING.put(p.getUUID(), new Pending(label, command, p.position(), p.getHealth(), tick + cfg.warmup_seconds * 20L));
        say(p, "&7Teleporting to &f" + label + "&7 in &e" + cfg.warmup_seconds + "&7 seconds" + (cfg.cancel_on_move ? ". Stand still!" : "."));
    }

    public static void tick(MinecraftServer server) {
        tick++;
        if (PENDING.isEmpty()) return;
        HubConfig.Teleport cfg = HubConfig.general.teleport;
        for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Pending pd = e.getValue();
            if (p == null) { it.remove(); continue; }
            if (p.getHealth() < pd.health) { it.remove(); say(p, "&cTeleport cancelled because you took damage."); continue; }
            if (cfg.cancel_on_move && p.position().distanceToSqr(pd.start) > 0.64) { it.remove(); say(p, "&cTeleport cancelled because you moved."); continue; }
            if (cfg.block_in_battle && inBattle(p)) { it.remove(); say(p, "&cTeleport cancelled: you are in a battle."); continue; }
            if (tick >= pd.runAtTick) { it.remove(); run(p, pd.command, pd.label); }
        }
    }

    private static void run(ServerPlayer p, String command, String label) {
        COOLDOWN.put(p.getUUID(), System.currentTimeMillis() + HubConfig.general.teleport.cooldown_seconds * 1000L);
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(), cmd);
        p.playNotifySound(SoundEvents.ENDERMAN_TELEPORT, SoundSource.MASTER, 0.6f, 1f);
    }

    /** True while the player is in a Cobblemon battle. Reflection keeps Cobblemon an optional dependency. */
    private static boolean inBattle(ServerPlayer p) {
        try {
            Class<?> c = Class.forName("com.cobblemon.mod.common.Cobblemon");
            Object inst = c.getField("INSTANCE").get(null);
            Object reg = c.getMethod("getBattleRegistry").invoke(inst);
            for (Method m : reg.getClass().getMethods()) {
                if (m.getName().equals("getBattleByParticipatingPlayer") && m.getParameterCount() == 1) {
                    return m.invoke(reg, p) != null;
                }
            }
        } catch (Throwable ignored) { }
        return false;
    }

    private static void say(ServerPlayer p, String msg) {
        p.sendSystemMessage(Fmt.parse("&8[&bHub&8] " + msg));
    }
}
