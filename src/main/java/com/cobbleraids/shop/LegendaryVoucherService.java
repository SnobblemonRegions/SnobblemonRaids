package com.cobbleraids.shop;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobbleraids.RaidLog;
import com.cobbleraids.item.LegendaryVoucherItem;
import com.cobbleraids.network.ShopEntryPayload;
import com.cobbleraids.network.ShopPagePayload;
import com.cobbleraids.web.ShopWebPage;
import com.cobbleraids.web.WebUi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The Legendary Voucher's chooser: the shop page in a "choose your legendary" mode, listing every legendary (owner, 2026-11-27: box
 * legendaries included, no mythicals), given at level 1.
 *
 * <p>A player enters the mode by using a voucher and leaves it by opening the shop any other way. Choosing takes two clicks on the same
 * Pokemon, like a purchase (RaidShopGateway's confirmation). Everything that can fail is checked before the voucher is touched: that the
 * player still holds one bound to them, that the species exists and is a legendary, and that the party or PC has room. Only then is the
 * Pokemon given and one voucher removed.
 */
public final class LegendaryVoucherService {
    private LegendaryVoucherService() {}

    public static final String PREFIX = "voucher:";
    private static final int PER_PAGE = 16;
    private static final Set<UUID> CHOOSING = ConcurrentHashMap.newKeySet();
    private static volatile List<Species> legendaries;

    public static void register() {
        LegendaryVoucherItem.opener = LegendaryVoucherService::open;
    }

    /** Opens the shop page in chooser mode; the page asks for its first page itself. */
    public static void open(ServerPlayer player) {
        CHOOSING.add(player.getUUID());
        if (!WebUi.open(player, ShopWebPage.PAGE)) {
            CHOOSING.remove(player.getUUID());
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Choosing needs the Snobblemon Client mod.")
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
    }

    public static boolean choosing(ServerPlayer player) {
        return CHOOSING.contains(player.getUUID());
    }

    public static void leave(ServerPlayer player) {
        CHOOSING.remove(player.getUUID());
    }

    public static void forget(UUID player) {
        CHOOSING.remove(player);
    }

    /** Every implemented legendary, in Pokedex order. Cached: the species list does not change while the server runs. */
    static List<Species> legendaries() {
        List<Species> known = legendaries;
        if (known != null) return known;
        List<Species> found = new ArrayList<>();
        for (Species species : PokemonSpecies.INSTANCE.getImplemented()) {
            Set<String> labels = species.getLabels();
            if (labels.contains("legendary") && !labels.contains("mythical")) found.add(species);
        }
        found.sort(Comparator.comparingInt(Species::getNationalPokedexNumber));
        legendaries = List.copyOf(found);
        return legendaries;
    }

    /** How many vouchers this player can redeem right now. */
    static int vouchersHeld(ServerPlayer player) {
        int held = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof LegendaryVoucherItem && LegendaryVoucherItem.usableBy(stack, player)) held += stack.getCount();
        }
        return held;
    }

    static void sendPage(ServerPlayer player, int requested) {
        List<Species> all = legendaries();
        int pageCount = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int index = Math.floorMod(requested, pageCount);
        List<ShopEntryPayload> entries = new ArrayList<>();
        for (Species species : all.subList(Math.min(all.size(), index * PER_PAGE), Math.min(all.size(), (index + 1) * PER_PAGE))) {
            String path = species.getResourceIdentifier().getPath();
            entries.add(new ShopEntryPayload(PREFIX + path, 0, true, ResourceLocation.withDefaultNamespace("air"), 1, path, 1, false,
                    0, 0, 0, 0, false, false, 0));
        }
        ShopWebPage.deliver(player, new ShopPagePayload("Legendary Voucher", index, pageCount, vouchersHeld(player), entries, 0L));
    }

    /** Gives the chosen legendary and uses up one voucher, in that order, once nothing can still fail. */
    static ShopPurchaseResult redeem(ServerPlayer player, String path) {
        Species chosen = null;
        for (Species species : legendaries()) {
            if (species.getResourceIdentifier().getPath().equals(path.toLowerCase(Locale.ROOT))) chosen = species;
        }
        if (chosen == null) return ShopPurchaseResult.UNKNOWN_ENTRY;
        int slot = voucherSlot(player);
        if (slot < 0) return ShopPurchaseResult.NO_VOUCHER;
        PokemonProperties properties = new PokemonProperties();
        properties.setSpecies(chosen.getResourceIdentifier().toString());
        properties.setLevel(1);
        Pokemon pokemon = properties.create();
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        boolean partyHasRoom = party.getFirstAvailablePosition() != null;
        if (!partyHasRoom && Cobblemon.INSTANCE.getStorage().getPC(player).getFirstAvailablePosition() == null) {
            return ShopPurchaseResult.NO_ROOM;
        }
        boolean toParty = partyHasRoom && party.add(pokemon);
        if (!toParty && !Cobblemon.INSTANCE.getStorage().getPC(player).add(pokemon)) {
            RaidLog.error("Legendary Voucher: {} could not be given to {} despite a free slot; the voucher was kept.", path,
                    player.getGameProfile().getName());
            return ShopPurchaseResult.FAILED;
        }
        player.getInventory().getItem(slot).shrink(1);
        player.getInventory().setChanged();
        RaidLog.info("Legendary Voucher: {} chose {}", player.getGameProfile().getName(), chosen.getResourceIdentifier());
        return toParty ? ShopPurchaseResult.CHOSEN : ShopPurchaseResult.CHOSEN_TO_PC;
    }

    private static int voucherSlot(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof LegendaryVoucherItem && LegendaryVoucherItem.usableBy(stack, player)) return slot;
        }
        return -1;
    }
}
