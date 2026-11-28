package com.cobbleraids.worldboss;

import java.io.IOException;
import java.util.List;

/** Main-thread travel continuations; the store owns durable I/O, never these callbacks. */
final class WorldBossArrivalBatch {
    interface Store { long checkpoint() throws IOException; boolean acknowledged(long receipt); }
    interface Entry {
        boolean eligible();
        boolean teleport();
        void acknowledge();
        void completed();
        void cancelled();
    }
    private final Store store;
    private final List<Entry> entries;
    private final java.util.ArrayList<Entry> moved = new java.util.ArrayList<>();
    private long receipt;
    private int cursor;
    private int stage;
    WorldBossArrivalBatch(Store store, List<? extends Entry> entries) throws IOException {
        if (entries.isEmpty() || entries.size() > 8) throw new IllegalArgumentException("Invalid arrival batch");
        this.store = store; this.entries = List.copyOf(entries); receipt = store.checkpoint();
    }
    boolean waiting() { return (stage == 0 || stage == 2) && !store.acknowledged(receipt); }
    boolean done() { return stage == 3; }
    void tick() throws IOException {
        if (stage == 3 || stage == 4) return;
        try {
            if (stage == 0) { if (!store.acknowledged(receipt)) return; stage = 1; }
            if (stage == 1) {
                for (int budget = 0; cursor < entries.size() && budget < 2; budget++) {
                    Entry entry = entries.get(cursor++);
                    if (entry.eligible() && entry.teleport()) { entry.acknowledge(); moved.add(entry); }
                    else entry.cancelled();
                }
                if (cursor < entries.size()) return;
                if (moved.isEmpty()) { stage = 3; return; }
                receipt = store.checkpoint(); stage = 2;
            }
            if (stage == 2 && store.acknowledged(receipt)) { stage = 3; for (Entry entry : moved) entry.completed(); }
        } catch (IOException | RuntimeException failure) { stage = 4; throw failure; }
    }
}
