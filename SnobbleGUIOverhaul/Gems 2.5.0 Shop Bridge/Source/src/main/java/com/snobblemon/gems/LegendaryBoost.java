package com.snobblemon.gems;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * The server-wide boost a Legendary Boost Ticket starts. It is our own, so it does not depend on Cobblemon Boosters (which has no legendary
 * boost): while it runs, the shiny odds of every Pokemon with one of the configured labels is multiplied (default 2x, 5 minutes, legendary
 * labels only). Using another ticket while it runs adds the minutes on top; the multiplier is never stacked. The end time is saved, so a
 * restart does not lose it.
 */
public final class LegendaryBoost {
    private final SnobbleGems mod;
    private volatile long endsAt;
    private volatile double multiplier = 1;
    private boolean hooked;

    public LegendaryBoost(SnobbleGems mod) {
        this.mod = mod;
    }

    private Path file() {
        return FabricLoader.getInstance().getGameDir().resolve("snobblegemcurrency").resolve("legendary_boost.json");
    }

    /** Registers the Cobblemon hook once the server is up (Cobblemon must be loaded). */
    public void start() {
        load();
        if (hooked) return;
        if (!FabricLoader.getInstance().isModLoaded("cobblemon")) {
            SnobbleGems.LOG.warn("[SnobbleGems] Cobblemon is not loaded: Legendary Boost Tickets cannot start a boost");
            return;
        }
        try {
            CobblemonEvents.SHINY_CHANCE_CALCULATION.subscribe(com.cobblemon.mod.common.api.Priority.NORMAL, event -> {
                try {
                    if (active() && appliesTo(event.getPokemon())) {
                        // the event's number is the "1 in X" odds, so a bigger chance means a smaller X
                        final float m = (float) multiplier;
                        event.addModificationFunction((chance, player, pokemon) -> Math.max(1f, chance / m));
                    }
                } catch (Throwable t) {
                    SnobbleGems.LOG.error("[SnobbleGems] legendary boost hook failed", t);
                }
                return Unit.INSTANCE;
            });
            hooked = true;
        } catch (Throwable t) {
            SnobbleGems.LOG.error("[SnobbleGems] could not hook Cobblemon's shiny chance; Legendary Boost Tickets will not work", t);
        }
    }

    public boolean hooked() { return hooked; }

    public boolean active() { return endsAt > System.currentTimeMillis(); }

    public long secondsLeft() { return Math.max(0, (endsAt - System.currentTimeMillis() + 999) / 1000); }

    public double multiplier() { return multiplier; }

    private boolean appliesTo(Pokemon pokemon) {
        Cfg.LegendaryTicket t = mod.cfg.legendaryTicket;
        if (!t.legendaryOnly) return true;
        Set<String> labels = pokemon.getSpecies().getLabels();
        for (String l : t.labels) if (labels.contains(l.toLowerCase())) return true;
        return false;
    }

    /** Starts the boost, or adds the ticket's minutes to one that is already running. @return false if the hook is missing. */
    public synchronized boolean add() {
        if (!hooked) return false;
        Cfg.LegendaryTicket t = mod.cfg.legendaryTicket;
        long now = System.currentTimeMillis();
        long base = Math.max(now, endsAt);
        endsAt = base + Math.round(t.minutes * 60_000.0);
        multiplier = Math.max(multiplier > 1 && now < base ? multiplier : 1, t.multiplier);
        save();
        return true;
    }

    /** Admin check: rolls {@code tries} Pokemon of one species through Cobblemon's normal creation (which fires the shiny-chance event) and counts the shinies. */
    public int rollShinies(String species, int tries) {
        int shiny = 0;
        for (int i = 0; i < tries; i++) {
            Pokemon p = com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(species + " level=10").create(null);
            if (p.getShiny()) shiny++;
        }
        return shiny;
    }

    public synchronized void stop() {
        endsAt = 0;
        save();
    }

    private void save() {
        try {
            Cfg.writeAtomic(file(), "{\"endsAt\":" + endsAt + ",\"multiplier\":" + multiplier + "}");
        } catch (Exception e) {
            SnobbleGems.LOG.warn("[SnobbleGems] could not save the legendary boost state: {}", e.toString());
        }
    }

    private void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            endsAt = o.get("endsAt").getAsLong();
            multiplier = o.get("multiplier").getAsDouble();
        } catch (Exception e) {
            SnobbleGems.LOG.warn("[SnobbleGems] ignoring unreadable legendary boost state: {}", e.toString());
        }
    }
}
