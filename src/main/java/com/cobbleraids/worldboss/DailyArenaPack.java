package com.cobbleraids.worldboss;

import com.cobbleraids.RaidLog;
import com.cobbleraids.fault.RaidFaultBarrier;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/**
 * The Daily Boss arena's art in the resource pack Polymer serves (drawn by {@code tools/daily_arena_art.py}): the Half Veil's panel model and
 * its animated texture, the seal's six flat panels (outer and inner, bronze, gold and ember), and the renowned gold boss bar, which reskins vanilla's WHITE bar (only the Daily Boss uses WHITE: AscensionLib reskins PINK, SpookySurge YELLOW and BLUE).
 */
public final class DailyArenaPack {
    private DailyArenaPack() {}

    static final List<String> FILES = List.of(
            "assets/cobbleraids/models/item/daily_veil.json",
            "assets/cobbleraids/textures/item/daily_veil.png",
            "assets/cobbleraids/textures/item/daily_veil.png.mcmeta",
            "assets/minecraft/textures/gui/sprites/boss_bar/white_background.png",
            "assets/minecraft/textures/gui/sprites/boss_bar/white_progress.png");

    /** Every model/texture of the seal's panels: outer and inner, in bronze, gold and ember (drawn by tools/daily_seal_art.py). */
    static List<String> sealFiles() {
        List<String> files = new java.util.ArrayList<>();
        for (String key : WorldBossSeal.modelKeys()) {
            files.add("assets/cobbleraids/models/item/daily_seal_" + key + ".json");
            files.add("assets/cobbleraids/textures/item/daily_seal_" + key + ".png");
        }
        return files;
    }

    /** Called once at start-up, before Polymer builds the pack: claims the veil's model number and adds the files. */
    public static void register() {
        PolymerModelData veil = PolymerResourcePackUtils.requestModel(Items.GLOWSTONE_DUST,
                ResourceLocation.fromNamespaceAndPath("cobbleraids", "item/daily_veil"));
        WorldBossDome.veilItem = veil.item();
        WorldBossDome.veilModel = veil.value();
        for (String key : WorldBossSeal.modelKeys()) {
            PolymerModelData panel = PolymerResourcePackUtils.requestModel(WorldBossSeal.baseItem(),
                    ResourceLocation.fromNamespaceAndPath("cobbleraids", "item/daily_seal_" + key));
            WorldBossSeal.registerModel(key, panel.item(), panel.value());
        }
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(builder ->
                RaidFaultBarrier.guard("resource-pack:daily-arena", () -> {
                    List<String> all = new java.util.ArrayList<>(FILES);
                    all.addAll(sealFiles());
                    for (String path : all) {
                        try (InputStream in = DailyArenaPack.class.getClassLoader().getResourceAsStream(path)) {
                            if (in == null) {
                                RaidLog.error("Daily Boss arena art is missing from the jar: {}", path);
                                continue;
                            }
                            builder.addData(path, in.readAllBytes());
                        } catch (IOException e) {
                            RaidLog.error("Could not read Daily Boss arena art {}: {}", path, e.toString());
                        }
                    }
                }));
    }
}
