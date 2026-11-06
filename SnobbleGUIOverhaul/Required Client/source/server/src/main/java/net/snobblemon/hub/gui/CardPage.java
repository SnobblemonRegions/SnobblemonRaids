package net.snobblemon.hub.gui;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.snobblemon.hub.Fmt;
import net.snobblemon.hub.HubData;

import java.util.List;

/** The Trainer Card: name, rank, region and balances, read from the live systems each time the tab opens. */
final class CardPage {
    void draw(HubGui g) {
        HubData.Profile pr = HubData.profile(g.p);
        g.layer("card_" + pr.accent());
        String[] values = {pr.name(), pr.rank(), pr.regionName(), pr.raid(), pr.dollars(), pr.gems(), pr.community()};
        int[] colors = {0xFFFFFF, 0x58D68D, accentColor(pr.accent()), 0xFFFFFF, 0xFFFFFF, 0x3FE0E8, 0xFFFFFF};
        for (int i = 0; i < values.length; i++) g.textRight(110, 37 + i * 10, values[i], colors[i]);
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        GameProfile gp = g.p.getGameProfile();
        head.set(DataComponents.PROFILE, new ResolvableProfile(gp));
        g.stackBtn(7, 2, head, Fmt.line(pr.name(), 0xFFFFFF),
                List.of(Fmt.line("Rank: " + pr.rank(), 0x58D68D), Fmt.line("Region: " + pr.regionName(), accentColor(pr.accent()))), null);
    }

    private static int accentColor(String accent) {
        return switch (accent) {
            case "root" -> 0x5FD35F;
            case "tide" -> 0x3FC1E8;
            default -> 0x9B6BFF;
        };
    }
}
