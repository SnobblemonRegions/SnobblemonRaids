package com.cobbleraids.worldboss;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The shared health pool of a World Boss, and who took it down.
 *
 * <p>Pure arithmetic with no Minecraft types, like {@link com.cobbleraids.raid.RaidProgress}, so
 * the rules can be tested without a server. Every duel reports its damage here as it happens.
 *
 * <p>Three rules matter. A strike is clamped to what is left, so contributions always add up to
 * exactly the damage the pool took and the last hitter cannot be credited with overkill. The
 * defeat is one-way and reported exactly once, because strikes arrive from many duels in the same
 * tick and every one of them must not be able to pay the reward. And damage already dealt is never
 * undone: when more players join the max rises by the difference; when the daily boss shrinks for
 * players who left ({@link #resizeTo}) it keeps the fraction of health left.
 *
 * <p>Synchronised: strikes come from separate duels' Showdown handlers, the state is written by a
 * timer and read by commands.
 */
public final class WorldBossPool {

    private float max;
    private float current;
    private boolean defeated;
    private final Map<UUID, Float> contribution = new LinkedHashMap<>();

    public WorldBossPool(float max) {
        if (!(max > 0f)) throw new IllegalArgumentException("a World Boss pool needs a positive max, got " + max);
        this.max = max;
        this.current = max;
    }

    /** Restores a saved pool. The contribution map is copied. */
    public WorldBossPool(float max, float current, boolean defeated, Map<UUID, Float> contribution) {
        this(max);
        this.current = Math.max(0f, Math.min(max, current));
        this.defeated = defeated;
        if (defeated) this.current = 0f;
        this.contribution.putAll(contribution);
    }

    /**
     * Takes damage off the pool, credited to {@code player}.
     *
     * @return what was actually applied: the amount clamped to what the pool had left, so 0 once it
     *         is empty or for a non-positive or non-finite amount
     */
    public synchronized float strike(UUID player, float amount) {
        if (player == null || defeated || !(amount > 0f) || Float.isInfinite(amount)) return 0f;
        float applied = Math.min(amount, current);
        current -= applied;
        contribution.merge(player, applied, Float::sum);
        return applied;
    }

    /**
     * Raises the max to {@code newMax}, adding the difference to the current health too. A smaller
     * or equal value, or a defeated pool, changes nothing.
     */
    public synchronized void growTo(float newMax) {
        if (defeated || !(newMax > max) || Float.isInfinite(newMax)) return;
        current += newMax - max;
        max = newMax;
    }

    /**
     * Moves the max to {@code newMax}. Larger: as {@link #growTo}, the difference is added to the health left. Smaller: the health left
     * keeps its fraction of the max, so a boss at 40% stays at 40%, damage already dealt still counts, and a shrink can never empty the
     * pool. The two are uneven on purpose: leaving and coming back adds more health than the leave took away, so cycling never weakens
     * the boss. A defeated pool, or a value that is not positive and finite, changes nothing.
     */
    public synchronized void resizeTo(float newMax) {
        if (defeated || !(newMax > 0f) || Float.isInfinite(newMax)) return;
        if (newMax >= max) {
            growTo(newMax);
            return;
        }
        float left = current / max;
        max = newMax;
        current = current <= 0f ? 0f : Math.max(Math.ulp(newMax), Math.min(newMax, newMax * left));
    }

    /**
     * Marks the boss defeated if the pool is empty.
     *
     * @return true for exactly one call, the first after the pool reached zero; false before that
     *         and ever after
     */
    public synchronized boolean claimDefeat() {
        if (defeated || current > 0f) return false;
        defeated = true;
        return true;
    }

    public synchronized float max() { return max; }
    public synchronized float current() { return current; }
    public synchronized boolean isDefeated() { return defeated; }

    /** A copy: the live map keeps changing while duels are still being credited. */
    public synchronized Map<UUID, Float> contributions() { return new LinkedHashMap<>(contribution); }

    public synchronized float contributionOf(UUID player) { return contribution.getOrDefault(player, 0f); }

    /** Total damage dealt so far, equal to {@code max - current}. */
    public synchronized float totalDamage() { return max - current; }

    /** One player's share of the damage dealt, 0 to 1; 0 when nothing has been dealt. */
    public synchronized float shareOf(UUID player) {
        float total = max - current;
        return total <= 0f ? 0f : contribution.getOrDefault(player, 0f) / total;
    }
}
