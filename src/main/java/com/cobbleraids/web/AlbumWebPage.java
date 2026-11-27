package com.cobbleraids.web;

import com.cobbleraids.album.RenownAlbum;
import com.cobbleraids.album.RenownAlbumStore;

import com.cobbleraids.RaidLog;
import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.item.RenownCoinItem;
import com.cobbleraids.title.TitleService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * The Coin Album page (Snobblemon GUI {@code cobbleraids_album}): one page per type, a slot for every species the Daily Boss can field.
 *
 * <p>Pressing a coin in takes one coin of that species from the inventory and fills the slot; taking it out empties the slot and gives the coin
 * back. Each is checked against server state and done in an order that cannot duplicate or lose a coin: pressing removes the coin before the
 * slot is filled (and gives it back if the slot was already full); taking out empties the slot before the coin is given. The store is written
 * to disk after every change.
 */
public final class AlbumWebPage {
    private AlbumWebPage() {}

    public static final String CHANNEL = "cobbleraids.album";
    public static final String PAGE = "album/album.html";
    static final List<String> TYPE_ORDER = List.of("normal", "fire", "water", "electric", "grass", "ice", "fighting", "poison", "ground",
            "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy");

    public static void register() {
        WebGuiBridge.onPageEvent(CHANNEL, (player, json) -> RaidFaultBarrier.guard("album-event", () -> onEvent(player, json)));
    }

    public static boolean open(ServerPlayer player) {
        return WebUi.open(player, PAGE);
    }

