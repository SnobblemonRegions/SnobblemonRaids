package com.cobbleraids.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Prototype models use the normal server pack and independently allocated Polymer model IDs. */
public final class SovereignSealMaterial {
    private static PolymerModelData band;
    private static PolymerModelData crest;
    private static PolymerModelData halo;
    private static PolymerModelData energy;
    private static PolymerModelData ground;

    private SovereignSealMaterial() {}

    public static void register() {
        if (band != null) return;
        band = PolymerResourcePackUtils.requestModel(Items.PAPER,
                ResourceLocation.fromNamespaceAndPath("cobbleraids", "item/worldboss_sovereign_band"));
        crest = PolymerResourcePackUtils.requestModel(Items.PAPER,
                ResourceLocation.fromNamespaceAndPath("cobbleraids", "item/worldboss_sovereign_crest"));
        halo = PolymerResourcePackUtils.requestModel(Items.PAPER,
                ResourceLocation.fromNamespaceAndPath("cobbleraids", "item/worldboss_sovereign_halo"));
        energy = PolymerResourcePackUtils.requestModel(Items.PAPER,
                ResourceLocation.fromNamespaceAndPath("cobbleraids", "item/worldboss_sovereign_energy"));
        ground = PolymerResourcePackUtils.requestModel(Items.PAPER,
                ResourceLocation.fromNamespaceAndPath("cobbleraids", "item/worldboss_sovereign_ground"));
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(builder ->
                RaidFaultBarrier.guard("resource-pack:sovereign-prototype", () -> {
                    for (String path : new String[]{"models/item/worldboss_sovereign_band.json", "models/item/worldboss_sovereign_crest.json",
                            "models/item/worldboss_sovereign_halo.json", "models/item/worldboss_sovereign_energy.json", "models/item/worldboss_sovereign_ground.json",
                            "textures/item/sovereign_gold.png", "textures/item/sovereign_black.png", "textures/item/sovereign_red.png",
                            "textures/item/sovereign_light.png", "textures/item/sovereign_ember.png",
                            "textures/item/sovereign_gold_side.png", "textures/item/sovereign_gold_shadow.png",
                            "textures/item/sovereign_black_side.png", "textures/item/sovereign_black_shadow.png",
                            "textures/item/sovereign_red_side.png", "textures/item/sovereign_red_shadow.png"}) {
                        String resource = "assets/cobbleraids/" + path;
                        try (var input = SovereignSealMaterial.class.getClassLoader().getResourceAsStream(resource)) {
                            if (input == null) throw new IOException("Missing Sovereign Seal prototype art: " + resource);
                            builder.addData(resource, input.readAllBytes());
                        } catch (IOException failure) {
                            throw new UncheckedIOException("Could not pack " + resource, failure);
                        }
                    }
                }));
    }

    static ItemStack band() { return band.asStack(); }
    static ItemStack crest() { return crest.asStack(); }
    static ItemStack halo() { return halo.asStack(); }
    static ItemStack energy() { return energy.asStack(); }
    static ItemStack ground() { return ground.asStack(); }
}
