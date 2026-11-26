package com.cobbleraids.renown;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobbleraids.RaidLog;
import com.cobbleraids.fault.RaidFaultBarrier;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * A renowned boss's look: the obsidian-and-gold recolour of its species, chosen by the aspect {@link #ASPECT}.
 *
 * <p>The textures and resolver files are generated from Cobblemon's own by {@code tools/renown_skins.py}; each resolver file
 * only adds variations that match the aspect and swap the texture, so the model, poses and layers stay Cobblemon's. They
 * reach players in the resource pack Polymer builds, listed in {@code assets/cobbleraids/renowned_skins.txt}. A species with
 * no recolour (a legendary whose model comes from the server's own model pack) simply keeps its usual texture.
 */
public final class RenownLook {
    public static final String ASPECT = "cobbleraids_renowned";
    private static final String INDEX = "assets/cobbleraids/renowned_skins.txt";

    private RenownLook() {}

    /** Adds the renowned aspect to whatever aspects the boss is already forced to have (a definition's own skin, say). */
    public static void apply(Pokemon pokemon) {
        Set<String> forced = new HashSet<>(pokemon.getForcedAspects());
        if (!forced.add(ASPECT)) return;
        pokemon.setForcedAspects(forced);
        pokemon.updateAspects();
    }

    /** Adds the recolours to the resource pack Polymer builds and serves. */
    public static void registerPack() {
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(builder ->
                RaidFaultBarrier.guard("resource-pack:renowned-skins", () -> {
                    String index = read(INDEX);
                    if (index == null) return;
                    int added = 0;
                    for (String path : index.split("\n")) {
                        path = path.trim();
                        if (path.isEmpty()) continue;
                        byte[] bytes = bytes(path);
                        if (bytes == null) continue;
                        builder.addData(path, bytes);
                        added++;
                    }
                    RaidLog.info("Renowned boss skins: {} file(s) added to the resource pack", added);
                }));
    }

    private static String read(String path) {
        byte[] bytes = bytes(path);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String path) {
        try (InputStream in = RenownLook.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                RaidLog.error("Renowned skin file is missing from the jar: {}", path);
                return null;
            }
            return in.readAllBytes();
        } catch (IOException e) {
            RaidLog.error("Could not read renowned skin file {}: {}", path, e.toString());
            return null;
        }
    }
}
