package com.cobbleraids.veilclient;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class VeilTimelineTest {
 @Test void nextBossRevealsAgainWithoutAcceptingOldSequence() {
  var clock = new VeilTimeline(); clock.accept(10, 0, true);
  assertEquals(1, clock.opacity(2000));
  assertTrue(clock.accept(11, 2100, true, true));
  assertEquals(0, clock.opacity(2100));
  assertFalse(clock.accept(10, 2200, true, true));
  assertEquals(1, clock.opacity(4100));
 }

 @Test void expiresEvenWhenWorldTimeStopsAndOldUpdatesCannotResurrectScene() {
  var clock = new VeilTimeline();
  assertTrue(clock.accept(10, 100, true));
  assertEquals(0, clock.opacity(100));
  assertEquals(1, clock.opacity(2100));
  assertFalse(clock.accept(9, 2200, true));
  assertEquals(0, clock.opacity(6100));
  assertTrue(clock.accept(11, 6200, false));
  assertFalse(clock.accept(10, 6300, true));
  assertEquals(0, clock.opacity(6400));
 }
 @Test void heartbeatDoesNotRestartEntranceAndClearAllowsNewServerSequence() {
  var clock = new VeilTimeline(); clock.accept(100, 0, true);
  clock.accept(101, 1000, true); assertEquals(1, clock.opacity(2000));
  clock.clear(); assertTrue(clock.accept(1, 3000, true));
  assertEquals(0, clock.opacity(3000));
 }
}
