package com.cobbleraids.item;

import com.cobbleraids.config.RaidRarityTier;
import eu.pb4.polymer.core.api.item.PolymerItemGroupUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * The CobbleRaids creative tab, registered through Polymer so it stays server-side.
 *
 * <p>It exists for recipe viewers as much as for creative mode. Polymer's client compat for JEI, REI
 * and EMI builds its list of server items from the creative tabs it is sent, nothing else: an item
 * in no tab cannot be searched for and has no recipe lookup, even for a player with Polymer
 * installed. A vanilla client is never sent the tab and sees no change.
 *
 * <p>The title is literal text because a client has no CobbleRaids language file.
 */
public final class RaidCreativeTab {
    private RaidCreativeTab() {}

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("cobbleraids", "items");

    private static boolean registered;

    /** After every item is registered: the tab's contents are read from the item registrars. */
    public static void register() {
        if (registered) return;   // a second registration of the same id throws at load
        registered = true;
        CreativeModeTab tab = PolymerItemGroupUtils.builder()
                .title(Component.literal("CobbleRaids"))
                .icon(() -> new ItemStack(RaidRadarItem.INSTANCE))
                .displayItems((parameters, output) -> {
                    output.accept(RaidRadarItem.INSTANCE);
                    for (RaidRarityTier tier : RaidRarityTier.values()) {
                        output.accept(RaidKeyItems.fragment(tier));
                        output.accept(RaidKeyItems.key(tier));
                    }
                    output.accept(RaidHeldItems.raidCore());
                    output.accept(RaidHeldItems.guardianScale());
                })
                .build();
        PolymerItemGroupUtils.registerPolymerItemGroup(ID, tab);
    }
}
