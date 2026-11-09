package com.cobbleraids.reward.points;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Tells a player an award was trimmed by a daily cap, so a raid that paid 0 Raid Points is not a mystery. */
public final class RaidPointsCapNotice {

    private RaidPointsCapNotice() {}

    /** Says nothing when the award was paid in full; {@code player} may be null for someone offline. */
    public static void tell(ServerPlayer player, RaidPointsSource source, int requested, int credited) {
        if (player == null || credited >= requested) return;
        int cap = com.cobbleraids.config.CobbleRaidsConfigManager.get().raidPoints().dailyCapFor(source);
        String from = source == RaidPointsSource.TOWER ? "towers" : "raids";
        String paid = credited > 0 ? "This one paid " + credited + " of " + requested + ". " : "";
        player.sendSystemMessage(Component.literal("You have reached today's limit of " + cap
                + " Raid Points from " + from + ". " + paid
                + "Rewards other than Raid Points are unaffected. The limit resets at 00:00 UTC.")
                .withStyle(ChatFormatting.YELLOW));
    }
}
