package com.cobbleraids.radar;

import com.cobbleraids.charges.RaidChargeAccount;
import com.cobbleraids.charges.RaidChargeStore;
import com.cobbleraids.item.RaidRadarItem;
import com.cobbleraids.presentation.CommandFormat;
import com.cobbleraids.raidio.RaidioMode;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * The radar's right-click menu: a vanilla chest, so it needs no client mod, custom screen or player-authoritative charge packets. It is a framed,
 * symmetric panel (layout in {@link RadarMenuLayout}); its icons cannot be taken, moved or cloned, because no click ever reaches the container.
 */
public final class RaidRadarMenu extends ChestMenu {
    private final SimpleContainer display;
    private final ServerPlayer owner;
    private final InteractionHand hand;
    private int refreshTicks;

    public static void open(ServerPlayer player, InteractionHand hand) {
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> new RaidRadarMenu(id, player, hand),
                Component.literal((RaidioMode.enabled() ? "Raidio" : "Raid Radar") + " - " + RaidChargeStore.status(player).available() + "/" + RaidChargeAccount.CAPACITY)));
    }

    private RaidRadarMenu(int id, ServerPlayer player, InteractionHand hand) {
        this(id, player, hand, new SimpleContainer(RadarMenuLayout.SIZE));
    }

    private RaidRadarMenu(int id, ServerPlayer player, InteractionHand hand, SimpleContainer display) {
        super(MenuType.GENERIC_9x3, id, player.getInventory(), display, 3);
        this.display = display;
        this.owner = player;
        this.hand = hand;
        refresh();
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        var account = RaidChargeStore.get(owner.server).status(owner.getUUID(), now);
        int capacity = RaidChargeAccount.CAPACITY;
        for (int i = 0; i < RadarMenuLayout.SIZE; i++) display.setItem(i, icon(Items.BLACK_STAINED_GLASS_PANE, " ", ChatFormatting.DARK_GRAY));
        for (int corner : RadarMenuLayout.CORNERS) display.setItem(corner, icon(Items.CYAN_STAINED_GLASS_PANE, " ", ChatFormatting.AQUA));

        boolean ready = account.available() > 0;
        display.setItem(RadarMenuLayout.HEADER, icon(ready ? Items.EMERALD : Items.REDSTONE,
                "Raid charges: " + account.available() + " / " + capacity, ready ? ChatFormatting.GREEN : ChatFormatting.RED,
                ready ? "Ready to join a raid" : "No charges remaining", "One charge is used when your battle starts."));
        for (int i = 0; i < capacity; i++) {
            boolean full = i < account.available();
            display.setItem(RadarMenuLayout.CHARGES[i], icon(full ? Items.AMETHYST_SHARD : Items.GRAY_DYE,
                    "Charge " + (i + 1) + ": " + (full ? "Ready" : "Recharging"), full ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        }
        boolean fullyCharged = account.available() == capacity;
        display.setItem(RadarMenuLayout.CLOCK, icon(Items.CLOCK,
                fullyCharged ? "Fully charged" : "Next charge in " + CommandFormat.duration(account.secondsToNext(now)), ChatFormatting.GOLD,
                fullyCharged ? "You have all " + capacity + " raid charges." : "Full in " + CommandFormat.duration(account.secondsToFull(now)),
                "Refills 1 charge per hour, up to " + capacity + ".", "Refills while you are offline, too."));
        int pointsCap = com.cobbleraids.config.CobbleRaidsConfigManager.get().raidPoints()
                .dailyCapFor(com.cobbleraids.reward.points.RaidPointsSource.RAID);
        if (pointsCap > 0) {
            int earned = com.cobbleraids.reward.points.RaidPointsStore.earnedToday(owner.getUUID(),
                    com.cobbleraids.reward.points.RaidPointsSource.RAID);
            boolean capped = earned >= pointsCap;
            display.setItem(RadarMenuLayout.POINTS, icon(capped ? Items.GRAY_DYE : Items.PRISMARINE_CRYSTALS,
                    "Raid Points today: " + Math.min(earned, pointsCap) + " / " + pointsCap,
                    capped ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE,
                    capped ? "Today's limit is reached." : (pointsCap - earned) + " more from raids today.",
                    "Raids still pay loot past the limit.", "Resets at 00:00 UTC."));
        }
        boolean flat = RaidRadarItem.prefersFlatMap(owner.getItemInHand(hand));
        if (RaidioMode.enabled()) {
            display.setItem(RadarMenuLayout.MODE, icon(Items.JUKEBOX, "How Raidio works", ChatFormatting.AQUA,
                    "Tune in, then turn around slowly.", "A clear tune means a raid lies that way.", "Static means you are facing away.",
                    "Each tier has its own tune.", "It quickens and rises as you get close.", "Silent while you stand still facing away.",
                    "Turn it down with the Jukebox/Note Blocks volume."));
        } else {
            display.setItem(RadarMenuLayout.MODE, icon(flat ? Items.FILLED_MAP : Items.ENDER_EYE, flat ? "Display: flat map" : "Display: 3D hologram", ChatFormatting.AQUA,
                    flat ? "A map in your hand." : "A hologram floating in front of you.", "Click for the " + (flat ? "3D hologram" : "flat map") + "."));
        }
        display.setItem(RadarMenuLayout.GUIDE, icon(Items.BOOK, "How raid charges work", ChatFormatting.AQUA,
                "Charges belong to your player UUID.", "Crafting another radar does not reset them.",
                "Joining a lobby costs nothing.", "Cancelled lobbies cost nothing.", "Failed battle starts are refunded.",
                "A started battle costs 1 charge, win or lose.", "Owned encounters use their own entry rules."));

        boolean open = RaidRadarItem.isOpen(owner.getItemInHand(hand));
        if (RaidioMode.enabled()) {
            display.setItem(RadarMenuLayout.TOGGLE, icon(Items.NOTE_BLOCK, open ? "Tune out" : "Tune in", ChatFormatting.AQUA,
                    "Lets you hear which way the raid is."));
        } else {
            display.setItem(RadarMenuLayout.TOGGLE, icon(Items.COMPASS, open ? "Close radar" : "Open radar", ChatFormatting.AQUA,
                    "Shows nearby raids on the display you chose."));
        }
        List<String> tracking = RaidRadarService.trackingLines(owner);
        String[] nextLore = new String[tracking.isEmpty() ? 2 : tracking.size() + 2];
        nextLore[0] = tracking.isEmpty() ? (open ? "No raid to track right now." : "Open the radar to track a raid.") : "Tracking:";
        for (int i = 0; i < tracking.size(); i++) nextLore[i + 1] = "  " + tracking.get(i);
        nextLore[nextLore.length - 1] = "Click to cycle the radar's target.";
        display.setItem(RadarMenuLayout.NEXT, icon(Items.SPECTRAL_ARROW, "Next raid", ChatFormatting.GOLD, nextLore));
        display.setItem(RadarMenuLayout.CLOSE, icon(Items.BARRIER, "Close menu", ChatFormatting.RED));
    }

    private static ItemStack icon(Item item, String title, ChatFormatting color, String... lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(title).withStyle(color).withStyle(s -> s.withItalic(false)));
        if (lore.length > 0) stack.set(DataComponents.LORE, new ItemLore(Arrays.stream(lore)
                .map(line -> (Component) Component.literal(line).withStyle(ChatFormatting.GRAY).withStyle(s -> s.withItalic(false))).toList()));
        return stack;
    }

    @Override
    public void broadcastChanges() {
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            refresh();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return player == owner && owner.isAlive() && owner.getItemInHand(hand).is(RaidRadarItem.INSTANCE);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        // No calls to super: menu icons and player inventory cannot be moved, thrown or cloned.
        if (!stillValid(player) || type != ClickType.PICKUP || (button != 0 && button != 1)) return;
        switch (RadarMenuLayout.controlAt(slot)) {
            case TOGGLE -> {
                owner.closeContainer();
                RaidRadarItem.toggle(owner, hand);
            }
            case NEXT -> {
                owner.closeContainer();
                if (!RaidRadarItem.isOpen(owner.getItemInHand(hand))) RaidRadarItem.toggle(owner, hand);
                RaidRadarService.nextTarget(owner);
            }
            case CLOSE -> owner.closeContainer();
            case MODE -> {
                if (RaidioMode.enabled()) return;   // Raidio has no display to switch; the icon is a guide
                RaidRadarItem.toggleDisplayMode(owner, hand);
                refresh();
            }
            case NONE -> { }
        }
    }
}
