package com.cobbleraids.battle;

import java.util.Set;

/**
 * Battle gimmicks a player may not use in a raid battle, by the id Cobblemon's client sends in
 * MoveActionResponse.gimmickID (ShowdownMoveset.Gimmick: mega, ultra, zmove, max, terastal).
 *
 * <p>Z-Moves are banned. They arrive as an ordinary move response with a gimmick attached, so the
 * move-name ban list alone never sees them.
 *
 * <p>Free of Minecraft types so it can be tested without a battle.
 */
public final class RaidBannedGimmicks {
    /** Cobblemon 1.8.2 ShowdownMoveset.Gimmick.Z_POWER's id. */
    public static final String Z_MOVE = "zmove";

    private static final Set<String> BANNED = Set.of(Z_MOVE);

    private RaidBannedGimmicks() {}

    public static boolean isBanned(String gimmickId) {
        return gimmickId != null && BANNED.contains(gimmickId);
    }
}
