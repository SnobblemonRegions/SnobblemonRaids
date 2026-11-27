package com.cobbleraids.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * The Legendary Voucher, bought at the Renown Vendor for Renown Coins (owner, 2026-11-27): use it to choose any legendary, given at level 1.
 *
 * <p>Bound to its buyer: the buyer's id is stamped on it when it is bought, and only that player can redeem it, so legendaries are earned and
 * never bought from someone else. Using it opens the chooser ({@code LegendaryVoucherService}); the voucher is used up only once the chosen
 * Pokemon has actually been given, so closing the page or a full party and PC costs nothing.
 */
public class LegendaryVoucherItem extends Item implements PolymerItem {

    public static final String OWNER = "owner";
    public static final String OWNER_NAME = "owner_name";

    private static Item voucher;
    private static PolymerModelData model;
    /** Opens the chooser; set by the shop at start-up, so this item does not depend on the shop package. */
    public static volatile Consumer<ServerPlayer> opener = player -> { };

    public LegendaryVoucherItem(Properties properties) {
        super(properties);
    }

    public static void register() {
        if (voucher != null) return;
        model = PolymerResourcePackUtils.requestModel(Items.PAPER, ResourceLocation.fromNamespaceAndPath("cobbleraids", "item/legendary_voucher"));
        voucher = Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("cobbleraids", "legendary_voucher"),
                new LegendaryVoucherItem(new Item.Properties().stacksTo(16)));
    }

    public static Item item() {
        return voucher;
    }

    /** One voucher bound to {@code player}. */
    public static ItemStack boundTo(ServerPlayer player, int count) {
        ItemStack stack = new ItemStack(voucher, Math.max(1, count));
        CompoundTag tag = new CompoundTag();
        tag.putString(OWNER, player.getUUID().toString());
        tag.putString(OWNER_NAME, player.getGameProfile().getName());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** The player a voucher is bound to, or null for an unbound one (only an operator can make that). */
    public static UUID owner(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String id = data.copyTag().getString(OWNER);
        try {
            return id.isEmpty() ? null : UUID.fromString(id);
        } catch (IllegalArgumentException bad) {
            return null;
        }
    }

    /** Whether {@code player} may redeem this voucher: it is theirs, or it was never bound. */
    public static boolean usableBy(ItemStack stack, ServerPlayer player) {
        UUID owner = owner(stack);
        return owner == null || owner.equals(player.getUUID());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer server)) return InteractionResultHolder.pass(stack);
        if (!usableBy(stack, server)) {
            server.sendSystemMessage(Component.literal("This voucher is bound to " + ownerName(stack) + ". Only they can use it.")
                    .withStyle(ChatFormatting.RED));
            return InteractionResultHolder.fail(stack);
        }
        opener.accept(server);
        return InteractionResultHolder.success(stack);
    }

    private static String ownerName(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        String name = data == null ? "" : data.copyTag().getString(OWNER_NAME);
        return name.isEmpty() ? "someone else" : name;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Legendary Voucher").withStyle(ChatFormatting.GOLD);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("Use it to choose any legendary Pokemon,").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("given at level 1.").withStyle(ChatFormatting.GRAY));
        if (owner(stack) != null) lines.add(Component.literal("Bound to " + ownerName(stack)).withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public Item getPolymerItem(ItemStack stack, ServerPlayer player) {
        return Items.PAPER;
    }

    @Override
    public int getPolymerCustomModelData(ItemStack stack, ServerPlayer player) {
        return model.value();
    }
}
