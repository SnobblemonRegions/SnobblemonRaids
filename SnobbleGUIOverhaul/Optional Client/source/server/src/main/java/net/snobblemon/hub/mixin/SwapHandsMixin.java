package net.snobblemon.hub.mixin;

import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.snobblemon.hub.SnobbleHub;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Turns the F key (swap hands) into "open the hub". Shift+F still swaps hands. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SwapHandsMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void snobblehub$swapHands(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (packet.getAction() != ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) return;
        // the packet first arrives on the network thread and is re-run on the server thread: act only on the second run
        if (!player.server.isSameThread()) return;
        if (SnobbleHub.onSwapHands(player)) ci.cancel();
    }
}
