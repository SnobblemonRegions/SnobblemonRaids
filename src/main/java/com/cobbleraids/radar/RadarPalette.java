package com.cobbleraids.radar;

import net.minecraft.world.level.material.MapColor;

/**
 * The radar's logical colours, in {@link RadarRenderer}'s order, as the vanilla map colours a client really has: the renderer works in small logical
 * codes and the server translates them once per frame. The first eight are the dial, grid, lines and markers; after them come the terrain tints, one
 * hue per height band and four brightnesses of each for hillshading.
 */
public final class RadarPalette {
    /** Low ground to high ground, in rising brightness: deep blue, teal, cyan, then white peaks. */
    private static final MapColor[] HUES = {MapColor.COLOR_BLUE, MapColor.COLOR_CYAN, MapColor.WARPED_STEM, MapColor.COLOR_LIGHT_BLUE,
            MapColor.DIAMOND, MapColor.SNOW};
    /** Darkest to brightest: the order of {@link RadarRenderer#SHADES}. */
    private static final MapColor.Brightness[] SHADES = {MapColor.Brightness.LOWEST, MapColor.Brightness.LOW,
            MapColor.Brightness.NORMAL, MapColor.Brightness.HIGH};

    public static final byte[] COLORS = build();

    private RadarPalette() {}

    private static byte[] build() {
        byte[] colors = new byte[RadarRenderer.COLOR_COUNT];
        byte[] dial = {
                MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.LOWEST),     // BLACK
                MapColor.TERRACOTTA_CYAN.getPackedId(MapColor.Brightness.LOWEST), // GRID_COLOR
                MapColor.COLOR_CYAN.getPackedId(MapColor.Brightness.LOW),         // LOW
                MapColor.DIAMOND.getPackedId(MapColor.Brightness.NORMAL),         // CONTOUR
                MapColor.DIAMOND.getPackedId(MapColor.Brightness.HIGH),           // CYAN
                MapColor.SNOW.getPackedId(MapColor.Brightness.HIGH),              // WHITE
                MapColor.COLOR_ORANGE.getPackedId(MapColor.Brightness.HIGH),      // ORANGE
                MapColor.COLOR_CYAN.getPackedId(MapColor.Brightness.LOWEST)};     // SHADOW
        System.arraycopy(dial, 0, colors, 0, dial.length);
        for (int tint = 0; tint < RadarRenderer.TINTS; tint++) {
            for (int shade = 0; shade < RadarRenderer.SHADES; shade++) {
                colors[RadarRenderer.FILL + tint * RadarRenderer.SHADES + shade] = HUES[tint].getPackedId(SHADES[shade]);
            }
        }
        return colors;
    }
}
