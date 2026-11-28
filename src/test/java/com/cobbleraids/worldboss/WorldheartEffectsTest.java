package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WorldheartEffectsTest {
    @Test void authoredFramesAreFiniteBoundedAndRespectReducedEffects() {
        for (int style = 0; style <= 2; style++) for (int age = 0; age <= 200; age++) for (double scale : new double[]{0.1, 1, 100}) {
            var full = WorldheartEffects.frame(age, style, scale, scale, false); var minimal = WorldheartEffects.frame(age, style, scale, scale, true);
            assertTrue(full.size() <= 12); assertTrue(minimal.size() <= 4);
            for (var mote : full) { assertTrue(Double.isFinite(mote.x()) && Double.isFinite(mote.y()) && Double.isFinite(mote.z())); assertTrue(Math.abs(mote.x()) <= 3.6 && Math.abs(mote.z()) <= 3.6); assertTrue(mote.y() >= 0 && mote.y() <= 3.2); }
            if (age >= 40 && age % 10 != 0) assertTrue(full.isEmpty());
        }
    }
    @Test void onlyAuthoredSpeciesAreAvailableAndInvalidInputsCannotCreateBadParticles() {
        assertTrue(WorldheartEffects.supported("cobblemon:charizard", "distortion_v1")); assertFalse(WorldheartEffects.supported("cobblemon:flareon", "distortion_v1"));
        assertFalse(WorldheartEffects.supported("cobblemon:eevee", "missing_art"));
        assertThrows(IllegalArgumentException.class, () -> WorldheartEffects.frame(0, 0, Double.NaN, 1, false));
        assertThrows(IllegalArgumentException.class, () -> WorldheartEffects.frame(0, 3, 1, 1, false));
    }
}
