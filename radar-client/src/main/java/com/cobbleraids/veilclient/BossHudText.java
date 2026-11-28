package com.cobbleraids.veilclient;

/** Compact labels for the server-owned status line; unknown messages remain readable. */
final class BossHudText {
    private BossHudText() {}
    static String[] stack(String status) { return status.split(" · ",2); }
    static String status(String source) {
        return switch (source) {
            case "STAFF HOLD" -> "Staff hold";
            case "SECURING REWARDS" -> "Securing rewards";
            case "COMBAT PRESENTATION FIXTURE" -> "Combat preview";
            default -> source.replace("GATHERING  /  OPENS IN ","Opens in ")
                    .replace("  /  "," · ").replace(" READY"," ready")
                    .replace(" FIGHTING"," fighting").replace(" REMAINING","")
                    .replace("REGROUP · ","Regroup · ");
        };
    }
}
