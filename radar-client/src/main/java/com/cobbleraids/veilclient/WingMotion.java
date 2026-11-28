package com.cobbleraids.veilclient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.cobbleraids.veilclient.RibbonStrip.V;

/** Time-based trailing motion; shoulder anchors remain exact and old scenes cannot leak into new ones. */
final class WingMotion {
    private final int anchorsPerWing;
    WingMotion(int anchorsPerWing) {
        if (anchorsPerWing < 2) throw new IllegalArgumentException("Too few wing anchors");
        this.anchorsPerWing = anchorsPerWing;
    }
    private UUID owner;
    private Object model;
    private List<V> previous;
    private long at;
    List<V> update(UUID nextOwner, Object nextModel, List<V> points, long millis) {
        long elapsed = millis - at;
        if (!nextOwner.equals(owner) || model != nextModel || previous == null || previous.size() != points.size()
                || elapsed < 0 || elapsed > 250 || points.getFirst().sub(previous.getFirst()).length() > 8) {
            previous = List.copyOf(points);
        } else {
            var next = new ArrayList<V>(points.size());
            for (int i = 0; i < points.size(); i++) {
                double u = (i % anchorsPerWing) / (double) (anchorsPerWing - 1);
                double blend = u == 0 ? 1 : -Math.expm1(-elapsed / (100.0 * u * u));
                next.add(previous.get(i).add(points.get(i).sub(previous.get(i)).mul(blend)));
            }
            previous = List.copyOf(next);
        }
        owner = nextOwner; model = nextModel; at = millis;
        return previous;
    }
    void clear() { owner = null; model = null; previous = null; at = 0; }
}
