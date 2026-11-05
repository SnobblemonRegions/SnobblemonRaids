package com.cobbleraids.radar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbleraids.charges.RaidChargeAccount;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The radar menu's slots: a framed, symmetric 3x9 chest, with no two things in one slot. */
class RadarMenuLayoutTest {

    private static Set<Integer> used() {
        Set<Integer> slots = new HashSet<>();
        for (int slot : RadarMenuLayout.CHARGES) assertTrue(slots.add(slot), "slot " + slot + " used twice");
        for (int slot : new int[] {RadarMenuLayout.HEADER, RadarMenuLayout.CLOCK, RadarMenuLayout.GUIDE, RadarMenuLayout.MODE, RadarMenuLayout.TOGGLE,
                RadarMenuLayout.NEXT, RadarMenuLayout.CLOSE}) assertTrue(slots.add(slot), "slot " + slot + " used twice");
        for (int slot : RadarMenuLayout.CORNERS) assertTrue(slots.add(slot), "corner " + slot + " clashes with an item");
        return slots;
    }

    @Test
    void everySlotIsInsideTheChest() {
        for (int slot : used()) assertTrue(slot >= 0 && slot < RadarMenuLayout.SIZE, "slot " + slot);
    }

    @Test
    void thereIsOneChargeIconPerCharge() {
        assertEquals(RaidChargeAccount.CAPACITY, RadarMenuLayout.CHARGES.length);
    }

    @Test
    void theControlsAreCentredAndSymmetric() {
        assertEquals(2 * RadarMenuLayout.NEXT, RadarMenuLayout.TOGGLE + RadarMenuLayout.CLOSE, "open/close and close sit either side of next");
        assertEquals(RadarMenuLayout.SIZE / 2 + 9, RadarMenuLayout.NEXT, "next is bottom-centre");
        assertEquals(4, RadarMenuLayout.HEADER % 9, "the header is top-centre");
    }

    @Test
    void theDisplayChoiceSitsInTheMiddleOfTheMiddleRow() {
        assertEquals(RadarMenuLayout.SIZE / 2, RadarMenuLayout.MODE);
    }

    @Test
    void theCornersAreTheFourCornersOfTheFrame() {
        assertEquals(Set.of(0, 8, 18, 26), Set.of(RadarMenuLayout.CORNERS[0], RadarMenuLayout.CORNERS[1], RadarMenuLayout.CORNERS[2], RadarMenuLayout.CORNERS[3]));
    }

    @Test
    void aClickOnAControlIsRecognisedAndAnythingElseIsNot() {
        assertEquals(RadarMenuLayout.Control.TOGGLE, RadarMenuLayout.controlAt(RadarMenuLayout.TOGGLE));
        assertEquals(RadarMenuLayout.Control.NEXT, RadarMenuLayout.controlAt(RadarMenuLayout.NEXT));
        assertEquals(RadarMenuLayout.Control.CLOSE, RadarMenuLayout.controlAt(RadarMenuLayout.CLOSE));
        assertEquals(RadarMenuLayout.Control.MODE, RadarMenuLayout.controlAt(RadarMenuLayout.MODE));
        for (int slot = -3; slot < RadarMenuLayout.SIZE + 3; slot++) {
            if (slot == RadarMenuLayout.TOGGLE || slot == RadarMenuLayout.NEXT || slot == RadarMenuLayout.CLOSE || slot == RadarMenuLayout.MODE) continue;
            assertEquals(RadarMenuLayout.Control.NONE, RadarMenuLayout.controlAt(slot), "slot " + slot + " is only decoration");
        }
    }
}
