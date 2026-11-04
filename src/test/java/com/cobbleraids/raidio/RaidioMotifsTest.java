package com.cobbleraids.raidio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbleraids.config.RaidRarityTier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Each tier's one-bar tune, in sixteenth-note positions: what makes a Starter sound different from a Legendary. */
class RaidioMotifsTest {

    private static List<Integer> notes(RaidRarityTier tier) {
        List<Integer> out = new ArrayList<>();
        for (int p = 0; p < RaidioMotifs.POSITIONS; p++) if (RaidioMotifs.lead(tier, p) != RaidioMotifs.REST) out.add(RaidioMotifs.lead(tier, p));
        return out;
    }

    @Test
    void aBarIsSixteenPositions() {
        assertEquals(16, RaidioMotifs.POSITIONS);
    }

    @Test
    void everyTierHasATuneWithRestsInIt() {
        for (RaidRarityTier tier : RaidRarityTier.values()) {
            int notes = notes(tier).size();
            assertTrue(notes >= 4, tier + " needs a tune, got " + notes + " notes");
            assertTrue(notes <= 12, tier + " should leave room to breathe, got " + notes + " notes in a bar");
        }
    }

    @Test
    void everyTierBeginsOnTheNoteTheDistanceGives() {
        for (RaidRarityTier tier : RaidRarityTier.values()) assertEquals(0, RaidioMotifs.lead(tier, 0), tier + " starts on the home note");
    }

    @Test
    void noNoteGoesHigherThanTheOctaveSoTheTuneAlwaysFitsOnTopOfTheDistancesNote() {
        for (RaidRarityTier tier : RaidRarityTier.values()) {
            for (int note : notes(tier)) assertTrue(note >= 0 && note <= RaidioTuning.NOTE_RANGE, tier + " has a note at " + note);
        }
        // base note up to NOTE_RANGE, plus up to NOTE_RANGE: the client's 0 to 24
        assertTrue(RaidioTuning.NOTE_RANGE * 2 <= 24);
    }

    @Test
    void theFourTiersSoundDifferent() {
        for (RaidRarityTier a : RaidRarityTier.values()) {
            for (RaidRarityTier b : RaidRarityTier.values()) {
                if (a.ordinal() >= b.ordinal()) continue;
                boolean same = true;
                for (int p = 0; p < RaidioMotifs.POSITIONS; p++) same &= RaidioMotifs.lead(a, p) == RaidioMotifs.lead(b, p);
                assertFalse(same, a + " and " + b + " have the same tune");
            }
        }
    }

    @Test
    void aTierWithNoTierPlaysTheStarterTune() {
        for (int p = 0; p < RaidioMotifs.POSITIONS; p++) assertEquals(RaidioMotifs.lead(RaidRarityTier.STARTER, p), RaidioMotifs.lead(null, p));
    }

    @Test
    void positionsWrapAroundTheBar() {
        for (int p = 0; p < RaidioMotifs.POSITIONS; p++) {
            assertEquals(RaidioMotifs.lead(RaidRarityTier.POWERHOUSE, p), RaidioMotifs.lead(RaidRarityTier.POWERHOUSE, p + RaidioMotifs.POSITIONS));
            assertEquals(RaidioMotifs.lead(RaidRarityTier.POWERHOUSE, p), RaidioMotifs.lead(RaidRarityTier.POWERHOUSE, p + 5 * RaidioMotifs.POSITIONS));
        }
        assertEquals(RaidioMotifs.lead(RaidRarityTier.STARTER, 0), RaidioMotifs.lead(RaidRarityTier.STARTER, 0 - RaidioMotifs.POSITIONS), "negative positions are folded in too");
    }

    @Test
    void theStarterTuneIsThePlayfulUpAndDownOne() {
        assertEquals(List.of(0, 4, 7, 4, 12, 7, 4, 0), notes(RaidRarityTier.STARTER));
    }

    @Test
    void theBassAnchorsTheBarOnTheBeatsAndOnlyThere() {
        assertTrue(RaidioMotifs.bass(RaidRarityTier.STARTER, 0));
        assertTrue(RaidioMotifs.bass(RaidRarityTier.STARTER, 8));
        assertFalse(RaidioMotifs.bass(RaidRarityTier.STARTER, 3));
        assertTrue(RaidioMotifs.bass(RaidRarityTier.POWERHOUSE, 4), "the Powerhouse pulses on every beat");
        assertFalse(RaidioMotifs.bass(RaidRarityTier.MYTHICAL, 0), "the Mythical tune floats with no bass");
    }

    @Test
    void onlyTheLegendaryTuneIsHarmonised() {
        for (RaidRarityTier tier : RaidRarityTier.values()) assertEquals(tier == RaidRarityTier.LEGENDARY, RaidioMotifs.harmony(tier), tier.name());
    }

    @Test
    void onlyTheMythicalTuneSparklesAndOnlyTwiceABar() {
        int sparkles = 0;
        for (RaidRarityTier tier : RaidRarityTier.values()) {
            for (int p = 0; p < RaidioMotifs.POSITIONS; p++) {
                if (RaidioMotifs.sparkle(tier, p)) {
                    assertEquals(RaidRarityTier.MYTHICAL, tier);
                    sparkles++;
                }
            }
        }
        assertEquals(2, sparkles);
        assertNotEquals(RaidioMotifs.REST, 0, "a rest is not a note");
    }
}
