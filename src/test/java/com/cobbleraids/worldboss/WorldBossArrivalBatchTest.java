package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;

class WorldBossArrivalBatchTest {
    static class Store implements WorldBossArrivalBatch.Store {
        long issued, durable; boolean fail;
        public long checkpoint() throws IOException { if (fail) throw new IOException("disk failed"); return ++issued; }
        public boolean acknowledged(long receipt) { return receipt <= durable; }
    }
    static class Entry implements WorldBossArrivalBatch.Entry {
        int teleports, acknowledgments, completions, cancellations; boolean valid = true, moved = true;
        public boolean eligible() { return valid; }
        public boolean teleport() { teleports++; return moved; }
        public void acknowledge() { acknowledgments++; }
        public void completed() { completions++; }
        public void cancelled() { cancellations++; }
    }
    @Test void noTeleportBeforeOriginDurabilityAndNoSuccessBeforeArrivalDurability() throws Exception {
        var store = new Store(); var entry = new Entry(); var batch = new WorldBossArrivalBatch(store, List.of(entry));
        batch.tick(); assertEquals(0, entry.teleports); assertFalse(batch.done());
        store.durable = 1; batch.tick(); assertEquals(1, entry.teleports); assertEquals(1, entry.acknowledgments);
        assertEquals(0, entry.completions); assertFalse(batch.done());
        store.durable = 2; batch.tick(); assertTrue(batch.done()); assertEquals(1, entry.completions);
        batch.tick(); assertEquals(1, entry.teleports); assertEquals(1, entry.completions);
    }
    @Test void eachPlayerIsRevalidatedAfterTheOriginSaveAndNativeMovesStayBounded() throws Exception {
        var store = new Store(); var entries = new ArrayList<Entry>(); for (int n=0;n<8;n++) entries.add(new Entry());
        var batch = new WorldBossArrivalBatch(store, entries); entries.getFirst().valid=false; store.durable=1;
        batch.tick(); assertEquals(1, entries.getFirst().cancellations); assertEquals(1, entries.stream().mapToInt(e->e.teleports).sum());
        batch.tick(); assertEquals(3, entries.stream().mapToInt(e->e.teleports).sum());
        batch.tick(); batch.tick(); assertEquals(7, entries.stream().mapToInt(e->e.teleports).sum());
        assertEquals(0, entries.stream().mapToInt(e->e.completions).sum());
        store.durable=2; batch.tick(); assertEquals(7, entries.stream().mapToInt(e->e.completions).sum());
    }
    @Test void failedArrivalSaveCannotRepeatTeleportOrAnnounceSuccess() throws Exception {
        var store = new Store(); var entry = new Entry(); var batch = new WorldBossArrivalBatch(store, List.of(entry));
        store.durable=1; store.fail=true; assertThrows(IOException.class,batch::tick);
        batch.tick(); assertEquals(1,entry.teleports); assertEquals(0,entry.completions);
    }
    @Test void refusedNativeMoveDoesNotReceiveAnArrivalAcknowledgment() throws Exception {
        var store = new Store(); var entry = new Entry(); entry.moved=false;
        var batch = new WorldBossArrivalBatch(store,List.of(entry)); store.durable=1; batch.tick();
        assertEquals(1,entry.cancellations); assertEquals(0,entry.acknowledgments); assertEquals(1,store.issued); assertTrue(batch.done());
    }
}
