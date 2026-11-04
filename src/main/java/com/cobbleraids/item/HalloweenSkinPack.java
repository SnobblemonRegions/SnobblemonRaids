package com.cobbleraids.item;

import com.cobbleraids.RaidLog;
import com.cobbleraids.fault.RaidFaultBarrier;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Adds the Halloween World Boss skins (Cobblemon resolvers, models and textures, selected by the {@code halloween} aspect) to the resource pack
 * Polymer builds and serves, so clients get them with the rest of the server pack.
 *
 * <p>The files live in the repo at {@code compat/halloween/resourcepack}; Gradle copies them into the jar under {@code halloween_pack/} (not
 * {@code assets/}, so a client that merely has the mod installed does not load them as its own). Each is added to the pack at its original path.
 */
public final class HalloweenSkinPack {
    private HalloweenSkinPack() {}

    static final String JAR_ROOT = "halloween_pack/";

    /** Every file of the skin pack, as a path inside the pack. A test checks each one is in the jar. */
    static final List<String> FILES = List.of(
            "assets/cobblemon/bedrock/pokemon/models/0133_eevee/eevee_halloween.geo.json",
            "assets/cobblemon/bedrock/pokemon/models/0151_mew/mew_halloween.geo.json",
            "assets/cobblemon/bedrock/pokemon/resolvers/0133_eevee/1_eevee_halloween.json",
            "assets/cobblemon/bedrock/pokemon/resolvers/0151_mew/1_mew_halloween.json",
            "assets/cobblemon/bedrock/pokemon/resolvers/0175_togepi/1_togepi_halloween.json",
            "assets/cobblemon/bedrock/pokemon/resolvers/0708_phantump/1_phantump_halloween.json",
            "assets/cobblemon/bedrock/pokemon/resolvers/0722_rowlet/1_rowlet_halloween.json",
            "assets/cobblemon/bedrock/pokemon/resolvers/0778_mimikyu/1_mimikyu_halloween.json",
            "assets/cobblemon/bedrock/pokemon/resolvers/0802_marshadow/1_marshadow_halloween.json",
            "assets/cobblemon/textures/pokemon/0133_eevee/eevee_halloween.png",
            "assets/cobblemon/textures/pokemon/0133_eevee/eevee_halloween_shiny.png",
            "assets/cobblemon/textures/pokemon/0151_mew/mew_halloween.png",
            "assets/cobblemon/textures/pokemon/0151_mew/mew_halloween_shiny.png",
            "assets/cobblemon/textures/pokemon/0175_togepi/togepi_halloween.png",
            "assets/cobblemon/textures/pokemon/0175_togepi/togepi_halloween_shiny.png",
            "assets/cobblemon/textures/pokemon/0708_phantump/phantump_halloween.png",
            "assets/cobblemon/textures/pokemon/0708_phantump/phantump_halloween_shiny.png",
            "assets/cobblemon/textures/pokemon/0722_rowlet/rowlet_halloween.png",
            "assets/cobblemon/textures/pokemon/0722_rowlet/rowlet_halloween_shiny.png",
            "assets/cobblemon/textures/pokemon/0778_mimikyu/mimikyu_halloween.png",
            "assets/cobblemon/textures/pokemon/0778_mimikyu/mimikyu_halloween_shiny.png",
            "assets/cobblemon/textures/pokemon/0802_marshadow/marshadow_halloween.png",
            "assets/cobblemon/textures/pokemon/0802_marshadow/marshadow_halloween_shiny.png");

    public static void register() {
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(builder ->
                RaidFaultBarrier.guard("resource-pack:halloween-skins", () -> {
                    for (String path : FILES) add(builder, path);
                }));
    }

    private static void add(eu.pb4.polymer.resourcepack.api.ResourcePackBuilder builder, String path) {
        try (InputStream in = HalloweenSkinPack.class.getClassLoader().getResourceAsStream(JAR_ROOT + path)) {
            if (in == null) {
                RaidLog.error("Halloween skin art is missing from the jar: {}", path);
                return;
            }
            builder.addData(path, in.readAllBytes());
        } catch (IOException e) {
            RaidLog.error("Could not read Halloween skin art {}: {}", path, e.toString());
        }
    }
}
