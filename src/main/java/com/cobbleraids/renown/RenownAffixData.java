package com.cobbleraids.renown;

import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Where a {@link RenownAffix} lives: one string in the Pokemon's own persistent data.
 *
 * <p>Persistent data rather than an entity tag, because the affix must outlive the boss. Cobblemon's
 * server codec ({@code PokemonP2}) saves it, so a trade or a PC move keeps it.
 *
 * <p>The raid catch (Capture Protocol) is the only way a player gets one: it copies the affix across
 * explicitly (RaidCaptureSession#affix). Every other copy of a boss -- the Personal Boss Shop
 * buy-back clone -- would carry it for free, so those copies {@link #clear} it.
 *
 * <p>Only the server writes it, and only from a boss's own roll. Reading refuses anything that is not
 * exactly a known id, so a hand-edited or foreign value gives no affix rather than an error.
 */
public final class RenownAffixData {
    /** Also read by the client-side codec Cobblemon syncs; harmless, it is an id and not a secret. */
    public static final String KEY = "cobbleraids_renown_affix";

    private RenownAffixData() {}

    public static Optional<RenownAffix> read(Pokemon pokemon) {
        if (pokemon == null) return Optional.empty();
        CompoundTag data = pokemon.getPersistentData();
        if (data == null || !data.contains(KEY, Tag.TAG_STRING)) return Optional.empty();
        return RenownAffix.byId(data.getString(KEY));
    }

    public static void write(Pokemon pokemon, RenownAffix affix) {
        pokemon.getPersistentData().putString(KEY, affix.id());
    }

    /** Removes any affix, for every copy of a boss that is not the raid catch. */
    public static void clear(Pokemon pokemon) {
        if (pokemon == null) return;
        CompoundTag data = pokemon.getPersistentData();
        if (data != null) data.remove(KEY);
    }
}
