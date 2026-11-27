package com.cobbleraids.worldboss;

/**
 * The Daily Boss's seal: the stages it goes through and how fast it turns. Pure maths with no Minecraft types; {@code WorldBossSeal} draws
 * it and {@code tools/daily_seal_art.py} holds its layout (outer radius {@link #RADIUS}).
 *
 * <p>Three stages follow the fight: while players gather it forms (bronze, a comet running round the rim as the countdown); once the fight
 * opens it is gold and turns once a minute; when the shared health falls under a quarter it burns low (ember red), turning three times as fast.
 */
final class DailySeal {

    /** Outer radius of the seal, in blocks. */
    static final double RADIUS = 30.0;
    /** Under this share of the shared health the seal burns low. */
    static final float LOW_SHARE = 0.25f;

    enum Stage { GATHER, OPEN, LOW }

    /** The stage, and how far through the gather countdown it is (1 once the fight is open). */
    record Phase(Stage stage, double progress) {}

    private DailySeal() {}

    /**
     * @param gatherSecondsLeft seconds before the fight opens (0 or less once it has)
     * @param countdownSeconds  the whole gather countdown
     * @param healthShare       the shared pool's current health over its maximum
     */
    static Phase phase(int gatherSecondsLeft, int countdownSeconds, float healthShare) {
        if (gatherSecondsLeft > 0 && countdownSeconds > 0) {
            double drawn = 1.0 - Math.min(1.0, (double) gatherSecondsLeft / countdownSeconds);
            return new Phase(Stage.GATHER, drawn);
        }
        return new Phase(healthShare < LOW_SHARE ? Stage.LOW : Stage.OPEN, 1.0);
    }

    /** How far the seal turns in {@code ticks} game ticks: not at all while forming, once a minute open, three times a minute low. */
    static double angle(Stage stage, long ticks) {
        double turnsPerMinute = switch (stage) {
            case GATHER -> 0.0;
            case OPEN -> 1.0;
            case LOW -> 3.0;
        };
        return 2 * Math.PI * turnsPerMinute * ticks / 1200.0;
    }
}
