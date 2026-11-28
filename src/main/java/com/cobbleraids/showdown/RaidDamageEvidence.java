package com.cobbleraids.showdown;

/** Server protocol evidence, separate from Cobblemon's later animation-dispatch turn counter. */
public record RaidDamageEvidence(int turn, long sequence) {
    public RaidDamageEvidence {
        if (turn < 0 || sequence < 1 || sequence > 9_007_199_254_740_991L) throw new IllegalArgumentException("Invalid raid damage evidence");
    }
    public static RaidDamageEvidence parse(String turn, String sequence) {
        try { return new RaidDamageEvidence(Integer.parseInt(turn), Long.parseLong(sequence)); }
        catch (IllegalArgumentException invalid) { return null; }
    }
}
