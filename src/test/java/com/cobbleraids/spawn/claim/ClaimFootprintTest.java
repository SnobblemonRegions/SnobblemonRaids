package com.cobbleraids.spawn.claim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The block square a raid boss needs clear of land claims, and the chunks a claim lookup must read to see every claim that could
 * touch it. A chunk missed here is a claim the spawn check never sees, so a boss lands inside it again.
 */
class ClaimFootprintTest {

    @Test
    @DisplayName("with no buffer the footprint is the one column, in its own chunk")
    void zeroBuffer() {
        ClaimFootprint f = ClaimFootprint.around(5, 9, 0);
        assertEquals(5, f.minX());
        assertEquals(5, f.maxX());
        assertEquals(9, f.minZ());
        assertEquals(9, f.maxZ());
        assertEquals(0, f.minChunkX());
        assertEquals(0, f.maxChunkX());
        assertEquals(0, f.minChunkZ());
        assertEquals(0, f.maxChunkZ());
    }

    @Test
    @DisplayName("negative coordinates floor into the chunk below zero, not chunk 0")
    void negativeCoordinates() {
        ClaimFootprint f = ClaimFootprint.around(-1, -16, 0);
        assertEquals(-1, f.minChunkX());
        assertEquals(-1, f.minChunkZ());
        ClaimFootprint g = ClaimFootprint.around(-17, -17, 0);
        assertEquals(-2, g.minChunkX());
        assertEquals(-2, g.minChunkZ());
    }

    @Test
    @DisplayName("a buffer that crosses a chunk border reads the neighbouring chunks too")
    void bufferCrossesChunkBorders() {
        ClaimFootprint f = ClaimFootprint.around(15, 0, 1);
        assertEquals(14, f.minX());
        assertEquals(16, f.maxX());
        assertEquals(0, f.minChunkX());
        assertEquals(1, f.maxChunkX());
        assertEquals(-1, f.minChunkZ(), "z = -1 is in chunk -1");
        assertEquals(0, f.maxChunkZ());
    }

    @Test
    @DisplayName("the largest allowed buffer spans a bounded number of chunks")
    void maxBufferIsBounded() {
        ClaimFootprint f = ClaimFootprint.around(0, 0, ClaimFootprint.MAX_BUFFER);
        int chunksX = f.maxChunkX() - f.minChunkX() + 1;
        int chunksZ = f.maxChunkZ() - f.minChunkZ() + 1;
        assertEquals(9, chunksX);
        assertEquals(9, chunksZ);
    }

    @Test
    @DisplayName("a buffer outside 0..MAX_BUFFER is refused")
    void bufferOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> ClaimFootprint.around(0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> ClaimFootprint.around(0, 0, ClaimFootprint.MAX_BUFFER + 1));
    }
}
