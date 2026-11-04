package com.cobbleraids.radar;

/**
 * Where everything goes in the radar's right-click menu, a 3x9 chest. No Minecraft types, so the layout is tested: nothing shares a slot, the
 * controls are centred, and a click on a decoration is never taken for a button.
 *
 * <pre>
 *   C . . . H . . . C        C corner, H header (charges summary)
 *   . 1 2 3 M T . G .        1 2 3 charges, M hologram or flat map, T next-charge timer, G how it works
 *   C . . O . N . X C        O open/close, N next raid, X close menu
 * </pre>
 */
final class RadarMenuLayout {
    static final int SIZE = 27;
    static final int HEADER = 4, MODE = 13, CLOCK = 14, GUIDE = 16;
    static final int[] CHARGES = {10, 11, 12};
    static final int TOGGLE = 20, NEXT = 22, CLOSE = 24;
    static final int[] CORNERS = {0, 8, 18, 26};

    private RadarMenuLayout() {}

    enum Control { NONE, TOGGLE, NEXT, CLOSE, MODE }

    static Control controlAt(int slot) {
        return switch (slot) {
            case TOGGLE -> Control.TOGGLE;
            case NEXT -> Control.NEXT;
            case CLOSE -> Control.CLOSE;
            case MODE -> Control.MODE;
            default -> Control.NONE;
        };
    }
}
