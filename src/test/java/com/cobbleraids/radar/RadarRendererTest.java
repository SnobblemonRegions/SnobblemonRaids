package com.cobbleraids.radar;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RadarRendererTest {
    @Test void northEastAndHeightUseConsistentCoordinates() {
        var origin = RadarRenderer.project(0, 0, 0);
        assertTrue(RadarRenderer.project(0, -100, 0).y() < origin.y());
        assertTrue(RadarRenderer.project(100, 0, 0).x() > origin.x());
        // A top-down dial: how high something is does not move it, or it would no longer sit over the ground it is above.
        assertEquals(origin, RadarRenderer.project(0, 0, 30));
        assertEquals(origin, RadarRenderer.project(0, 0, -30));
    }
    @Test void theDialIsATrueTopDownCircleWithTheSameScaleOnBothAxes() {
        var origin = RadarRenderer.project(0, 0, 0);
        assertEquals(RadarRenderer.project(100, 0, 0).x() - origin.x(), RadarRenderer.project(0, 100, 0).y() - origin.y(), 1e-9);
        // The grid's edge, 16 samples out, reaches the rim of the filled dial.
        assertEquals(45, RadarRenderer.project(16 * RadarRenderer.STEP, 0, 0).x() - origin.x(), 1e-9);
    }
    @Test void offscreenTargetRetainsBearingAtTheEdge() {
        var point = RadarRenderer.project(2000, -1000, 0);
        var edge = RadarRenderer.clampTarget(point, RadarRenderer.project(0, 0, 0));
        assertEquals(42, Math.hypot(edge.x() - 64, edge.y() - 63), 1e-9);
        assertEquals((point.y() - 63) / (point.x() - 64), (edge.y() - 63) / (edge.x() - 64), 1e-9);
        var nearby = RadarRenderer.project(10, 10, 0);
        assertEquals(nearby, RadarRenderer.clampTarget(nearby, RadarRenderer.project(0, 0, 0)));
    }
    @Test void edgeBearingStaysCorrectWhenPlayerIsNotAtTheMapCentre() {
        var player = RadarRenderer.project(70, 50, 0);
        var target = RadarRenderer.project(2000, -1000, 0);
        var edge = RadarRenderer.clampTarget(target, player);
        assertEquals(42, Math.hypot(edge.x() - 64, edge.y() - 63), 1e-9);
        assertEquals((target.y() - player.y()) / (target.x() - player.x()),
                (edge.y() - player.y()) / (edge.x() - player.x()), 1e-9);
    }
    @Test void facingArrowAgreesWithTheProjectedGeographicBearing() {
        var origin = RadarRenderer.project(0, 0, 0);
        var target = RadarRenderer.project(100, -100, 0);
        var facing = RadarRenderer.facing(-135);
        assertEquals((target.y() - origin.y()) / (target.x() - origin.x()), facing.y() / facing.x(), 1e-9);
    }
    @Test void unknownTerrainNeverInventsContours() {
        int[] heights = new int[RadarRenderer.GRID * RadarRenderer.GRID];
        Arrays.fill(heights, RadarRenderer.UNKNOWN);
        byte[] frame = RadarRenderer.terrain(heights, 64);
        for (byte pixel : frame) {
            assertNotEquals(RadarRenderer.CONTOUR, pixel);
            assertNotEquals(RadarRenderer.SHADOW, pixel);
        }
    }
    @Test void mountainsAddContoursAndOverlaysDoNotMutateCachedTerrain() {
        int[] heights = new int[RadarRenderer.GRID * RadarRenderer.GRID];
        for (int z = 0; z < RadarRenderer.GRID; z++) for (int x = 0; x < RadarRenderer.GRID; x++) {
            heights[z * RadarRenderer.GRID + x] = 64 + (int) Math.max(0, 100 - Math.hypot(x - 16, z - 16) * 9);
        }
        byte[] background = RadarRenderer.terrain(heights, 64), original = background.clone();
        boolean contour = false;
        for (byte color : background) contour |= color == RadarRenderer.CONTOUR;
        assertTrue(contour);
        byte[] frame = RadarRenderer.frame(background, RadarRenderer.project(0, 0, 0), 90,
                RadarRenderer.project(1200, -600, 0), "1342M", "LEGENDARY", false);
        assertArrayEquals(original, background);
        assertFalse(Arrays.equals(frame, background));
        assertEquals(128 * 128, frame.length);
        boolean raidMarker = false;
        for (byte color : frame) raidMarker |= color == RadarRenderer.ORANGE;
        assertTrue(raidMarker);
    }
    private static int[] hills() {
        int[] heights = new int[RadarRenderer.GRID * RadarRenderer.GRID];
        for (int z = 0; z < RadarRenderer.GRID; z++) for (int x = 0; x < RadarRenderer.GRID; x++) {
            heights[z * RadarRenderer.GRID + x] = 64 + (int) (28 * Math.sin(x * 0.45) * Math.cos(z * 0.38) + x + z);
        }
        return heights;
    }
    private static boolean isFill(byte code) { return code >= RadarRenderer.FILL && code < RadarRenderer.COLOR_COUNT; }
    private static int tint(byte code) { return (code - RadarRenderer.FILL) / RadarRenderer.SHADES; }
    private static int shade(byte code) { return (code - RadarRenderer.FILL) % RadarRenderer.SHADES; }
    private static double distanceFromCentre(int index) {
        return Math.hypot(index % RadarRenderer.SIZE - 64, index / RadarRenderer.SIZE - 63);
    }
    @Test void knownTerrainFillsTheWholeDial() {
        byte[] pixels = RadarRenderer.terrain(hills(), 64);
        for (int i = 0; i < pixels.length; i++) {
            if (distanceFromCentre(i) <= 43) assertTrue(isFill(pixels[i]) || pixels[i] == RadarRenderer.CONTOUR || pixels[i] == RadarRenderer.LOW
                    || pixels[i] == RadarRenderer.SHADOW, "pixel " + i % 128 + "," + i / 128 + " is empty: " + pixels[i]);
        }
    }
    @Test void unknownTerrainIsNotFilledOrInvented() {
        int[] heights = new int[RadarRenderer.GRID * RadarRenderer.GRID];
        Arrays.fill(heights, RadarRenderer.UNKNOWN);
        for (byte pixel : RadarRenderer.terrain(heights, 64)) assertFalse(isFill(pixel));
        // A quarter of the grid known leaves the rest of the dial honestly empty.
        int[] part = hills();
        for (int z = 0; z < RadarRenderer.GRID; z++) for (int x = 0; x < RadarRenderer.GRID; x++) if (x > 16 || z > 16) part[z * RadarRenderer.GRID + x] = RadarRenderer.UNKNOWN;
        byte[] pixels = RadarRenderer.terrain(part, 64);
        for (int i = 0; i < pixels.length; i++) {
            int x = i % RadarRenderer.SIZE, z = i / RadarRenderer.SIZE;
            if (x > 64 + 3 && z > 63 + 3) assertFalse(isFill(pixels[i]), "south-east is unknown at " + x + "," + z);
        }
    }
    @Test void reliefUsesSeveralTintsAndFlatGroundUsesOne() {
        java.util.Set<Integer> tints = new java.util.HashSet<>();
        for (byte pixel : RadarRenderer.terrain(hills(), 64)) if (isFill(pixel)) tints.add(tint(pixel));
        assertTrue(tints.size() >= 4, "got tints " + tints);
        int[] flat = new int[RadarRenderer.GRID * RadarRenderer.GRID];
        Arrays.fill(flat, 70);
        java.util.Set<Integer> flatTints = new java.util.HashSet<>();
        for (byte pixel : RadarRenderer.terrain(flat, 70)) if (isFill(pixel)) flatTints.add(tint(pixel));
        assertEquals(1, flatTints.size());
    }
    @Test void slopesFacingTheLightAreBrighterThanThoseFacingAway() {
        int[] towardLight = new int[RadarRenderer.GRID * RadarRenderer.GRID], awayFromLight = new int[RadarRenderer.GRID * RadarRenderer.GRID];
        for (int z = 0; z < RadarRenderer.GRID; z++) for (int x = 0; x < RadarRenderer.GRID; x++) {
            towardLight[z * RadarRenderer.GRID + x] = 60 + (x + z) * 3;           // rising to the south-east: faces the north-west light
            awayFromLight[z * RadarRenderer.GRID + x] = 60 + (64 - x - z) * 3;    // rising to the north-west
        }
        double bright = 0, dark = 0;
        int count = 0;
        byte[] lit = RadarRenderer.terrain(towardLight, 60), shadowed = RadarRenderer.terrain(awayFromLight, 60);
        for (int i = 0; i < lit.length; i++) if (isFill(lit[i]) && isFill(shadowed[i])) {
            bright += shade(lit[i]);
            dark += shade(shadowed[i]);
            count++;
        }
        assertTrue(count > 1000);
        assertTrue(bright / count > dark / count + 0.5, "lit " + bright / count + " vs shadowed " + dark / count);
    }
    @Test void everyColourTheRendererCanProduceHasAMapPaletteEntry() {
        assertEquals(RadarRenderer.COLOR_COUNT, RadarPalette.COLORS.length);
        for (byte pixel : RadarRenderer.terrain(hills(), 64)) assertTrue(pixel >= 0 && pixel < RadarRenderer.COLOR_COUNT);
        byte[] frame = RadarRenderer.frame(RadarRenderer.terrain(hills(), 64), RadarRenderer.project(30, 20, 0), 45,
                RadarRenderer.project(300, -200, 0), "300M", "RARE", true);
        for (byte pixel : frame) assertTrue(pixel >= 0 && pixel < RadarRenderer.COLOR_COUNT);
    }
    @Test void malformedTerrainIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RadarRenderer.terrain(new int[10], 64));
    }
}
