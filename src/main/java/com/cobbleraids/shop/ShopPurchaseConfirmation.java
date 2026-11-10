package com.cobbleraids.shop;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes a shop purchase take two clicks on the same entry.
 *
 * <p>A cell in the shop used to buy on a single click, and Raid Points cannot be refunded, so a stray click
 * while browsing cost a player something they did not mean to buy. The first click now only marks the entry
 * as awaiting confirmation; a second click on the same entry within {@link #WINDOW_MILLIS} buys it. Clicking
 * anything else, turning the page, or waiting too long starts over.
 *
 * <p>Held on the server rather than in the page, so every view of the shop gets it without each having to
 * build its own dialog, and a view that does nothing about it is still safe. Minecraft-free; the clock is
 * passed in.
 */
public final class ShopPurchaseConfirmation {
    /** How long a first click stays good for. Long enough to read the prompt, short enough not to linger. */
    public static final long WINDOW_MILLIS = 10_000L;

    private record Pending(String entryId, long expiresAt) {}

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    /**
     * Registers a click on {@code entryId}.
     *
     * @return true when this click confirms an earlier one on the same entry, and the purchase should go ahead
     */
    public boolean confirm(UUID player, String entryId, long now) {
        Pending earlier = pending.get(player);
        if (earlier != null && earlier.entryId().equals(entryId) && now <= earlier.expiresAt()) {
            pending.remove(player);
            return true;
        }
        pending.put(player, new Pending(entryId, now + WINDOW_MILLIS));
        return false;
    }

    /** The entry this player has been asked to confirm, or null. */
    public String pending(UUID player, long now) {
        Pending earlier = pending.get(player);
        if (earlier == null) return null;
        if (now > earlier.expiresAt()) {
            pending.remove(player, earlier);
            return null;
        }
        return earlier.entryId();
    }

    public void clear(UUID player) { pending.remove(player); }

    public void clearAll() { pending.clear(); }
}
