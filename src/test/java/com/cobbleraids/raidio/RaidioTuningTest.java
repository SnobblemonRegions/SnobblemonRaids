package com.cobbleraids.raidio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The maths behind Raidio: which way a raid lies, how well the player is tuned to it, how fast and how high the tune plays. No Minecraft, so every number is tested. */
class RaidioTuningTest {

    @Test
    void targetYawFollowsMinecraftsConvention() {
        // Yaw 0 faces south (+z), 90 faces west (-x), -90 faces east (+x), 180 faces north (-z).
        assertEquals(0, RaidioTuning.targetYaw(0, 10), 1e-9);
        assertEquals(90, RaidioTuning.targetYaw(-10, 0), 1e-9);
        assertEquals(-90, RaidioTuning.targetYaw(10, 0), 1e-9);
        assertEquals(180, Math.abs(RaidioTuning.targetYaw(0, -10)), 1e-9);
    }

    @Test
    void offsetIsTheShortWayRoundAndPositiveMeansTurnClockwise() {
        assertEquals(20, RaidioTuning.offset(170, -170), 1e-9, "170 to -170 is 20 degrees on, across the wrap");
        assertEquals(-20, RaidioTuning.offset(-170, 170), 1e-9);
        assertEquals(30, RaidioTuning.offset(0, 30), 1e-9);
        assertEquals(0, RaidioTuning.offset(45, 45), 1e-9);
        assertTrue(Math.abs(RaidioTuning.offset(0, 540)) <= 180, "any yaw, however wound up, folds into half a turn");
    }

    @Test
    void clarityIsFullInsideTheWindowAndGoneOutsideTheFade() {
        assertEquals(1.0, RaidioTuning.clarity(0), 1e-9);
        assertEquals(1.0, RaidioTuning.clarity(RaidioTuning.ALIGNED_DEGREES), 1e-9);
        assertEquals(1.0, RaidioTuning.clarity(-RaidioTuning.ALIGNED_DEGREES), 1e-9);
        assertEquals(0.0, RaidioTuning.clarity(RaidioTuning.FADE_DEGREES), 1e-9);
        assertEquals(0.0, RaidioTuning.clarity(180), 1e-9);
    }

    @Test
    void clarityFadesSmoothlyAndEvenlyOnBothSides() {
        double mid = (RaidioTuning.ALIGNED_DEGREES + RaidioTuning.FADE_DEGREES) / 2;
        double c = RaidioTuning.clarity(mid);
        assertTrue(c > 0 && c < 1, "halfway through the fade it is neither silent nor clear: " + c);
        assertEquals(c, RaidioTuning.clarity(-mid), 1e-12);
        double previous = 1;
        for (double a = RaidioTuning.ALIGNED_DEGREES; a <= RaidioTuning.FADE_DEGREES; a += 1) {
            double now = RaidioTuning.clarity(a);
            assertTrue(now <= previous + 1e-12, "clarity rose at " + a);
            previous = now;
        }
    }

    @Test
    void notANumberIsNeverTunedIn() {
        assertEquals(0.0, RaidioTuning.clarity(Double.NaN));
        assertEquals(0.0, RaidioTuning.clarity(Double.POSITIVE_INFINITY));
        assertEquals(0, RaidioTuning.baseNote(Double.NaN));
        assertEquals(0, RaidioTuning.baseNote(Double.POSITIVE_INFINITY));
        assertEquals(0.0, RaidioTuning.offset(Double.NaN, 10), 1e-9);
        assertEquals(RaidioTuning.SLOW_BPM, RaidioTuning.bpm(Double.NaN), 1e-9, "no distance is the far, slow end");
    }

