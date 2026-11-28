package com.cobbleraids.veilclient;

import java.util.UUID;

/** Presentation only: the bright health edge never waits for the damage echo. */
final class BossHudMotion {
    record Sample(double health, double echo) {}
    private UUID owner;
    private double health, echo;
    private long at, holdUntil;
    Sample update(UUID nextOwner, double value, long now, boolean animated) {
        double next = Double.isFinite(value) ? Math.clamp(value, 0, 1) : 0;
        if (!nextOwner.equals(owner) || now < at || now - at > 2000 || !animated) {
            health = echo = next; holdUntil = now;
        } else {
            long decayFrom = Math.max(at, holdUntil);
            if (now > decayFrom) echo = health + (echo - health) * Math.exp(-(now - decayFrom) / 240.0);
            if (next < health - .00001) holdUntil = now + 250;
            echo = Math.max(next, echo);
            health = next;
        }
        owner = nextOwner; at = now;
        return new Sample(health, echo);
    }
    void clear() { owner = null; at = holdUntil = 0; health = echo = 0; }
}
