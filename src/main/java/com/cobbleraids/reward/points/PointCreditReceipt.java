package com.cobbleraids.reward.points;

/** A frozen credit promise and the part already applied to a balance, saved with that balance. */
public record PointCreditReceipt(int promised, int credited) {
    public PointCreditReceipt {
        if (promised < 0 || credited < 0 || credited > promised) {
            throw new IllegalArgumentException("Invalid Raid Points credit receipt");
        }
    }

    public int pending() { return promised - credited; }

    public Allocation allocate(int balance) {
        if (balance < 0) throw new IllegalArgumentException("Negative Raid Points balance");
        int applied = Math.min(pending(), Integer.MAX_VALUE - balance);
        return new Allocation(balance + applied, new PointCreditReceipt(promised, credited + applied));
    }

    public record Allocation(int balance, PointCreditReceipt receipt) {}
}
