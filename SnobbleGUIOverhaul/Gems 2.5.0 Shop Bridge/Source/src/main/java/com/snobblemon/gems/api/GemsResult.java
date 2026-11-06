package com.snobblemon.gems.api;

/**
 * Outcome of a ledger call.
 *
 * @param status  what happened
 * @param applied the change that was actually made (can be smaller than requested when the balance cap or a daily limit cut it; negative for withdrawals)
 * @param balance the balance after the call
 */
public record GemsResult(Status status, long applied, long balance) {
    public enum Status {
        OK,
        /** The same ref was already processed; nothing changed. */
        DUPLICATE,
        /** A withdrawal larger than the balance. */
        INSUFFICIENT,
        /** Zero/negative/absurd amount. */
        INVALID,
        /** Community reward refused (cooldown, daily limit, disabled, duplicate message...). */
        LIMITED
    }

    public boolean ok() { return status == Status.OK; }
}
