package com.snobblemon.gems;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Shiny Boost Ticket. Each ticket is a non-stackable item carrying its own random id and an HMAC signature of that id (key kept in the
 * database). Redeeming looks the id up in the ledger and retires it in one step, so a copied, forged or already-used ticket is refused.
 */
public final class Tickets {
    private final SnobbleGems mod;
    private static final String TAG = "snobblegemcurrency";
    public static final String SHINY = "shiny_ticket", LEGENDARY = "legendary_ticket";
    private final Map<UUID, Pending> confirm = new HashMap<>();

    private record Pending(String ticketId, long until) {}

    public Tickets(SnobbleGems mod) {
        this.mod = mod;
    }

    public void register() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.pass(player.getItemInHand(hand));
            ItemStack stack = sp.getItemInHand(hand);
            if (!isTicketShape(stack)) return InteractionResultHolder.pass(stack);
            use(sp, hand, stack);
            return InteractionResultHolder.success(stack);
        });
    }

    // ------------------------------------------------------------------ the item

    public ItemStack create(Ledger.TicketRec rec) { return create(rec, SHINY); }

    public ItemStack create(Ledger.TicketRec rec, String kind) {
        boolean leg = LEGENDARY.equals(kind);
        Cfg.LegendaryTicket lt = mod.cfg.legendaryTicket;
        Cfg.Ticket t = leg ? null : mod.cfg.ticket;
        String name = leg ? lt.displayName : t.displayName;
        List<String> lore = leg ? lt.lore : t.lore;
        String baseItem = leg ? lt.baseItem : t.baseItem;
        int cmd = leg ? lt.customModelData : t.customModelData;
        boolean glint = leg ? lt.glint : t.glint;
        Item base = BuiltInRegistries.ITEM.get(ResourceLocation.parse(baseItem));
        if (base == Items.AIR) base = Items.TRIAL_KEY;
        ItemStack s = new ItemStack(base);
        s.set(DataComponents.CUSTOM_NAME, Txt.parse(name));
        Map<String, String> v = Map.of("short", rec.id.substring(0, 8), "id", rec.id,
                "multiplier", trim(lt.multiplier), "minutes", trim(lt.minutes));
        s.set(DataComponents.LORE, new ItemLore(Txt.parseAll(lore, v)));
        if (mod.cfg.gui.customIcons) s.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(cmd));
        s.set(DataComponents.MAX_STACK_SIZE, 1);
        if (glint) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        CompoundTag root = new CompoundTag();
        CompoundTag mine = new CompoundTag();
        mine.putString("kind", leg ? LEGENDARY : SHINY);
        mine.putString("id", rec.id);
        mine.putString("sig", mod.ledger.sign(leg ? "L:" + rec.id : rec.id));
        root.put(TAG, mine);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return s;
    }

    private static CompoundTag tag(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        if (d == null) return null;
        CompoundTag t = d.copyTag();
        return t.contains(TAG, 10) ? t.getCompound(TAG) : null;
    }

    /** Looks like one of ours (claims to be a ticket), whether or not it is genuine. */
    public static boolean isTicketShape(ItemStack s) {
        if (s.isEmpty()) return false;
        CompoundTag t = tag(s);
        return t != null && (SHINY.equals(t.getString("kind")) || LEGENDARY.equals(t.getString("kind")));
    }

    private static String kindOf(ItemStack s) {
        CompoundTag t = tag(s);
        return t != null && LEGENDARY.equals(t.getString("kind")) ? LEGENDARY : SHINY;
    }

    /** The ticket id if the signature matches, otherwise null. */
    public String genuineId(ItemStack s) {
        CompoundTag t = tag(s);
        if (t == null) return null;
        String id = t.getString("id"), sig = t.getString("sig");
        if (id.isEmpty() || sig.isEmpty()) return null;
        return mod.ledger.sign(LEGENDARY.equals(t.getString("kind")) ? "L:" + id : id).equals(sig) ? id : null;
    }

    // ------------------------------------------------------------------ granting

    /**
     * Gives tickets to a player (online: into the inventory as far as it fits, the rest waits; offline: waits for the next join).
     * @return how many were handed over right now
     */
    public int grant(UUID uuid, String name, int count, String source, String ref) { return grant(uuid, name, count, source, ref, SHINY); }

    public int grant(UUID uuid, String name, int count, String source, String ref, String kind) {
        boolean leg = LEGENDARY.equals(kind);
        ServerPlayer online = uuid == null ? null : mod.server.getPlayerList().getPlayer(uuid);
        int delivered = 0;
        if (online != null) delivered = handOver(online, count, source, kind);
        int rest = count - delivered;
        if (rest > 0) mod.ledger.addPending(uuid, name, leg ? "legendary_ticket" : "ticket", String.valueOf(rest), ref);
        if (online != null) {
            if (delivered > 0) {
                mod.notify(online, mod.vars(leg ? "lticket_received" : "ticket_received", "amount", String.valueOf(delivered)));
                mod.sound(online, mod.cfg.sounds.ticket);
            }
            if (rest > 0) mod.notify(online, mod.vars("ticket_pending", "amount", String.valueOf(rest)));
        }
        return delivered;
    }

    /** Puts up to {@code count} fresh tickets into the inventory (one slot each); returns how many fit. */
    public int handOver(ServerPlayer p, int count, String source) { return handOver(p, count, source, SHINY); }

    public int handOver(ServerPlayer p, int count, String source, String kind) {
        int done = 0;
        while (done < count && p.getInventory().getFreeSlot() >= 0) {
            Ledger.TicketRec rec = mod.ledger.issueTickets(p.getUUID(), 1, source).get(0);
            ItemStack stack = create(rec, kind);
            if (!p.getInventory().add(stack)) {
                mod.ledger.voidTicket(rec.id);
                break;
            }
            done++;
        }
        return done;
    }

    // ------------------------------------------------------------------ using one

    private void use(ServerPlayer p, InteractionHand hand, ItemStack stack) {
        Cfg cfg = mod.cfg;
        if (!Perms.player(p, Perms.TICKET_USE, 0)) {
            mod.notify(p, mod.vars("no_permission"));
            return;
        }
        final boolean leg = LEGENDARY.equals(kindOf(stack));
        final String ticketName = leg ? "Legendary Boost Ticket" : "Shiny Boost Ticket";
        String id = genuineId(stack);
        if (id == null || !mod.ledger.ticketIssued(id)) {
            // forged, copied after use, or never issued by this server
            mod.notify(p, mod.vars("ticket_invalid"));
            mod.sound(p, cfg.sounds.error);
            SnobbleGems.LOG.warn("[SnobbleGems] {} tried to use an invalid {} (id={})", p.getGameProfile().getName(), ticketName, id);
            return;
        }
        long now = System.currentTimeMillis();
        boolean confirmClick = leg ? cfg.legendaryTicket.confirmClick : cfg.ticket.confirmClick;
        int confirmSeconds = leg ? cfg.legendaryTicket.confirmSeconds : cfg.ticket.confirmSeconds;
        if (confirmClick) {
            Pending pend = confirm.get(p.getUUID());
            if (pend == null || !pend.ticketId.equals(id) || now > pend.until) {
                confirm.put(p.getUUID(), new Pending(id, now + confirmSeconds * 1000L));
                mod.notify(p, mod.vars(leg ? "lticket_confirm" : "ticket_confirm", "seconds", String.valueOf(confirmSeconds)));
                mod.sound(p, cfg.sounds.click);
                return;
            }
            confirm.remove(p.getUUID());
        }
        if (!mod.ledger.redeemTicket(id, p.getUUID())) {
            mod.notify(p, mod.vars("ticket_invalid"));
            return;
        }
        boolean ok;
        try {
            ok = leg ? mod.legendary.add() : activate(p);
        } catch (Throwable t) {
            SnobbleGems.LOG.error("[SnobbleGems] activation failed", t);
            ok = false;
        }
        if (!ok) {
            mod.ledger.reinstateTicket(id, p.getUUID());
            mod.notify(p, mod.vars(leg ? "lticket_unavailable" : "ticket_unavailable"));
            mod.sound(p, cfg.sounds.error);
            return;
        }
        stack.shrink(1);
        mod.notify(p, mod.vars(leg ? "lticket_used" : "ticket_used"));
        mod.sound(p, cfg.sounds.ticket);
        SnobbleGems.LOG.info("[SnobbleGems] {} redeemed {} {}", p.getGameProfile().getName(), ticketName, id);
        if (leg) {
            Cfg.LegendaryTicket lt = cfg.legendaryTicket;
            if (lt.broadcastEnabled && !lt.broadcast.isBlank()) {
                mod.broadcast(Txt.apply(lt.broadcast, Map.of("player", p.getGameProfile().getName(), "multiplier", trim(lt.multiplier), "minutes", trim(lt.minutes))));
            }
        } else if (cfg.ticket.broadcastEnabled && !cfg.ticket.broadcast.isBlank()) {
            mod.broadcast(Txt.apply(cfg.ticket.broadcast, Map.of("player", p.getGameProfile().getName())));
        }
    }

    // ------------------------------------------------------------------ activation

    /** Which backend a ticket will use right now: COMPANION, BOOSTERS, COMMANDS or NONE. */
    public String resolveMode() {
        String m = mod.cfg.ticket.activation == null ? "AUTO" : mod.cfg.ticket.activation.toUpperCase();
        if (m.equals("COMPANION") || m.equals("BOOSTERS") || m.equals("COMMANDS")) return m;
        FabricLoader fl = FabricLoader.getInstance();
        if (fl.isModLoaded("snobblemoncompanion") && rootExists("companion")) return "COMPANION";
        if (fl.isModLoaded("cobblemon_boosters") && rootExists("boosters")) return "BOOSTERS";
        return mod.cfg.ticket.commands.isEmpty() ? "NONE" : "COMMANDS";
    }

    private boolean rootExists(String literal) {
        return mod.server.getCommands().getDispatcher().getRoot().getChild(literal) != null;
    }

    private boolean activate(ServerPlayer p) {
        return activate(p.getGameProfile().getName(), p.getUUID());
    }

    /** Runs exactly what redeeming a ticket runs, without a player or a ticket. Used by /snobblegems ticket test. */
    public boolean activate(String playerName, UUID playerId) {
        Cfg.Ticket t = mod.cfg.ticket;
        String mode = resolveMode();
        long seconds = Math.max(1, Math.round(t.minutes * 60.0));
        Map<String, String> v = new HashMap<>();
        v.put("player", playerName);
        v.put("uuid", playerId.toString());
        v.put("multiplier", trim(t.multiplier));
        v.put("seconds", String.valueOf(seconds));
        v.put("minutes", String.valueOf(t.minutes));
        switch (mode) {
            case "COMPANION" -> {
                if (!rootExists("companion")) return false;
                mod.runConsole(t.companionCommand);
            }
            case "BOOSTERS" -> {
                if (!rootExists("boosters")) return false;
                for (String c : t.boostersCommands) mod.runConsole(Txt.apply(c, v));
            }
            case "COMMANDS" -> {
                if (t.commands.isEmpty()) return false;
            }
            default -> {
                return false;
            }
        }
        for (String c : t.commands) mod.runConsole(Txt.apply(c, v));
        return true;
    }

    private static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    public void forget(UUID id) { confirm.remove(id); }
}
