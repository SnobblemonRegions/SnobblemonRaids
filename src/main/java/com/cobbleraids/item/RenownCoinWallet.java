package com.cobbleraids.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Renown Coins a player can spend: the ones in their inventory (main, hotbar and off hand). Coins pressed into the Coin Album live
 * server-side and are not counted, so a player only ever spends what they chose to keep out of the album.
 *
 * <p>Server thread only. {@link #take} is called after {@link #count} has said there are enough, inside the same purchase, so nothing can move
 * coins in between.
 */
public final class RenownCoinWallet {
    private RenownCoinWallet() {}

    public static int count(ServerPlayer player) {
        int total = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof RenownCoinItem) total += stack.getCount();
        }
        return total;
    }

    /**
     * Takes {@code amount} coins, any species, from the largest stacks first (so a player's single coins of rarer bosses are kept as long as
     * possible). Returns how many were taken: all of them unless the inventory changed under the caller.
     */
    public static int take(ServerPlayer player, int amount) {
        Inventory inventory = player.getInventory();
        int left = amount;
        while (left > 0) {
            int best = -1;
            int bestCount = 0;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.getItem() instanceof RenownCoinItem && stack.getCount() > bestCount) {
                    best = slot;
                    bestCount = stack.getCount();
                }
            }
            if (best < 0) break;
            int taken = Math.min(left, bestCount);
            inventory.getItem(best).shrink(taken);
            left -= taken;
        }
        inventory.setChanged();
        return amount - left;
    }
}
