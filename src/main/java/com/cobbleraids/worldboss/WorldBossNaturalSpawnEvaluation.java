package com.cobbleraids.worldboss;

/** Candidate identity scope for the native matching pass; never retains a position beyond that pass. */
public final class WorldBossNaturalSpawnEvaluation implements AutoCloseable {
    private static final ThreadLocal<WorldBossNaturalSpawnEvaluation> CURRENT = new ThreadLocal<>();
    private final WorldBossNaturalSpawnEvaluation previous;
    private final Object position;
    private final boolean blocked;
    private boolean suppressed;
    private WorldBossNaturalSpawnEvaluation(Object position, boolean blocked) {
        this.position = position; this.blocked = blocked; previous = CURRENT.get(); CURRENT.set(this);
    }
    public static WorldBossNaturalSpawnEvaluation open(Object position, boolean blocked) { return new WorldBossNaturalSpawnEvaluation(position, blocked); }
    public static boolean blocks(Object position) {
        var scope = CURRENT.get();
        if (scope == null || !scope.blocked || scope.position != position) return false;
        scope.suppressed = true; return true;
    }
    public boolean suppressed() { return suppressed; }
    @Override public void close() {
        if (CURRENT.get() != this) throw new IllegalStateException("Natural spawn scopes must close on their owning thread in order");
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
