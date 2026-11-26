package com.cobbleraids.showdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The `>raidpool <current> <max>` line raid-patch.js mirrors the boss's simulated HP from. */
class RaidPoolCommandTest {

    @Test
    @DisplayName("current and max, plain decimal")
    void formats() {
        assertEquals(">raidpool 1234.5 4000.0", RaidPoolCommand.of(1234.5f, 4000f));
    }

    @Test
    @DisplayName("current is clamped into [0, max]")
    void clamps() {
        assertEquals(">raidpool 0.0 100.0", RaidPoolCommand.of(-5f, 100f));
        assertEquals(">raidpool 100.0 100.0", RaidPoolCommand.of(250f, 100f));
    }

    @Test
    @DisplayName("nothing is sent for a pool that cannot be mirrored")
    void refusesNonsense() {
        assertNull(RaidPoolCommand.of(10f, 0f));
        assertNull(RaidPoolCommand.of(Float.NaN, 100f));
        assertNull(RaidPoolCommand.of(10f, Float.POSITIVE_INFINITY));
    }

    @Test
    @DisplayName("a comma-decimal default locale cannot leak into the protocol")
    void localeIndependent() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals(">raidpool 0.5 2.0", RaidPoolCommand.of(0.5f, 2f));
        } finally {
            Locale.setDefault(before);
        }
    }
}
