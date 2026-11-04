package com.cobbleraids.raidio;

/**
 * The maths behind Raidio: which way a raid lies, how well the player is tuned to it, and how fast and how high the tune plays. Raidio gives a direction and
 * a sense of nearness and never a position: inside {@link #ALIGNED_DEGREES} of the raid the tune is clear and it fades out by {@link #FADE_DEGREES}; the tune
 * runs at {@link #SLOW_BPM} a long way off and quickens towards {@link #FAST_BPM} as the player closes in, rising in pitch as it goes. No Minecraft types.
 */
public final class RaidioTuning {
    /** The tune is fully clear this close to the raid's bearing, and fades to nothing at {@link #FADE_DEGREES}. */
    public static final double ALIGNED_DEGREES = 15, FADE_DEGREES = 45;
    /** Distances at which the tune is at its slowest and lowest ({@link #FAR_BLOCKS}) and its quickest and highest ({@link #NEAR_BLOCKS}). */
    public static final double FAR_BLOCKS = 1500, NEAR_BLOCKS = 40;
    /** The tune's pitch spans this many semitones as the raid goes from far to near. */
    public static final int NOTE_RANGE = 12;
    /** Beats per minute at the far end (a slow heartbeat) and the near end. A beat is a sixteenth note. */
    public static final double SLOW_BPM = 50, FAST_BPM = 130;
    /**
     * The tempo may change by at most this many BPM a step (15 a second), so a sudden jump in distance, such as a nearer raid being picked, is a ramp and not a
     * cut.
     */
    public static final double MAX_BPM_PER_STEP = 1.5;
    /**
     * A step is a tenth of a second and a sixteenth note lasts 15 / bpm seconds, so the tune moves on by bpm / 150 of a position each step. At {@link #FAST_BPM}
     * that is under one position a step, which the listener relies on.
     */
    public static final double BPM_PER_POSITION_PER_STEP = 150;
    /** Within this many blocks of the raid there is no direction left to tune to, so it plays whichever way the player faces. */
    public static final double ARRIVED_BLOCKS = 12;

    private RaidioTuning() {}

    /** The yaw, in Minecraft's convention (0 faces south, 90 west), that a player at the origin would face to look at ({@code dx}, {@code dz}) east and south of them. */
    public static double targetYaw(double dx, double dz) {
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** How far the player must turn to face the target: positive is clockwise (towards west), negative the other way, always the short way round. */
    public static double offset(double playerYaw, double targetYaw) {
        if (!Double.isFinite(playerYaw) || !Double.isFinite(targetYaw)) return 0;
        return ((targetYaw - playerYaw + 180) % 360 + 360) % 360 - 180;
    }

    /** 1 inside the aligned window, easing down to 0 at the fade limit and beyond. Anything that is not a number is not tuned in. */
    public static double clarity(double offsetDegrees) {
        if (!Double.isFinite(offsetDegrees)) return 0;
        double angle = Math.abs(offsetDegrees);
        if (angle <= ALIGNED_DEGREES) return 1;
        if (angle >= FADE_DEGREES) return 0;
        double x = (FADE_DEGREES - angle) / (FADE_DEGREES - ALIGNED_DEGREES);
        return x * x * (3 - 2 * x);
    }

    /** How near the raid is, 0 at {@link #FAR_BLOCKS} or beyond to 1 at {@link #NEAR_BLOCKS} or closer, on a log scale so the last stretch is the most audible. */
    public static double closeness(double distance) {
        if (Double.isNaN(distance) || distance >= FAR_BLOCKS) return 0;
        if (distance <= NEAR_BLOCKS) return 1;
        return (Math.log(FAR_BLOCKS) - Math.log(distance)) / (Math.log(FAR_BLOCKS) - Math.log(NEAR_BLOCKS));
    }

    /** The tempo for a raid {@code distance} blocks away: {@link #SLOW_BPM} far off, quickening to {@link #FAST_BPM}. */
    public static double bpm(double distance) {
        return SLOW_BPM + (FAST_BPM - SLOW_BPM) * closeness(distance);
    }

    /** The tune's lowest note for a raid {@code distance} blocks away: 0 far off, rising to {@link #NOTE_RANGE} up close. */
    public static int baseNote(double distance) {
        return (int) Math.round(closeness(distance) * NOTE_RANGE);
    }

    /** The vanilla note-block pitch for a note: 0.5 at note 0, 1.0 at 12, 2.0 at 24, and nothing outside what a client can play. */
    public static float pitch(int note) {
        int clamped = Math.clamp(note, 0, 24);
        return (float) Math.pow(2, (clamped - 12) / 12.0);
    }
}
