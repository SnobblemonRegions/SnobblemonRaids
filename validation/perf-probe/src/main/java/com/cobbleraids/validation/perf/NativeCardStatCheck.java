package com.cobbleraids.validation.perf;

import net.minecraft.server.level.ServerPlayer;

/** Calls the real optional bonus calculation; does not dispatch a capture, faint or drop event. */
final class NativeCardStatCheck {
    private static boolean used;
    static boolean used(){return used;}
    static String run(ServerPlayer player) throws ReflectiveOperationException {
        used=true;
        var root=Class.forName("com.cobbleraids.worldboss.WorldBossService");
        var count=root.getMethod("quietCardStatCalls");
        long before=(Long)count.invoke(null);
        float bonus=(Float)Class.forName("com.howlite.cobblemoncards.util.CardStatUtil")
                .getMethod("getPlayerDropBonus",ServerPlayer.class).invoke(null,player);
        long quiet=(Long)count.invoke(null)-before;
        return "bonus="+bonus+" debugCalls="+quiet;
    }
}
