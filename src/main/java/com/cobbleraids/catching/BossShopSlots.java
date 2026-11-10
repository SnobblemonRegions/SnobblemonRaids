package com.cobbleraids.catching;

import java.util.Collection;

/**
 * The two limits on the bosses a player keeps in their personal shop: a slot cap, and a number of days each
 * saved boss stays before it leaves.
 *
 * <p>Before these existed every victor was given every boss they beat, one per species, for ever, so the page
 * only grew and nothing on it was a decision. With a handful of slots a player has to choose which bosses are
 * worth keeping, and with a deadline they have to earn the Raid Points to buy one while it is still there.
 *
 * <p>Pure, so both rules can be tested without a server. Generic in the key because the rule is about
 * counting, not about what a species is.
 */
public final class BossShopSlots {
    private static final long DAY_MILLIS = 86_400_000L;

    private BossShopSlots() {}

    public enum Verdict {
        /** There is a free slot. */
        ROOM,
        /** This species is already saved; the new boss takes that slot, whether or not the shop is full. */
        SAME_SPECIES,
        /** Every slot is used; one of the saved bosses has to be given up for this one. */
        FULL
    }

    public static <K> Verdict verdict(Collection<K> saved, K offered, int maxSlots) {
        if (saved.contains(offered)) return Verdict.SAME_SPECIES;
        return saved.size() < maxSlots ? Verdict.ROOM : Verdict.FULL;
    }

    /** The title of the Your Bosses page, which doubles as its slot counter. */
    public static String heading(int used, int maxSlots) {
        return "Your Bosses - " + used + "/" + maxSlots + (maxSlots == 1 ? " slot" : " slots");
    }

    /** Whether a boss saved at {@code savedAt} has been kept its {@code keepDays}. Zero days keeps it for ever. */
    public static boolean expired(long savedAt, long now, int keepDays) {
        return keepDays > 0 && now - savedAt >= keepDays * DAY_MILLIS;
    }

    /** Milliseconds until it leaves, never negative; -1 when saved bosses do not leave at all. */
    public static long millisLeft(long savedAt, long now, int keepDays) {
        if (keepDays <= 0) return -1L;
        long left = savedAt + keepDays * DAY_MILLIS - now;
        return Math.max(0L, Math.min(left, keepDays * DAY_MILLIS));
    }
}
