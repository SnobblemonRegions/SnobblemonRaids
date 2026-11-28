package com.cobbleraids.veilclient;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RiftCrownLayoutTest {
    @Test void normalAndMinimumGuiWidthsKeepTheArtworkAndTrackInsideTheViewport() {
        var normal=RiftCrownLayout.at(640);
        assertEquals(140,normal.x());assertEquals(360,normal.width());assertEquals(320,normal.center());
        for(int width:new int[]{320,426,640,1280}) {
            var p=RiftCrownLayout.at(width);
            assertTrue(p.x()>=8);assertTrue(p.x()+p.width()<=width-8);
            assertTrue(p.railX()>=p.x());assertTrue(p.railX()+p.railWidth()<=p.x()+p.width());
            assertTrue(p.railY()>p.titleY());assertTrue(p.infoY()>=p.railY()+p.railHeight());
        }
    }
    @Test void crowdedCaptionsStackAndReserveRoomBeforeOtherBossBars() {
        var normal=RiftCrownLayout.at(640);
        assertEquals(1,normal.rows(65,95,false));
        var small=RiftCrownLayout.at(320);
        assertEquals(2,small.rows(85,110,false));
        assertEquals(2,small.rows(999,120,true),"narrow preparation splits its time and readiness facts, without displaying health");
        assertTrue(small.reserve(2)>=small.infoY()+20+3);
        assertThrows(IllegalArgumentException.class,()->RiftCrownLayout.at(20));
    }
    @Test void smallViewportProtectsTheNativePartyPanel() {
        var p=RiftCrownLayout.at(320);
        assertTrue(p.x()>=64,"wing tips need the same side gutter as the native party panel");
        assertTrue(p.x()+p.width()<=320-64);
        assertEquals(360,RiftCrownLayout.at(640).width(),"normal-size art remains unchanged");
    }
}
