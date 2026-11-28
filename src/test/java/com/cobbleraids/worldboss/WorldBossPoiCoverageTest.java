package com.cobbleraids.worldboss;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldBossPoiCoverageTest {
    @Test void coverageContainsNativeOuterChunkRingAcrossNegativeAndBoundaryCoordinates(){
        int diameter=8;float distance=64;int radius=WorldBossPoiReadAhead.radius(distance,diameter);
        assertTrue(radius>0);
        int nativeRing=(32+(int)Math.ceil(Math.hypot(diameter,diameter)))/16+1;
        for(int player:new int[]{-1000,-208,-17,-16,-1,0,15,16,200,208})for(int degrees=0;degrees<360;degrees+=5){
            double center=Math.floor(player+Math.cos(Math.toRadians(degrees))*distance-diameter/2.0)+diameter/2.0;
            int centerChunk=(int)Math.floor(center/16);
            int first=(int)Math.floorDiv((long)player-radius,16),last=(int)Math.floorDiv((long)player+radius,16);
            assertTrue(first<=centerChunk-nativeRing,"Native first scan column escaped prefetch bounds");
            assertTrue(last>=centerChunk+nativeRing,"Native last scan column escaped prefetch bounds");
        }
    }
    @Test void unsupportedOrInvalidConfigurationsRetainNativeFallbackRatherThanClaimPartialCoverage(){
        assertEquals(-1,WorldBossPoiReadAhead.radius(Float.NaN,8));assertEquals(-1,WorldBossPoiReadAhead.radius(Float.POSITIVE_INFINITY,8));
        assertEquals(-1,WorldBossPoiReadAhead.radius(-1,8));assertEquals(-1,WorldBossPoiReadAhead.radius(64,0));
        assertEquals(-1,WorldBossPoiReadAhead.radius(1000,8));assertEquals(-1,WorldBossPoiReadAhead.radius(64,16));
    }
}
