package com.cobbleraids.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

/** Owned dome art with Polymer-allocated model numbers, independent of seasonal AscensionLib panels. */
public final class WorldBossDomeMaterial {
    private static final PolymerModelData[] MODELS = new PolymerModelData[DomeGeometry.SWIRL_VARIANTS + 1];
    private static boolean registered;

    private WorldBossDomeMaterial() {}

    public static void register() {
        if (registered) return;
        for (int variant = 0; variant < MODELS.length; variant++) {
            MODELS[variant] = PolymerResourcePackUtils.requestModel(Items.PAPER,
                    ResourceLocation.fromNamespaceAndPath("cobbleraids", "item/worldboss_distortion_dome_" + variant));
        }
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(builder ->
                RaidFaultBarrier.guard("resource-pack:worldboss-dome", () -> {
                    for (int variant = 0; variant < MODELS.length; variant++) {
                        String name = "worldboss_distortion_dome_" + variant;
                        for (String path : new String[]{"models/item/" + name + ".json",
                                "textures/item/" + name + ".png", "textures/item/" + name + ".png.mcmeta"}) {
                            String resource = "assets/cobbleraids/" + path;
                            try (var input = WorldBossDomeMaterial.class.getClassLoader().getResourceAsStream(resource)) {
                                if (input == null) throw new IOException("Missing World Boss dome art: " + resource);
                                builder.addData(resource, input.readAllBytes());
                            } catch (IOException failure) {
                                throw new UncheckedIOException("Could not pack " + resource, failure);
                            }
                        }
                    }
                }));
        registered = true;
    }

    static ItemStack stack(int variant) {
        if (!registered) throw new IllegalStateException("World Boss dome materials were not registered at startup");
        return MODELS[variant].asStack();
    }
}
