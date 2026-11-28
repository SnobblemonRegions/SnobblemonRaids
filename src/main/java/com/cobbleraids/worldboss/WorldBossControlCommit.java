package com.cobbleraids.worldboss;

import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Server-thread continuation: accepted control work is not published before its forced receipt. */
final class WorldBossControlCommit {
    interface Store {
        boolean available();
        long checkpoint() throws IOException;
        boolean acknowledged(long receipt);
    }
    private final Store store;
    private final BooleanSupplier valid;
    private final Runnable publish, cancelled;
    private final Consumer<Exception> failed;
    private final Thread owner=Thread.currentThread();
    private long receipt;
    private boolean done;
    WorldBossControlCommit(Store store,BooleanSupplier valid,Runnable publish,Runnable cancelled,Consumer<Exception> failed) {
        this.store=store;this.valid=valid;this.publish=publish;this.cancelled=cancelled;this.failed=failed;
    }
    boolean done() { return done; }
    void cancel() { assertOwner(); done=true; }
    void tick() {
        assertOwner();
        if(done) return;
        try {
            if(!valid.getAsBoolean()) { done=true;cancelled.run();return; }
            if(receipt==0) {
                if(!store.available()) return;
                receipt=store.checkpoint();
                if(receipt<=0) throw new IllegalStateException("Control checkpoint did not issue a receipt");
                return;
            }
            if(!store.acknowledged(receipt)) return;
            done=true; // A callback that throws or re-enters cannot be replayed.
            publish.run();
        } catch(IOException|RuntimeException failure) { done=true;failed.accept(failure); }
    }
    private void assertOwner() {
        if(Thread.currentThread()!=owner) throw new IllegalStateException("Control continuation requires its owning server thread");
    }
}
