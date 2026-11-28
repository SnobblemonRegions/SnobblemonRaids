package com.cobbleraids.veilclient;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossHudMotionTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);
    @Test void damageIsImmediateWhileTheEchoHoldsThenCatchesUp() {
        var motion = new BossHudMotion();
        motion.update(A, 1, 0, true);
        var hit = motion.update(A, .6, 100, true);
        assertEquals(.6, hit.health()); assertEquals(1, hit.echo());
        assertEquals(1, motion.update(A, .6, 300, true).echo());
        double echo = motion.update(A, .6, 900, true).echo();
        assertTrue(echo > .6 && echo < .7);
    }
    @Test void healingNewOwnerAndReducedMotionNeverInheritDamage() {
        var motion = new BossHudMotion(); motion.update(A,1,0,true);
        motion.update(A,.2,100,true);
        assertEquals(.9,motion.update(A,.9,200,true).health());
        assertEquals(.1,motion.update(B,.1,210,true).echo());
        assertEquals(0,motion.update(B,-1,220,false).echo());
        assertEquals(1,motion.update(B,2,230,false).health());
        assertEquals(0,motion.update(B,Double.NaN,240,false).health());
    }
    @Test void echoDecayIsConsistentAcrossFrameRatesAndResetsOnClockRewind() {
        double[] result = new double[2]; int n=0;
        for(int fps:new int[]{30,144}) {
            var motion=new BossHudMotion(); motion.update(A,1,0,true); motion.update(A,.4,100,true);
            for(int i=1;i<=fps;i++)motion.update(A,.4,350+Math.round(i*1000.0/fps),true);
            result[n++]=motion.update(A,.4,1350,true).echo();
            assertEquals(.2,motion.update(A,.2,0,true).echo());
        }
        assertEquals(result[0],result[1],1e-9);
    }
}
