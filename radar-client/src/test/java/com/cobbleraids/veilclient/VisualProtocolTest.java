package com.cobbleraids.veilclient;
import com.cobbleraids.visual.protocol.VisualProtocol;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class VisualProtocolTest {
 @Test void coreAndCompanionCanInitializeTogetherWithoutDuplicateRegistration() {
  var count = new AtomicInteger();
  VisualProtocol.once("test-shared-registration", count::incrementAndGet);
  VisualProtocol.once("test-shared-registration", () -> fail("duplicate registration"));
  assertEquals(1, count.get());
 }
 @Test void failedRegistrationCanRetry() {
  assertThrows(IllegalStateException.class, () -> VisualProtocol.once("test-retry", () -> {throw new IllegalStateException();}));
  var count = new AtomicInteger(); VisualProtocol.once("test-retry", count::incrementAndGet);
  assertEquals(1, count.get());
 }
}
