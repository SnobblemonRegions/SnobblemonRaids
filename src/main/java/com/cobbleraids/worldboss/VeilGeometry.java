package com.cobbleraids.worldboss;

import java.util.ArrayList;
import java.util.List;

/**
 * The Daily Boss's Half Veil: a low ring of upright amber panels standing on the zone's edge. Pure maths with no Minecraft types.
 *
 * <p>Each panel is the same two-sided quad the dome uses (a display entity's model spans one block, centred), stretched to
 * {@code width x height} and turned about Y so its face points out from the centre. Panels are returned as {@link DomeGeometry.Panel}s
 * so the dome's visibility and nearest-panel helpers work on them unchanged; {@code y} is the panel's centre above the ground ring
 * (the caller lifts each one onto the terrain under it).
 */
final class VeilGeometry {

    /** Neighbours overlap by this much of their width: enough to close hairline gaps, little enough that the doubled haze does not show as a seam. */
    static final double OVERLAP = 1.01;

    private VeilGeometry() {}

    /**
     * @param radius the zone radius: the ring the veil stands on
     * @param height how tall each panel is
     * @param width  the target panel width along the rim; the real width is the chord that closes the ring evenly
     */
    static List<DomeGeometry.Panel> panels(double radius, double height, double width) {
        int count = Math.max(8, (int) Math.ceil(2 * Math.PI * radius / width));
        double step = 2 * Math.PI / count;
        float chord = (float) (2 * radius * Math.sin(step / 2) * OVERLAP);
        List<DomeGeometry.Panel> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = i * step;
            double nx = Math.sin(angle);
            double nz = Math.cos(angle);
            // a pure turn about Y by `angle` takes the quad's +Z face to (sin, 0, cos), the outward normal
            float ly = (float) Math.sin(angle / 2);
            float lw = (float) Math.cos(angle / 2);
            out.add(new DomeGeometry.Panel(radius * nx, height / 2, radius * nz, 0f, ly, 0f, lw, 0f, 0f, 0f, 1f,
                    chord, (float) height, i % 2, nx, 0, nz, new double[0]));
        }
        return out;
    }
}
