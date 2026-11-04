package com.cobbleraids.raidio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbleraids.config.RaidRarityTier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What the player hears, a step (two game ticks, a tenth of a second) at a time. */
class RaidioListenerTest {

    /** Cues at or above this volume are the tune's lead; the tuning bleeps are quieter than any lead note. */
    private static final float LEAD_FLOOR = 0.5f;

    private static List<RaidioCue> step(RaidioListener listener, double yaw, double distance, RaidRarityTier tier) {
        return listener.step(true, yaw, 0, distance, tier);   // the raid is due south, so a tuned-in player faces yaw 0
    }

    private static boolean isLead(RaidioCue cue) {
        return cue.voice() == RaidioVoice.BIT && cue.volume() >= LEAD_FLOOR;
    }

    private static List<Integer> leadNotes(RaidioListener listener, int steps, double yaw, double distance, RaidRarityTier tier) {
        List<Integer> notes = new ArrayList<>();
        for (int i = 0; i < steps; i++) for (RaidioCue cue : step(listener, yaw, distance, tier)) if (isLead(cue)) notes.add(cue.note());
        return notes;
    }

    @Test
    void withNoRaidThereIsNeverASound() {
        RaidioListener listener = new RaidioListener();
        for (int i = 0; i < 60; i++) assertTrue(listener.step(false, i * 7.0, 0, 300, RaidRarityTier.STARTER).isEmpty());
    }

    @Test
    void standingStillFacingAwayIsSilent() {
        RaidioListener listener = new RaidioListener();
        for (int i = 0; i < 100; i++) assertTrue(step(listener, 180, 300, RaidRarityTier.MYTHICAL).isEmpty(), "step " + i);
    }

    @Test
    void sweepingAwayPlaysOnlyTuningBleepsAndAFaintTickNeverTheTune() {
        RaidioListener listener = new RaidioListener();
        step(listener, 180, 300, RaidRarityTier.LEGENDARY);
        int hats = 0, steps = 0;
        for (int i = 1; i <= 40; i++) {
            steps++;
            for (RaidioCue cue : step(listener, 180 + i * 3.0, 300, RaidRarityTier.LEGENDARY)) {   // never within 45 degrees of yaw 0
                assertFalse(isLead(cue), "no tune while facing away");
                assertTrue(cue.voice() == RaidioVoice.BIT || cue.voice() == RaidioVoice.HAT, "only bleeps and ticks, heard " + cue.voice());
                if (cue.voice() == RaidioVoice.HAT) hats++;
            }
        }
        assertTrue(hats <= steps / 4, "the noise tick must stay sparse: " + hats + " in " + steps + " steps");
    }

    @Test
    void theTuningBleepsRiseAsYouTurnTowardsTheRaid() {
        RaidioListener listener = new RaidioListener();
        step(listener, 180, 300, RaidRarityTier.STARTER);
        List<Integer> bleeps = new ArrayList<>();
        for (int i = 1; i <= 36; i++) {
            for (RaidioCue cue : step(listener, 180 + i * 3.0, 300, RaidRarityTier.STARTER)) if (cue.voice() == RaidioVoice.BIT) bleeps.add(cue.note());
        }
        // The player turns from facing away (offset 180) round to 288 degrees = 72 degrees from the raid: nearer all the time.
        assertTrue(bleeps.size() >= 5, "a sweep needs bleeps, heard " + bleeps.size());
        for (int i = 1; i < bleeps.size(); i++) assertTrue(bleeps.get(i) >= bleeps.get(i - 1), "the bleeps fell while turning towards the raid: " + bleeps);
        assertTrue(bleeps.get(bleeps.size() - 1) > bleeps.get(0));
    }

    @Test
    void facingTheRaidPlaysTheTiersTuneInOrderTransposedByTheDistance() {
        for (RaidRarityTier tier : RaidRarityTier.values()) {
            List<Integer> motif = new ArrayList<>();
            for (int p = 0; p < RaidioMotifs.POSITIONS; p++) if (RaidioMotifs.lead(tier, p) != RaidioMotifs.REST) motif.add(RaidioMotifs.lead(tier, p));
            int base = RaidioTuning.baseNote(300);
            List<Integer> heard = leadNotes(new RaidioListener(), 400, 0, 300, tier);
            assertTrue(heard.size() >= motif.size() * 2, tier + " should play at least two bars in 40 seconds, heard " + heard.size() + " notes");
            for (int i = 0; i < heard.size(); i++) assertEquals(base + motif.get(i % motif.size()), heard.get(i), tier + " note " + i);
        }
    }

    @Test
    void theTuneIsSlowFarAwayAndQuickensAsYouCloseIn() {
        int far = leadNotes(new RaidioListener(), 300, 0, 1400, RaidRarityTier.STARTER).size();
        int mid = leadNotes(new RaidioListener(), 300, 0, 300, RaidRarityTier.STARTER).size();
        int near = leadNotes(new RaidioListener(), 300, 0, 50, RaidRarityTier.STARTER).size();
        assertTrue(far < mid && mid < near, "notes in thirty seconds: far " + far + ", mid " + mid + ", near " + near);
        assertTrue(near >= far * 2, "close should be at least twice as busy as far: " + near + " vs " + far);
        // Far away is a slow heartbeat: no more than about two notes a second.
        assertTrue(far <= 300 / 10 * 2, "far away should be slow, heard " + far + " notes in thirty seconds");
    }

