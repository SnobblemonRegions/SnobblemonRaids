package com.cobbleraids.worldboss;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Exact milli-HP authority for new events; float values are compatibility/display projections only. */
public final class WorldBossFixedHealth {
    public static final long UNITS_PER_HP = 1000;
    /** Every whole opening HP value remains exact in the existing float encounter/listener API. */
    public static final long MAX_WHOLE_HP = 1L << 24;

    public record Snapshot(long maxUnits, long currentUnits, Map<UUID, Long> damageUnits,
                           Map<UUID, BigDecimal> fractionalUnits) {
        public Snapshot {
            damageUnits = Map.copyOf(damageUnits);
            fractionalUnits = Map.copyOf(fractionalUnits);
            if (maxUnits < UNITS_PER_HP || maxUnits % UNITS_PER_HP != 0
                    || maxUnits > MAX_WHOLE_HP * UNITS_PER_HP || currentUnits < 0 || currentUnits > maxUnits) {
                throw new IllegalArgumentException("Invalid fixed World Boss health");
            }
            long total = 0;
            for (long damage : damageUnits.values()) {
                if (damage <= 0) throw new IllegalArgumentException("Invalid fixed damage");
                total = Math.addExact(total, damage);
            }
            if (total != maxUnits - currentUnits) throw new IllegalArgumentException("Fixed damage does not reconcile");
            for (BigDecimal fraction : fractionalUnits.values()) {
                if (fraction.scale() > 50 || fraction.signum() <= 0 || fraction.compareTo(BigDecimal.ONE) >= 0) {
                    throw new IllegalArgumentException("Invalid fractional damage carry");
                }
            }
        }
    }

    private final long maxUnits;
    private long currentUnits;
    private final Map<UUID, Long> damage = new LinkedHashMap<>();
    private final Map<UUID, BigDecimal> fractions = new HashMap<>();

    public WorldBossFixedHealth(long wholeHp) {
        this(new Snapshot(Math.multiplyExact(wholeHp, UNITS_PER_HP), Math.multiplyExact(wholeHp, UNITS_PER_HP), Map.of(), Map.of()));
    }

    public WorldBossFixedHealth(Snapshot snapshot) {
        maxUnits = snapshot.maxUnits();
        currentUnits = snapshot.currentUnits();
        damage.putAll(snapshot.damageUnits());
        fractions.putAll(snapshot.fractionalUnits());
    }

    /** Convert once, carrying sub-milli-HP per player. Overkill never becomes credit. */
    public long strike(UUID player, float amount) {
        if (player == null || !Float.isFinite(amount) || amount <= 0 || currentUnits == 0) return 0;
        BigDecimal requested = new BigDecimal(Float.toString(amount)).movePointRight(3)
                .add(fractions.getOrDefault(player, BigDecimal.ZERO));
        long applied;
        if (requested.compareTo(BigDecimal.valueOf(currentUnits)) >= 0) {
            applied = currentUnits;
            fractions.remove(player);
        } else {
            applied = requested.setScale(0, RoundingMode.DOWN).longValueExact();
            BigDecimal remainder = requested.subtract(BigDecimal.valueOf(applied)).stripTrailingZeros();
            if (remainder.signum() == 0) fractions.remove(player);
            else fractions.put(player, remainder);
        }
        currentUnits -= applied;
        if (applied > 0) damage.merge(player, applied, Math::addExact);
        return applied;
    }

    public long maxUnits() { return maxUnits; }
    public long currentUnits() { return currentUnits; }
    public long contribution(UUID player) { return damage.getOrDefault(player, 0L); }
    public Snapshot snapshot() { return new Snapshot(maxUnits, currentUnits, damage, fractions); }
    public Map<UUID, Float> projectedDamage() {
        Map<UUID, Float> projected = new LinkedHashMap<>();
        damage.forEach((player, units) -> projected.put(player, (float) (units / (double) UNITS_PER_HP)));
        return projected;
    }
}
