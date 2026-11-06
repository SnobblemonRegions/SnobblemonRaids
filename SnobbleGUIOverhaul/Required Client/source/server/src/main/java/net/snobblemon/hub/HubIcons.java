package net.snobblemon.hub;

import com.google.gson.Gson;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** The 16x16 button icons. Each is a paper item with a custom model, requested from Polymer at start-up. */
public final class HubIcons {
    private static final Map<String, PolymerModelData> MODELS = new HashMap<>();

    private HubIcons() {}

    /** Must run during mod initialisation, before Polymer builds the resource pack. */
    public static void init() {
        try (Reader r = new InputStreamReader(HubIcons.class.getResourceAsStream("/snobblehub_items.json"), StandardCharsets.UTF_8)) {
            String[] names = new Gson().fromJson(r, String[].class);
            for (String n : names) {
                MODELS.put(n, PolymerResourcePackUtils.requestModel(Items.PAPER,
                        ResourceLocation.fromNamespaceAndPath(SnobbleHub.MOD_ID, "item/" + n)));
            }
        } catch (Exception e) {
            SnobbleHub.LOGGER.error("Could not read the icon list: {}", e.toString());
        }
        PolymerResourcePackUtils.addModAssets(SnobbleHub.MOD_ID);
    }

    public static ItemStack stack(String name) {
        PolymerModelData d = MODELS.get(name);
        if (d == null) d = MODELS.get("blank");
        return d == null ? new ItemStack(Items.PAPER) : d.asStack();
    }
}
