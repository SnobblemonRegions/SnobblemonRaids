package com.snobblemon.gems;

import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Placeholder API hooks (the placeholder system StyledChat, StyledSidebars and StyledPlayerList all read).
 * Placeholder API writes ids as %namespace:name%, so the public form is %snobblegems:balance% (the spigot style %snobblegems_balance% is not parsed by Fabric's Placeholder API).
 * Loaded only when Placeholder API is installed.
 */
public final class PH {
    private PH() {}

    private static void reg(String ns, String id, BiFunction<PlaceholderContext, String, String> f) {
        Placeholders.register(ResourceLocation.fromNamespaceAndPath(ns, id), (ctx, arg) -> {
            SnobbleGems m = SnobbleGems.I;
            if (m == null || m.ledger == null || m.server == null) return PlaceholderResult.invalid("not ready");
            try {
                String s = f.apply(ctx, arg);
                return s == null ? PlaceholderResult.invalid("n/a") : PlaceholderResult.value(s);
            } catch (Throwable t) {
                return PlaceholderResult.invalid("error");
            }
        });
    }

    private static long bal(PlaceholderContext ctx) {
        return ctx.hasPlayer() ? SnobbleGems.I.ledger.balance(ctx.player().getUUID()) : 0;
    }

    private static String fmt(long v) {
        return SnobbleGems.I.cfg.placeholders.formatBalance ? Txt.num(v) : String.valueOf(v);
    }

    /** Argument = player name or UUID, so a hologram or leaderboard can show someone else's balance. */
    private static Long balanceOf(String arg) {
        if (arg == null || arg.isBlank()) return null;
        String a = arg.trim();
        UUID id = Resolver.looksLikeUuid(a) ? Resolver.parseUuid(a) : null;
        if (id == null) {
            ServerPlayer online = SnobbleGems.I.server.getPlayerList().getPlayerByName(a);
            id = online != null ? online.getUUID() : SnobbleGems.I.ledger.uuidByName(a);
        }
        return id == null ? null : SnobbleGems.I.ledger.balance(id);
    }

    /** The Gem glyph (pack font snobblegemsv2:icons): returned as a styled component so the font survives inside StyledSidebars / StyledChat. */
    private static void regGlyph(String ns, String id, char ch) {
        Placeholders.register(ResourceLocation.fromNamespaceAndPath(ns, id), (ctx, arg) ->
                PlaceholderResult.value(Component.literal(String.valueOf(ch)).withStyle(net.minecraft.network.chat.Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath("snobblegemsv2", "icons")).withColor(0xFFFFFF))));
    }

    public static void register() {
        for (String ns : new String[]{"snobblegems", "snobblegemcurrency"}) {
            regGlyph(ns, "glyph", (char) 0xE300);
            regGlyph(ns, "glyph_large", (char) 0xE301);
        }
        for (String ns : new String[]{"snobblegems", "snobblegemcurrency"}) {
            // the main one: %snobblegems:balance%  -> 1,250
            reg(ns, "balance", (c, a) -> fmt(bal(c)));
            reg(ns, "gems", (c, a) -> fmt(bal(c)));
            reg(ns, "balance_raw", (c, a) -> String.valueOf(bal(c)));
            reg(ns, "balance_short", (c, a) -> Txt.shortNum(bal(c)));
            reg(ns, "currency", (c, a) -> SnobbleGems.I.cfg.currency.name);
            reg(ns, "icon", (c, a) -> Txt.strip(SnobbleGems.I.cfg.currency.chatIcon));
            reg(ns, "display", (c, a) -> Txt.strip(SnobbleGems.I.cfg.currency.chatIcon) + " " + SnobbleGems.I.cfg.currency.name + ": " + fmt(bal(c)));
            reg(ns, "earned_today", (c, a) -> c.hasPlayer() ? String.valueOf(SnobbleGems.I.ledger.earnedToday(c.player().getUUID())) : "0");
            reg(ns, "balance_of", (c, a) -> {
                Long v = balanceOf(a);
                return v == null ? "0" : fmt(v);
            });
        }
    }

    /** Resolves %placeholders% in a string for a player (the admin /snobblegems placeholder test). */
    public static String parse(ServerPlayer p, String text) {
        return Placeholders.parseText(Component.literal(text), PlaceholderContext.of(p)).getString();
    }
}
