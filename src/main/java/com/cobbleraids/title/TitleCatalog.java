package com.cobbleraids.title;

import com.cobbleraids.RaidLog;
import com.cobbleraids.config.RaidRarityTier;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every trainer title an operator has configured, in the order players unlock them.
 *
 * <p>Free of Minecraft types, like {@link com.cobbleraids.shop.ShopCatalog}, so parsing and unlock
 * evaluation are both testable without a server.
 */
public record TitleCatalog(List<TitleDefinition> titles) {

    public TitleCatalog {
        titles = List.copyOf(titles == null ? List.of() : titles);
    }

    /**
     * A small, immediately useful starter set, so a fresh server has titles to earn on day one
     * instead of an empty file an operator has to populate before the feature does anything.
     */
    public static TitleCatalog defaults() {
        return new TitleCatalog(List.of(
                new TitleDefinition("novice_raider", "Novice Raider", "GRAY",
                        TitleUnlockType.RAIDS_WON, null, 1),
                new TitleDefinition("veteran_raider", "Veteran Raider", "AQUA",
                        TitleUnlockType.RAIDS_WON, null, 25),
                new TitleDefinition("champion_raider", "Champion Raider", "GOLD",
                        TitleUnlockType.RAIDS_WON, null, 100),
                new TitleDefinition("legend_slayer", "Legend Slayer", "LIGHT_PURPLE",
                        TitleUnlockType.TIER_WINS, RaidRarityTier.LEGENDARY, 10),
                new TitleDefinition("collector", "Collector", "GREEN",
                        TitleUnlockType.BOSSES_CAUGHT, null, 50)))
                .withAlbumTitles();
    }

    /** Version 2 added the Coin Album's titles; an older file gets them once (see {@link #fromJson}). */
    public static final int CURRENT_VERSION = 2;
    static final List<String> ALBUM_TYPES = List.of("normal", "fire", "water", "electric", "grass", "ice", "fighting", "poison", "ground",
            "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy");

    /**
     * The Coin Album's titles (owner, 2026-11-27): pressed-species milestones, one "<Type> Curator" per album page, and Renowned for the whole
     * album. A type title is earnable only while the Daily Boss pool has species of that type.
     */
    static List<TitleDefinition> albumTitles() {
        List<TitleDefinition> out = new ArrayList<>(List.of(
                new TitleDefinition("album_coin_bearer", "Coin Bearer", "YELLOW", TitleUnlockType.ALBUM_SPECIES, null, 10),
                new TitleDefinition("album_numismatist", "Numismatist", "GOLD", TitleUnlockType.ALBUM_SPECIES, null, 25),
                new TitleDefinition("album_curator", "Curator", "GOLD", TitleUnlockType.ALBUM_SPECIES, null, 50),
                new TitleDefinition("album_grand_curator", "Grand Curator", "GOLD", TitleUnlockType.ALBUM_SPECIES, null, 100),
                new TitleDefinition("album_renowned", "Renowned", "LIGHT_PURPLE", TitleUnlockType.ALBUM_COMPLETE, null, 1)));
        for (String type : ALBUM_TYPES) {
            String name = Character.toUpperCase(type.charAt(0)) + type.substring(1);
            out.add(new TitleDefinition("album_curator_" + type, name + " Curator", "YELLOW", TitleUnlockType.ALBUM_TYPE, null, 1, type));
        }
        return out;
    }

    /** This catalogue with every album title it does not have yet, at the end. */
    TitleCatalog withAlbumTitles() {
        List<TitleDefinition> out = new ArrayList<>(titles);
        Set<String> ids = new HashSet<>();
        titles.forEach(title -> ids.add(title.id()));
        for (TitleDefinition album : albumTitles()) if (ids.add(album.id())) out.add(album);
        return new TitleCatalog(out);
    }

    /**
     * Reads a catalogue, dropping what it cannot use rather than refusing the file -- see
     * {@link TitleDefinition#fromJson} and {@link com.cobbleraids.shop.ShopCatalog#fromJson} for why.
     */
    public static TitleCatalog fromJson(JsonObject root) {
        List<TitleDefinition> titles = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        JsonArray array = root.has("titles") && root.get("titles").isJsonArray()
                ? root.getAsJsonArray("titles") : new JsonArray();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                RaidLog.error("titles.json: dropping a title that is not an object");
                continue;
            }
            TitleDefinition title = TitleDefinition.fromJson(element.getAsJsonObject());
            if (title == null) continue;
            if (!seen.add(title.id())) {
                RaidLog.error("titles.json: dropping a second title called " + title.id());
                continue;
            }
            titles.add(title);
        }
        // A file from before the Coin Album gets its titles once; saved as version 2, an operator's deletions then stay deleted.
        int version = root.has("version") ? root.get("version").getAsInt() : 1;
        TitleCatalog catalog = new TitleCatalog(titles);
        return version < CURRENT_VERSION ? catalog.withAlbumTitles() : catalog;
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", CURRENT_VERSION);
        JsonArray array = new JsonArray();
        titles.forEach(title -> array.add(title.toJson()));
        root.add("titles", array);
        return root;
    }

    /** Every title by id, in file order -- the order a player's {@code /cobbleraids title list} shows. */
    public Map<String, TitleDefinition> byId() {
        Map<String, TitleDefinition> index = new LinkedHashMap<>();
        titles.forEach(title -> index.putIfAbsent(title.id(), title));
        return java.util.Collections.unmodifiableMap(index);
    }
}
