package com.cobbleraids.worldboss;

/** Per-viewer monotonic cursor. Slow ticks show only the latest due subtitle, never a backlog burst. */
final class WorldBossSceneTimeline {
    private final WorldBossSceneScript script;
    private long lastElapsed;
    private int nextBeat;
    WorldBossSceneTimeline(WorldBossSceneScript script) { this.script = script; }
    WorldBossSceneScript.Beat advance(long elapsedMillis) {
        if (elapsedMillis < lastElapsed) throw new IllegalArgumentException("Scene time moved backwards");
        lastElapsed = elapsedMillis; if (done(elapsedMillis)) return null;
        WorldBossSceneScript.Beat newest = null;
        while (nextBeat < script.beats().size() && script.beats().get(nextBeat).atMillis() <= elapsedMillis) newest = script.beats().get(nextBeat++);
        return newest;
    }
    boolean done(long elapsedMillis) { return elapsedMillis < 0 || elapsedMillis >= script.durationMillis(); }
}
