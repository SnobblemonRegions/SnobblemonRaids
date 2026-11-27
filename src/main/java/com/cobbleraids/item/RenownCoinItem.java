package com.cobbleraids.item;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

/**
 * A Renown Coin: struck from a Daily Boss, one species per coin (see docs/DAILY-BOSS-LOOT.md). Spent at the Renown Vendor, or pressed into
 * the Coin Album to collect.
 *
 * <p>The coin's species and the boss's tier live in its custom data, and nothing else does, so two coins of the same boss stack and trade like
 * any item. A client sees a gold nugget with a custom model number: one icon per primary type and rim (gold for a legendary boss, platinum for
 * a mythical one), drawn by {@code tools/renown_coin_art.py} and served from this mod's own pack. Server-side only, like every raid item.
 */
public class RenownCoinItem extends Item implements PolymerItem {

    public static final String SPECIES = "species";
    public static final String TIER = "tier";
    static final List<String> TYPES = List.of("normal", "fire", "water", "electric", "grass", "ice", "fighting", "poison", "ground", "flying",
            "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy");
    static final List<String> RIMS = List.of("gold", "platinum");

    private static Item coin;
    private static PolymerModelData plain;
    private static final Map<String, PolymerModelData> FACES = new HashMap<>();

    public RenownCoinItem(Properties properties) {
        super(properties);
    }

    /** Registers the item and claims its 37 model numbers. Idempotent, like the other raid items. */
    public static void register() {
        if (coin != null) return;
        plain = PolymerResourcePackUtils.requestModel(Items.GOLD_NUGGET, ResourceLocation.fromNamespaceAndPath("cobbleraids", "item/renown_coin"));
        for (String rim : RIMS) {
            for (String type : TYPES) {
                String key = type + "_" + rim;
                FACES.put(key, PolymerResourcePackUtils.requestModel(Items.GOLD_NUGGET,
                        ResourceLocation.fromNamespaceAndPath("cobbleraids", "item/renown_coin_" + key)));
            }
        }
        coin = Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("cobbleraids", "renown_coin"),
                new RenownCoinItem(new Item.Properties().stacksTo(64)));
    }

    /** Every model and texture the pack needs, for RaidItemPack. */
    public static List<String> artNames() {
        List<String> names = new java.util.ArrayList<>();
        names.add("renown_coin");
        for (String rim : RIMS) for (String type : TYPES) names.add("renown_coin_" + type + "_" + rim);
        return names;
    }

    public static Item item() {
        return coin;
    }

    /** {@code count} coins struck with {@code species} (e.g. cobblemon:rayquaza) from a boss of {@code tier} ("legendary" or "mythical"). */
    public static ItemStack stack(ResourceLocation species, String tier, int count) {
        ItemStack stack = new ItemStack(coin, Math.max(1, count));
        CompoundTag tag = new CompoundTag();
        tag.putString(SPECIES, species.toString());
        tag.putString(TIER, tier == null ? "legendary" : tier.toLowerCase(Locale.ROOT));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** The species a coin was struck with, or null for a plain coin (or anything else). */
    public static ResourceLocation species(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof RenownCoinItem)) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String id = data.copyTag().getString(SPECIES);
        return id.isEmpty() ? null : ResourceLocation.tryParse(id);
    }

    private static String tier(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        String tier = data == null ? "" : data.copyTag().getString(TIER);
        return tier.isEmpty() ? "legendary" : tier;
    }

    /** The species' primary type name, lower case, or null when Cobblemon does not know the species. */
    public static String primaryType(ResourceLocation species) {
        if (species == null) return null;
        Species found = PokemonSpecies.INSTANCE.getByIdentifier(species);
        return found == null ? null : found.getPrimaryType().getName().toLowerCase(Locale.ROOT);
    }

    /** The species' display name ("Rayquaza"), or its id path when Cobblemon does not know it. */
    public static String speciesName(ResourceLocation species) {
        Species found = species == null ? null : PokemonSpecies.INSTANCE.getByIdentifier(species);
        if (found != null) return found.getTranslatedName().getString();
        String path = species == null ? "?" : species.getPath();
        return path.isEmpty() ? "?" : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation species = species(stack);
        return Component.literal(species == null ? "Renown Coin" : "Renown Coin: " + speciesName(species))
                .withStyle(ChatFormatting.GOLD);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        ResourceLocation species = species(stack);
        lines.add(Component.literal("Struck from a Daily Boss.").withStyle(ChatFormatting.GRAY));
        String type = primaryType(species);
        if (type != null) {
            lines.add(Component.literal("Album page: " + Character.toUpperCase(type.charAt(0)) + type.substring(1)).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.literal("Collect it in the Coin Album, or spend it at the Renown Vendor.").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public Item getPolymerItem(ItemStack stack, ServerPlayer player) {
        return Items.GOLD_NUGGET;
    }

    @Override
    public int getPolymerCustomModelData(ItemStack stack, ServerPlayer player) {
        String type = primaryType(species(stack));
        PolymerModelData face = type == null ? null : FACES.get(type + "_" + ("mythical".equals(tier(stack)) ? "platinum" : "gold"));
        return (face != null ? face : plain).value();
    }
}
