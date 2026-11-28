package com.cobbleraids.veilclient;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BossHudFlairTest {
    @Test void animationRespectsPreferencesAndWrapsWithinTheAtlas() {
        assertEquals(-1,BossHudFlair.row(150,0,false));
        assertEquals(-1,BossHudFlair.row(150,1,true));
        assertEquals(0,BossHudFlair.row(0,2,false));
        assertEquals(0,BossHudFlair.row(149,2,false));
        assertEquals(1,BossHudFlair.row(150,2,false));
        assertEquals(7,BossHudFlair.row(1199,2,false));
        assertEquals(0,BossHudFlair.row(1200,2,false));
        assertEquals(9,BossHudFlair.row(150,2,true));
        assertTrue(BossHudFlair.row(Long.MAX_VALUE,2,true)<16);
        assertTrue(BossHudFlair.row(-1,2,false)>=0);
    }
}
