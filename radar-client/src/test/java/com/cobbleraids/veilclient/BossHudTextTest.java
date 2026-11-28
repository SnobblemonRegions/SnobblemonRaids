package com.cobbleraids.veilclient;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BossHudTextTest {
    @Test void compactLabelsPreserveTheOpeningTimeReadyCountAndCombatFacts() {
        assertEquals("Opens in 9:53 · 12 ready",BossHudText.status("GATHERING  /  OPENS IN 9:53  /  12 READY"));
        assertEquals("3 fighting · 14m",BossHudText.status("3 FIGHTING  /  14m REMAINING"));
        assertEquals("Staff hold",BossHudText.status("STAFF HOLD"));
        assertEquals("Future seasonal status",BossHudText.status("Future seasonal status"));
    }
    @Test void stackedPreparationPreservesBothTimeAndReadyCount() {
        assertArrayEquals(new String[]{"Opens in 9:55","50 ready"},BossHudText.stack("Opens in 9:55 · 50 ready"));
        assertArrayEquals(new String[]{"Staff hold"},BossHudText.stack("Staff hold"));
    }
}