    @Test
    void closenessRunsFromZeroFarAwayToOneUpClose() {
        assertEquals(0.0, RaidioTuning.closeness(RaidioTuning.FAR_BLOCKS), 1e-12);
        assertEquals(0.0, RaidioTuning.closeness(RaidioTuning.FAR_BLOCKS * 10), 1e-12);
        assertEquals(1.0, RaidioTuning.closeness(RaidioTuning.NEAR_BLOCKS), 1e-12);
        assertEquals(1.0, RaidioTuning.closeness(0), 1e-12);
        double previous = 1;
        for (double d = 0; d <= RaidioTuning.FAR_BLOCKS * 2; d += 20) {
            double now = RaidioTuning.closeness(d);
            assertTrue(now <= previous + 1e-12, "closeness rose as distance grew, at " + d);
            previous = now;
        }
    }

    @Test
    void theTempoQuickensAsYouCloseInAndStaysSlowAndSteadyFarAway() {
        assertEquals(RaidioTuning.SLOW_BPM, RaidioTuning.bpm(RaidioTuning.FAR_BLOCKS), 1e-9);
        assertEquals(RaidioTuning.FAST_BPM, RaidioTuning.bpm(RaidioTuning.NEAR_BLOCKS), 1e-9);
        assertEquals(RaidioTuning.FAST_BPM, RaidioTuning.bpm(0), 1e-9);
        assertTrue(RaidioTuning.bpm(300) > RaidioTuning.bpm(900), "closer is faster");
        assertTrue(RaidioTuning.bpm(100) > RaidioTuning.bpm(300), "closer is faster");
    }

    @Test
    void theTunesSlowEndIsGenuinelySlow() {
        assertTrue(RaidioTuning.SLOW_BPM <= 60, "far away the tune should be a slow heartbeat, not a busy one");
        assertTrue(RaidioTuning.FAST_BPM > RaidioTuning.SLOW_BPM * 2, "and it should more than double by the time you arrive");
    }

    @Test
    void theTempoNeverOutrunsOnePositionPerStep() {
        // The listener moves the tune on by bpm / 150 of a position a step, so no step may need more than one position.
        assertTrue(RaidioTuning.FAST_BPM / RaidioTuning.BPM_PER_POSITION_PER_STEP <= 1.0, "FAST_BPM would skip positions");
    }

    @Test
    void theNoteRisesAsYouCloseIn() {
        assertEquals(0, RaidioTuning.baseNote(RaidioTuning.FAR_BLOCKS));
        assertEquals(0, RaidioTuning.baseNote(RaidioTuning.FAR_BLOCKS * 10));
        assertEquals(RaidioTuning.NOTE_RANGE, RaidioTuning.baseNote(RaidioTuning.NEAR_BLOCKS));
        assertEquals(RaidioTuning.NOTE_RANGE, RaidioTuning.baseNote(0));
        int previous = RaidioTuning.NOTE_RANGE;
        for (double d = 0; d <= RaidioTuning.FAR_BLOCKS * 2; d += 25) {
            int note = RaidioTuning.baseNote(d);
            assertTrue(note <= previous, "the note rose as distance grew, at " + d);
            previous = note;
        }
    }

    @Test
    void noteStaysInsideWhatTheClientCanPlayEvenWithTheMotifOnTop() {
        // Vanilla note-block pitch is 2^((note - 12) / 12), and the client plays 0.5 to 2.0: notes 0 to 24.
        assertEquals(0.5f, RaidioTuning.pitch(0), 1e-6);
        assertEquals(1.0f, RaidioTuning.pitch(12), 1e-6);
        assertEquals(2.0f, RaidioTuning.pitch(24), 1e-6);
        assertEquals(0.5f, RaidioTuning.pitch(-5), 1e-6, "below the range is clamped, never an invalid pitch");
        assertEquals(2.0f, RaidioTuning.pitch(99), 1e-6);
    }

    @Test
    void thereIsNoSnareAnywhere() {
        for (RaidioVoice voice : RaidioVoice.values()) assertTrue(!voice.name().equals("SNARE"), "the snare was cut: it was far too much");
    }
}
