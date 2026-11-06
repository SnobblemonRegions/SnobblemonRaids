package com.snobblemon.gems;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.RegistryOps;

import java.util.List;

/**
 * Hands over rewards that were waiting because the player was offline or had no inventory room: Shiny Boost Tickets, shop items,
 * and Gem credits that were held under a name Mojang could not resolve. A pending record is taken exactly once, so a reward can
 * never be delivered twice, and a partly delivered batch keeps only what is still outstanding.
 */
public final class Delivery {
    private final SnobbleGems mod;

    public Delivery(SnobbleGems mod) {
        this.mod = mod;
    }

    public static String encodeItem(RegistryAccess ra, ItemStack stack) {
        RegistryOps<JsonElement> ops = ra.createSerializationContext(JsonOps.INSTANCE);
        return ItemStack.CODEC.encodeStart(ops, stack).getOrThrow().toString();
    }

    public static ItemStack decodeItem(RegistryAccess ra, String json) {
        RegistryOps<JsonElement> ops = ra.createSerializationContext(JsonOps.INSTANCE);
        return ItemStack.CODEC.parse(ops, JsonParser.parseString(json)).getOrThrow();
    }

    /**
     * @param announce tell the player what happened (join and /claim do; silent callers pass false)
     * @return number of pending records fully delivered
     */
    public int deliver(ServerPlayer p, boolean announce) {
        List<Ledger.PendingRec> list = mod.ledger.pendingFor(p.getUUID(), p.getGameProfile().getName());
        int delivered = 0;
        int stillWaiting = 0;
        for (Ledger.PendingRec rec : list) {
            try {
                switch (rec.kind) {
                    case "gems" -> {
                        long amount = Long.parseLong(rec.data);
                        if (!mod.ledger.completePending(rec.id, p.getUUID())) continue;
                        var r = mod.ledger.adjust(p.getUUID(), p.getGameProfile().getName(), amount, "held credit delivered", "pending", null);
                        mod.notifyCredit(p, r.applied(), r.balance(), true);
                        delivered++;
                    }
                    case "ticket", "legendary_ticket" -> {
                        boolean leg = rec.kind.equals("legendary_ticket");
                        int want = Integer.parseInt(rec.data);
                        int gave = mod.tickets.handOver(p, want, "pending:" + rec.id, leg ? Tickets.LEGENDARY : Tickets.SHINY);
                        if (gave >= want) {
                            if (mod.ledger.completePending(rec.id, p.getUUID())) delivered++;
                        } else {
                            if (gave > 0) mod.ledger.updatePendingData(rec.id, String.valueOf(want - gave));
                            stillWaiting += want - gave;
                        }
                        if (gave > 0) {
                            mod.notify(p, mod.vars(leg ? "lticket_received" : "ticket_received", "amount", String.valueOf(gave)));
                            mod.sound(p, mod.cfg.sounds.ticket);
                        }
                    }
                    case "item" -> {
                        ItemStack stack = decodeItem(mod.server.registryAccess(), rec.data);
                        ItemStack copy = stack.copy();
                        p.getInventory().add(stack);
                        if (stack.isEmpty()) {
                            if (mod.ledger.completePending(rec.id, p.getUUID())) delivered++;
                            mod.sound(p, mod.cfg.sounds.purchase);
                        } else {
                            // part of the stack may have gone in: remember only the rest
                            if (stack.getCount() < copy.getCount()) mod.ledger.updatePendingData(rec.id, encodeItem(mod.server.registryAccess(), stack));
                            stillWaiting++;
                        }
                    }
                    default -> SnobbleGems.LOG.warn("[SnobbleGems] unknown pending kind {}", rec.kind);
                }
            } catch (Throwable t) {
                SnobbleGems.LOG.error("[SnobbleGems] could not deliver pending {} to {}: {}", rec.id, p.getGameProfile().getName(), t.toString());
                stillWaiting++;
            }
        }
        if (announce) {
            if (list.isEmpty()) mod.notify(p, mod.vars("nothing_to_claim"));
            else if (delivered > 0) mod.notify(p, mod.vars("claimed", "amount", String.valueOf(delivered)));
            if (stillWaiting > 0) mod.notify(p, mod.vars("ticket_pending", "amount", String.valueOf(stillWaiting)));
        }
        return delivered;
    }
}
