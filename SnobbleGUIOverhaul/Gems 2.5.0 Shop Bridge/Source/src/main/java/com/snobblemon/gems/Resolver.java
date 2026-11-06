package com.snobblemon.gems;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Turns "Steve", a UUID, or a Tebex {username}/{id} into a UUID, online or not. Everything that can be answered locally
 * (online players, players the ledger knows, a literal UUID) is answered immediately on the calling thread; only a name nobody here has
 * ever seen goes to Mojang, off the server thread, and the callback then runs back on the server thread.
 */
public final class Resolver {
    private Resolver() {}

    public record Who(UUID uuid, String name) {}

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.]{1,17}");
    private static final Pattern UUID_RX = Pattern.compile("(?i)[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}");

    public static void resolve(MinecraftServer server, Ledger ledger, String input, Consumer<Who> cb) {
        if (input == null) { cb.accept(null); return; }
        String in = input.trim();
        if (UUID_RX.matcher(in).matches()) {
            UUID id = parseUuid(in);
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            String name = online != null ? online.getGameProfile().getName() : ledger.nameOf(id);
            if (name == null) {
                Optional<GameProfile> gp = server.getProfileCache() == null ? Optional.empty() : server.getProfileCache().get(id);
                if (gp.isPresent()) name = gp.get().getName();
            }
            cb.accept(new Who(id, name == null ? in : name));
            return;
        }
        if (!NAME.matcher(in).matches()) { cb.accept(null); return; }
        ServerPlayer online = server.getPlayerList().getPlayerByName(in);
        if (online != null) { cb.accept(new Who(online.getUUID(), online.getGameProfile().getName())); return; }
        UUID known = ledger.uuidByName(in);
        if (known != null) { cb.accept(new Who(known, ledger.nameOf(known))); return; }
        if (!server.usesAuthentication()) {
            cb.accept(new Who(UUIDUtil.createOfflinePlayerUUID(in), in));
            return;
        }
        // online-mode server and an unknown name: ask the profile cache / Mojang without blocking the tick
        server.getProfileCache().getAsync(in).whenComplete((opt, err) -> server.execute(() -> {
            if (err != null || opt == null || opt.isEmpty()) cb.accept(null);
            else cb.accept(new Who(opt.get().getId(), opt.get().getName()));
        }));
    }

    public static UUID parseUuid(String s) {
        String t = s.replace("-", "");
        return UUID.fromString(t.substring(0, 8) + "-" + t.substring(8, 12) + "-" + t.substring(12, 16) + "-" + t.substring(16, 20) + "-" + t.substring(20));
    }

    public static boolean looksLikeUuid(String s) { return s != null && UUID_RX.matcher(s.trim()).matches(); }
}
