package com.cobbleraids.raidio;

import com.cobbleraids.config.RaidRarityTier;
import java.util.ArrayList;
import java.util.List;

/**
 * One player's Raidio: each step (two game ticks) it is told where they face and where their raid is, and says what they should hear. Silent when there is no
 * raid or they are neither sweeping nor tuned in. Tuned in, the raid's tier plays its tune ({@link RaidioMotifs}) at a tempo that follows the distance, slow far
 * off and quickening as they close in; it breaks up as they drift off the bearing. While they sweep and are not tuned in they hear a bleep that rises as
 * they turn towards the raid, and the odd faint tick. Deterministic, so a given sequence of turns always sounds the same. No Minecraft types.
 *
 * <p>Nothing here ever jumps: the tempo eases towards the distance's tempo, and the tune changes pitch only at the start of a bar, never in the middle of a
 * phrase.
 */
public final class RaidioListener {
    private final RaidioSweep sweep = new RaidioSweep();
    private int step, lastPosition = -1, key = -1;
    private double phase;
    private double bpm = Double.NaN;

    /**
     * @param yaw the player's yaw, Minecraft's convention
     * @param dx blocks east of the player the raid is (negative west)
     * @param dz blocks south of the player the raid is (negative north)
     */
    public List<RaidioCue> step(boolean hasTarget, double yaw, double dx, double dz, RaidRarityTier tier) {
        int n = step++;
        boolean sweeping = sweep.update(yaw);
        if (!hasTarget || !Double.isFinite(dx) || !Double.isFinite(dz)) return List.of();

        double distance = Math.hypot(dx, dz);
        boolean arrived = distance < RaidioTuning.ARRIVED_BLOCKS;
        double offset = arrived ? 0 : RaidioTuning.offset(yaw, RaidioTuning.targetYaw(dx, dz));
        double clarity = RaidioTuning.clarity(offset);
        if (!sweeping && clarity <= 0) return List.of();

        List<RaidioCue> cues = new ArrayList<>(4);
        if (clarity > 0) {
            double want = RaidioTuning.bpm(distance);
            bpm = Double.isNaN(bpm) ? want : bpm + Math.clamp(want - bpm, -RaidioTuning.MAX_BPM_PER_STEP, RaidioTuning.MAX_BPM_PER_STEP);
            phase += bpm / RaidioTuning.BPM_PER_POSITION_PER_STEP;
            int reached = (int) Math.floor(phase);
            while (lastPosition < reached) {
                lastPosition++;
                if (key < 0 || lastPosition % RaidioMotifs.POSITIONS == 0) key = RaidioTuning.baseNote(distance);
                play(cues, lastPosition, tier, key, clarity);
                if (arrived && lastPosition % 2 == 0) {
                    int[] sparkle = {0, 4, 7, 12};
                    cues.add(new RaidioCue(RaidioVoice.PLING, Math.min(24, 12 + sparkle[(lastPosition / 2) % sparkle.length]), 0.5f));
                }
            }
        }
        if (sweeping && clarity < 1) {
            // A bleep that rises as the player turns towards the raid and falls as they turn away, like turning a dial.
            if (n % 4 == 1) cues.add(new RaidioCue(RaidioVoice.BIT, (int) Math.round(24 * (1 - Math.abs(offset) / 180.0)), (float) (0.20 * (1 - clarity * 0.6))));
            // And now and then a faint tick of noise, nothing more.
            if (clarity < 0.8 && hash(n, 3) % 8 == 0) cues.add(new RaidioCue(RaidioVoice.HAT, 6 + hash(n, 4) % 13, (float) (0.18 * (1 - clarity))));
        }
        return cues;
    }

    /** The notes of one position of the tune. Off the middle of the bearing the signal breaks up: notes drop out in proportion to how far off the player is. */
    private static void play(List<RaidioCue> cues, int position, RaidRarityTier tier, int key, double clarity) {
        boolean heard = hash(position, 1) % 100 < clarity * 100;
        int lead = RaidioMotifs.lead(tier, position);
        if (heard && lead != RaidioMotifs.REST) {
            int note = Math.min(24, key + lead);
            cues.add(new RaidioCue(RaidioVoice.BIT, note, (float) (0.55 + 0.25 * clarity)));
            if (RaidioMotifs.harmony(tier)) cues.add(new RaidioCue(RaidioVoice.PLING, Math.max(0, note - 7), 0.45f));
        }
        if (RaidioMotifs.bass(tier, position) && hash(position, 2) % 100 < clarity * 100) cues.add(new RaidioCue(RaidioVoice.BASS, key, 0.55f));
        if (heard && RaidioMotifs.sparkle(tier, position)) cues.add(new RaidioCue(RaidioVoice.PLING, Math.min(24, key + 12), 0.4f));
    }

    /** A fixed scramble of a step or position number, so dropouts and ticks are uneven but the same every time. */
    private static int hash(int n, int salt) {
        return ((n * 0x9E3779B1 + salt * 0x85EBCA6B) >>> 16) & 0x7fff;
    }
}
