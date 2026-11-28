package com.cobbleraids.worldboss;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class WorldBossVisibilityTest {
 @Test void arenaShowpieceRemainsTrackedAtTheFarEdge() {
  assertEquals(128, WorldBossVisibility.trackingRange(64, 96));
  assertEquals(512, WorldBossVisibility.trackingRange(512, 96));
  assertEquals(1056, WorldBossVisibility.trackingRange(64, 1024));
 }
 @Test void invalidRadiusCannotPoisonNativeTracking() {
  assertEquals(64, WorldBossVisibility.trackingRange(64, Double.NaN));
  assertEquals(64, WorldBossVisibility.trackingRange(64, -1));
  assertEquals(0, WorldBossVisibility.trackingRange(0, 96));
 }
}
