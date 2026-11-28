package com.cobbleraids.radarclient.mixin;

import com.cobbleraids.veilclient.WorldBossHud;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BossHealthOverlay.class)
public abstract class WorldBossHudMixin {
    @Shadow @Final private Map<UUID,LerpingBossEvent> events;
    @Unique private UUID cobbleraids$hiddenHud;

    @WrapMethod(method="render")
    private void cobbleraids$renderOwnedHud(GuiGraphics gui, Operation<Void> original) {
        int reserve = 0;
        try {
            var id = WorldBossHud.owned();
            var event = id == null ? null : events.get(id);
            if (event != null) {
                reserve = WorldBossHud.draw(gui,event);
                if (reserve > 0) cobbleraids$hiddenHud = id;
            }
        } catch (RuntimeException failure) { WorldBossHud.fault(failure); }
        if (reserve > 0) gui.pose().pushPose();
        try {
            if (reserve > 0) gui.pose().translate(0,reserve,0);
            original.call(gui);
        } finally {
            cobbleraids$hiddenHud = null;
            if (reserve > 0) gui.pose().popPose();
        }
    }
    @ModifyExpressionValue(method="render",at=@At(value="INVOKE",target="Ljava/util/Map;values()Ljava/util/Collection;"))
    private Collection<LerpingBossEvent> cobbleraids$keepOtherBars(Collection<LerpingBossEvent> original) {
        try {
            return cobbleraids$hiddenHud == null ? original : original.stream().filter(e -> !e.getId().equals(cobbleraids$hiddenHud)).toList();
        } catch (RuntimeException failure) { WorldBossHud.fault(failure); return original; }
    }
}
