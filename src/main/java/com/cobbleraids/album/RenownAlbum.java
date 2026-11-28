package com.cobbleraids.album;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The Coin Album's rules, with no Minecraft types: which species it has a slot for, grouped by primary type, and how far a player's album is.
 *
 * <p>The album has a slot for every species the Daily Boss can field (its pool is the album, so nothing is collectable that can never drop).
 * A slot is filled by pressing a coin of that species into it; the coin is then kept server-side, safe, until the player takes it out again.
 */
public final class RenownAlbum {
    private RenownAlbum() {}

    /** How far one album is. */
    public record Progress(int pressed, int total, Set<String> completedTypes, boolean complete) {}

    /**
     * @param slots   every species in the album (id, e.g. cobblemon:rayquaza) to its primary type ("dragon")
     * @param pressed the species this player has pressed in
     */
    public static Progress progress(Map<String, String> slots, Set<String> pressed) {
        int filled = 0;
        Map<String, int[]> byType = new LinkedHashMap<>();
        for (Map.Entry<String, String> slot : slots.entrySet()) {
            int[] counts = byType.computeIfAbsent(slot.getValue(), type -> new int[2]);
            counts[1]++;
            if (pressed.contains(slot.getKey())) {
                filled++;
                counts[0]++;
            }
        }
        Set<String> completed = new TreeSet<>();
        byType.forEach((type, counts) -> {
            if (counts[1] > 0 && counts[0] == counts[1]) completed.add(type);
        });
        return new Progress(filled, slots.size(), Set.copyOf(completed), !slots.isEmpty() && filled == slots.size());
    }

    /** The album's pages: each type that has at least one species, in the order of {@code typeOrder}. */
    public static List<String> pages(Map<String, String> slots, List<String> typeOrder) {
        Set<String> present = Set.copyOf(slots.values());
        return typeOrder.stream().filter(present::contains).toList();
    }
}