    /** Every species in the album (id to primary type), in the Daily Boss pool's order. */
    static Map<String, String> slots() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : com.cobbleraids.worldboss.WorldBossService.albumSpecies().entrySet()) {
            String type = RenownCoinItem.primaryType(ResourceLocation.tryParse(entry.getKey()));
            if (type != null) out.put(entry.getKey(), type);
        }
        return out;
    }

    public static RenownAlbum.Progress progress(ServerPlayer player) {
        return RenownAlbum.progress(slots(), RenownAlbumStore.get(player.getServer()).pressed(player.getUUID()));
    }

    /** For the title check, which runs for players who may be offline. */
    public static RenownAlbum.Progress progress(net.minecraft.server.MinecraftServer server, java.util.UUID player) {
        return RenownAlbum.progress(slots(), RenownAlbumStore.get(server).pressed(player));
    }

    private static void onEvent(ServerPlayer player, String json) {
        JsonObject message = WebJson.object(json);
        if (message == null) return;
        String type = WebJson.bounded(message, "type", 16);
        String species = WebJson.bounded(message, "species", 64);
        switch (WebJson.string(message, "action")) {
            case "ready", "page" -> send(player, type);
            case "press" -> {
                press(player, species);
                send(player, type);
            }
            case "take" -> {
                takeOut(player, species);
                send(player, type);
            }
            default -> RaidLog.warn("Ignoring unknown album action from {}", player.getGameProfile().getName());
        }
    }

    private static void press(ServerPlayer player, String species) {
        if (!slots().containsKey(species)) return;
        RenownAlbumStore store = RenownAlbumStore.get(player.getServer());
        if (store.pressed(player.getUUID()).contains(species)) return;
        int slot = coinSlot(player, species);
        if (slot < 0) {
            player.sendSystemMessage(Component.literal("You have no " + name(species) + " coin to press in.").withStyle(ChatFormatting.RED));
            return;
        }
        ItemStack held = player.getInventory().getItem(slot);
        String tier = tierOf(held);
        held.shrink(1);
        player.getInventory().setChanged();
        if (!store.press(player.getUUID(), species)) {
            player.getInventory().placeItemBackInInventory(RenownCoinItem.stack(ResourceLocation.parse(species), tier, 1));
            return;
        }
        flush(player, store);
        WebSounds.play(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.3f);
        TitleService.checkUnlocks(player.getServer(), player.getUUID());
    }

    private static void takeOut(ServerPlayer player, String species) {
        RenownAlbumStore store = RenownAlbumStore.get(player.getServer());
        if (!store.takeOut(player.getUUID(), species)) return;
        flush(player, store);
        String tier = com.cobbleraids.worldboss.WorldBossService.albumSpecies().getOrDefault(species, "legendary");
        player.getInventory().placeItemBackInInventory(RenownCoinItem.stack(ResourceLocation.parse(species), tier, 1));
        WebSounds.play(player, SoundEvents.ITEM_PICKUP, 0.7f, 1.0f);
    }

    private static void flush(ServerPlayer player, RenownAlbumStore store) {
        try {
            store.flush(player.getServer());
        } catch (java.io.IOException ex) {
            RaidLog.error("Could not save the Coin Album", ex);
        }
    }

    private static int coinSlot(ServerPlayer player, String species) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ResourceLocation id = RenownCoinItem.species(inventory.getItem(slot));
            if (id != null && id.toString().equals(species)) return slot;
        }
        return -1;
    }

    private static int coinsOf(ServerPlayer player, String species) {
        int count = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            ResourceLocation id = RenownCoinItem.species(stack);
            if (id != null && id.toString().equals(species)) count += stack.getCount();
        }
        return count;
    }

    private static String tierOf(ItemStack stack) {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        String tier = data == null ? "" : data.copyTag().getString(RenownCoinItem.TIER);
        return tier.isEmpty() ? "legendary" : tier;
    }

    /** The species' coin as the client draws it ("minecraft:gold_nugget@model"), so a filled slot shows its own struck face. */
    private static String coinItem(ServerPlayer player, String species, String tier) {
        ItemStack coin = RenownCoinItem.stack(ResourceLocation.parse(species), tier, 1);
        ItemStack shown = eu.pb4.polymer.core.api.item.PolymerItemUtils.getPolymerItemStack(coin, player.registryAccess(), player);
        var model = shown.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
        String vanilla = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(shown.getItem()).toString();
        return model == null ? vanilla : vanilla + "@" + model.value();
    }

    private static String name(String species) {
        return RenownCoinItem.speciesName(ResourceLocation.tryParse(species));
    }

    private static void send(ServerPlayer player, String requestedType) {
        Map<String, String> slots = slots();
        Set<String> pressed = RenownAlbumStore.get(player.getServer()).pressed(player.getUUID());
        List<String> pages = RenownAlbum.pages(slots, TYPE_ORDER);
        String type = pages.contains(requestedType) ? requestedType : pages.isEmpty() ? "" : pages.get(0);
        RenownAlbum.Progress progress = RenownAlbum.progress(slots, pressed);
        Map<String, String> tiers = com.cobbleraids.worldboss.WorldBossService.albumSpecies();

        JsonObject page = new JsonObject();
        page.addProperty("type", type);
        page.addProperty("pressed", progress.pressed());
        page.addProperty("total", progress.total());
        page.addProperty("coins", com.cobbleraids.item.RenownCoinWallet.count(player));
        page.addProperty("complete", progress.complete());
        JsonArray tabs = new JsonArray();
        for (String id : pages) {
            int filled = 0, total = 0;
            for (Map.Entry<String, String> slot : slots.entrySet()) {
                if (!slot.getValue().equals(id)) continue;
                total++;
                if (pressed.contains(slot.getKey())) filled++;
            }
            JsonObject tab = new JsonObject();
            tab.addProperty("id", id);
            tab.addProperty("filled", filled);
            tab.addProperty("total", total);
            tab.addProperty("done", progress.completedTypes().contains(id));
            tabs.add(tab);
        }
        page.add("pages", tabs);
        JsonArray cells = new JsonArray();
        for (Map.Entry<String, String> slot : slots.entrySet()) {
            if (!slot.getValue().equals(type)) continue;
            String species = slot.getKey();
            JsonObject cell = new JsonObject();
            cell.addProperty("species", species);
            cell.addProperty("path", ResourceLocation.parse(species).getPath());
            cell.addProperty("name", name(species));
            cell.addProperty("pressed", pressed.contains(species));
            cell.addProperty("have", coinsOf(player, species));
            cell.addProperty("mythical", "mythical".equals(tiers.get(species)));
            cell.addProperty("coinItem", coinItem(player, species, tiers.getOrDefault(species, "legendary")));
            cells.add(cell);
        }
        page.add("slots", cells);
        WebGuiBridge.emit(player, "album_page", page.toString());
    }
}
