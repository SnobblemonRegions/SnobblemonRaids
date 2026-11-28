package com.cobbleraids.veilclient;

/** Monotonic-time watchdog; paused worlds cannot retain a disconnected visual forever. */
public final class VeilTimeline {
    private long sequence = -1, opened, updated;
    private boolean active;
    public boolean accept(long next, long millis, boolean visible) {
        return accept(next, millis, visible, false);
    }
    public boolean accept(long next, long millis, boolean visible, boolean reveal) {
        if (next <= sequence) return false;
        sequence = next;
        if (visible && (reveal || !active || millis - updated >= 6000)) opened = millis;
        updated = millis;
        active = visible;
        return true;
    }
    public double opacity(long millis) {
        if (!active) return 0;
        double entrance = Math.clamp((millis - opened) / 1800.0, 0, 1);
        double expiry = Math.clamp((6000 - (millis - updated)) / 1000.0, 0, 1);
        return entrance * entrance * (3 - 2 * entrance) * expiry;
    }
    public void clear() { sequence = -1; active = false; }
}
