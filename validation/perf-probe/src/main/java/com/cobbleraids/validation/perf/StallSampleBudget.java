package com.cobbleraids.validation.perf;

/** Bounds diagnostic stack walks without changing the complete tick measurements. */
final class StallSampleBudget {
    private long tick = Long.MIN_VALUE;
    private long last;
    private int samples;

    boolean allow(long tickId, long now) {
        if (tick != tickId) { tick = tickId; samples = 0; }
        if (samples >= 3 || samples > 0 && now - last < 200_000_000L) return false;
        samples++; last = now; return true;
    }
}
