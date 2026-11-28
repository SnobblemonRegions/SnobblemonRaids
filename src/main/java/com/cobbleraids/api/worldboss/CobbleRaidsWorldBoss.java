package com.cobbleraids.api.worldboss;

import com.cobbleraids.worldboss.WorldBossService;
import java.util.UUID;

/**
 * The surface another mod uses to follow World Boss events (for example to pay its own reward).
 *
 * <p>Signatures use only {@code java.*}, {@code net.minecraft.*} and this package, the same rule as
 * {@code com.cobbleraids.api.encounter}. Experimental, {@link #API_VERSION} 2.
 */
public final class CobbleRaidsWorldBoss {

    /** Incremented on any incompatible change to this package. */
    public static final int API_VERSION = 2;

    private CobbleRaidsWorldBoss() {}

    /** Registers a listener for the life of the server. Registering the same instance twice is a no-op. */
    public static void addListener(WorldBossListener listener) {
        WorldBossService.addListener(listener);
    }

    /** Removes a listener added with {@link #addListener}. */
    public static void removeListener(WorldBossListener listener) {
        WorldBossService.removeListener(listener);
    }

    /**
     * The immutable recipients of a secured payout, including qualified support players with no
     * damage. Call on the server thread from {@link WorldBossListener#onDefeated}; the frozen
     * journal promise, rather than a second integration-specific damage threshold, is authoritative.
     * Historical secured payouts remain queryable while retained in the journal.
     *
     * @throws IllegalStateException if the payout is not secured or recovery is unavailable
     */
    public static java.util.Set<UUID> qualifiedPlayers(UUID bossId) {
        return WorldBossService.qualifiedPlayers(bossId);
    }

    /** True when the World Boss system is switched on ({@code enabled} in {@code world_boss.json}); {@link #start} refuses otherwise. */
    public static boolean isEnabled() {
        return com.cobbleraids.worldboss.WorldBossConfigManager.get().enabled();
    }

    /**
     * Legacy manual-live-start entry point. API version 2 retains its binary signature but refuses
     * unscheduled live starts: staff stage/activate seasons and the daily scheduler reserves each
     * rewarded occurrence. The returned reason explains the policy. Use the operator test command
     * in an isolated world for rehearsals. External integrations should observe events through listeners.
     *
     * @param near retained for binary compatibility; ignored
     * @return a non-null reason that unscheduled live starts are disabled
     */
    @Deprecated
    public static String start(net.minecraft.server.MinecraftServer server, net.minecraft.server.level.ServerPlayer near) {
        return WorldBossService.start(server, near);
    }

    /** True while a World Boss is active. */
    public static boolean isActive() {
        return WorldBossService.isActive();
    }

    /** The active World Boss's id, or null when none is active. */
    public static UUID activeBossId() {
        return WorldBossService.activeBossId();
    }
}
