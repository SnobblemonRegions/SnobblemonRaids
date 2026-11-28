package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossCommandNodes;
import com.mojang.brigadier.tree.CommandNode;
import java.util.Map;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Only replace the empty reference map; native permission filtering, cloning and packet send still run. */
@Mixin(Commands.class)
public abstract class WorldBossCommandNodesMixin {
    @ModifyVariable(method="sendCommands",at=@At("STORE"),ordinal=0,require=1,allow=1)
    private Map<CommandNode<CommandSourceStack>,CommandNode<SharedSuggestionProvider>> cobbleRaids$references(
            Map<CommandNode<CommandSourceStack>,CommandNode<SharedSuggestionProvider>> original){
        try{return WorldBossCommandNodes.mapping(original);}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:command-node-map",failure);throw failure;}
    }
}
