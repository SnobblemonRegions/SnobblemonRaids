package com.snobblemon.gems.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import java.util.UUID;

/** Events other mods can subscribe to. They fire after the balance changed in memory (the disk write is queued right behind). */
public final class GemsEvents {
    private GemsEvents() {}

    /** Fired for every balance change: give, take, set, shop purchase, chat reward, API call. Runs on the thread that made the change (normally the server thread); keep handlers short. */
    public static final Event<BalanceChanged> BALANCE_CHANGED = EventFactory.createArrayBacked(BalanceChanged.class,
            listeners -> (player, name, oldBalance, newBalance, reason) -> {
                for (BalanceChanged l : listeners) {
                    try {
                        l.onChange(player, name, oldBalance, newBalance, reason);
                    } catch (Throwable t) {
                        // a broken subscriber must never undo or block a currency change
                        t.printStackTrace();
                    }
                }
            });

    @FunctionalInterface
    public interface BalanceChanged {
        void onChange(UUID player, String name, long oldBalance, long newBalance, String reason);
    }
}
