package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossCommandNodes;
import com.mojang.brigadier.tree.CommandNode;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Preserve node identity through the native packet enumeration as well as native command cloning. */
@Mixin(ClientboundCommandsPacket.class)
public abstract class WorldBossCommandPacketNodesMixin {
    @ModifyVariable(method="enumerateNodes",at=@At("STORE"),ordinal=0,require=1,allow=1)
    private static Object2IntMap<CommandNode<SharedSuggestionProvider>> cobbleRaids$references(
            Object2IntMap<CommandNode<SharedSuggestionProvider>> original){
        try{return WorldBossCommandNodes.packetMapping(original);}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:command-packet-node-map",failure);throw failure;}
    }
}