    @Test
    void aSuddenJumpInDistanceEasesTheTempoInInsteadOfCutting() {
        RaidioListener listener = new RaidioListener();
        leadNotes(listener, 200, 0, 1400, RaidRarityTier.STARTER);   // settled at the slow tempo
        int justAfter = leadNotes(listener, 20, 0, 60, RaidRarityTier.STARTER).size();    // a nearer raid is picked
        leadNotes(listener, 100, 0, 60, RaidRarityTier.STARTER);                           // the tempo catches up
        int settled = leadNotes(listener, 20, 0, 60, RaidRarityTier.STARTER).size();
        assertTrue(justAfter < settled, "right after the jump it should still be on its way up: " + justAfter + " then " + settled);
        assertTrue(justAfter >= 1, "but it should keep playing, not stop");
    }

    @Test
    void theTuneNeverChangesKeyInTheMiddleOfAPhrase() {
        RaidioListener listener = new RaidioListener();
        List<Integer> notes = new ArrayList<>();
        // Walk in from far to near in one go, so the distance's note climbs through the whole range while the tune plays.
        int steps = 600;
        for (int i = 0; i < steps; i++) {
            double distance = 1400 * Math.pow(20.0 / 1400.0, i / (double) (steps - 1));
            for (RaidioCue cue : step(listener, 0, distance, RaidRarityTier.STARTER)) if (isLead(cue)) notes.add(cue.note());
        }
        int[] motif = {0, 4, 7, 4, 12, 7, 4, 0};   // the Starter tune's notes in a bar
        int keys = 0, previousKey = -1;
        for (int bar = 0; (bar + 1) * motif.length <= notes.size(); bar++) {
            int key = notes.get(bar * motif.length) - motif[0];
            for (int i = 0; i < motif.length; i++) {
                assertEquals(key + motif[i], notes.get(bar * motif.length + i), "the key moved inside bar " + bar + " at note " + i);
            }
            if (key != previousKey) keys++;
            previousKey = key;
        }
        assertTrue(keys >= 3, "the tune should still climb as you close in, but it only changed key " + keys + " times");
    }

    @Test
    void arrivingAddsASparkleAndTheTuneCarriesOn() {
        RaidioListener listener = new RaidioListener();
        boolean lead = false, sparkle = false;
        for (int i = 0; i < 100; i++) {
            for (RaidioCue cue : listener.step(true, 180, 0, RaidioTuning.ARRIVED_BLOCKS - 8, RaidRarityTier.STARTER)) {   // facing away, 4 blocks off
                lead |= isLead(cue);
                sparkle |= cue.voice() == RaidioVoice.PLING;
            }
        }
        assertTrue(lead, "the tune must not stop when you arrive");
        assertTrue(sparkle, "arriving should sparkle");
    }

    @Test
    void theTuneBreaksUpAtTheEdgeOfTheWindowAndStillPlays() {
        double edge = (RaidioTuning.ALIGNED_DEGREES + RaidioTuning.FADE_DEGREES) / 2;
        int clear = leadNotes(new RaidioListener(), 400, 0, 300, RaidRarityTier.STARTER).size();
        int broken = leadNotes(new RaidioListener(), 400, edge, 300, RaidRarityTier.STARTER).size();
        assertTrue(broken > 0, "still audible at the edge");
        assertTrue(broken < clear, "but with notes missing: " + broken + " of " + clear);
    }

    @Test
    void silenceReturnsOnceTheSweepStopsAwayFromTheRaid() {
        RaidioListener listener = new RaidioListener();
        step(listener, 180, 300, RaidRarityTier.STARTER);
        for (int i = 1; i <= 10; i++) step(listener, 180 + i * 4.0, 300, RaidRarityTier.STARTER);   // sweep, then stop at 220
        for (int i = 0; i < RaidioSweep.HOLD_STEPS + 2; i++) step(listener, 220, 300, RaidRarityTier.STARTER);
        for (int i = 0; i < 30; i++) assertTrue(step(listener, 220, 300, RaidRarityTier.STARTER).isEmpty(), "silent again, step " + i);
    }

    @Test
    void everyCueIsSafeToSend() {
        RaidioListener listener = new RaidioListener();
        for (RaidRarityTier tier : RaidRarityTier.values()) {
            for (int i = 0; i < 400; i++) {
                for (RaidioCue cue : listener.step(true, (i * 13) % 360 - 180, (i % 7) * 40.0 - 120, (i % 11) * 90.0 - 200, tier)) {
                    assertTrue(cue.volume() > 0 && cue.volume() <= 1, "volume " + cue.volume());
                    assertTrue(cue.note() >= 0 && cue.note() <= 24, "note " + cue.note() + " for " + cue.voice());
                }
            }
        }
    }

    @Test
    void sameInputsAlwaysGiveTheSameSounds() {
        RaidioListener a = new RaidioListener(), b = new RaidioListener();
        for (int i = 0; i < 200; i++) assertEquals(step(a, i * 5.0 % 360, 300, RaidRarityTier.MYTHICAL), step(b, i * 5.0 % 360, 300, RaidRarityTier.MYTHICAL));
    }

    @Test
    void aRaidExactlyUnderThePlayerHasNoDirectionAndDoesNotBreak() {
        RaidioListener listener = new RaidioListener();
        for (int i = 0; i < 40; i++) {
            for (RaidioCue cue : listener.step(true, i * 20.0, 0, 0, RaidRarityTier.STARTER)) assertTrue(cue.note() >= 0 && cue.note() <= 24);
        }
    }

    @Test
    void notANumberFromTheWorldIsSilentNotAnException() {
        RaidioListener listener = new RaidioListener();
        assertTrue(listener.step(true, 0, Double.NaN, 300, RaidRarityTier.STARTER).isEmpty());
        assertTrue(listener.step(true, 0, 0, Double.POSITIVE_INFINITY, RaidRarityTier.STARTER).isEmpty());
    }
}
