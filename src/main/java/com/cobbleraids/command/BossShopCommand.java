package com.cobbleraids.command;

import com.cobbleraids.catching.BossShopOffers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * {@code /cobbleraids bossshop}: what a player has saved in their personal boss shop, and the answer to the
 * offer a won raid makes them. The chat buttons of {@link BossShopOffers} run these, so a player never has to
 * type one, but they work typed too.
 */
public final class BossShopCommand {
    private BossShopCommand() {}

    /** The command tree, separate from the Fabric event so a test can build it without a server. */
    public static void build(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobbleraids")
                .then(Commands.literal("bossshop")
                        .executes(context -> {
                            BossShopOffers.describeTo(context.getSource().getPlayerOrException());
                            return 1;
                        })
                        .then(Commands.literal("save")
                                .executes(context -> BossShopOffers.accept(context.getSource().getPlayerOrException(), null) ? 1 : 0)
                                .then(Commands.argument("replace", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            ResourceLocation replace = ResourceLocation.tryParse(
                                                    StringArgumentType.getString(context, "replace").trim());
                                            if (replace == null) {
                                                context.getSource().sendFailure(Component.literal("That is not a saved boss.")
                                                        .withStyle(ChatFormatting.RED));
                                                return 0;
                                            }
                                            return BossShopOffers.accept(context.getSource().getPlayerOrException(), replace) ? 1 : 0;
                                        })))));
    }
}
