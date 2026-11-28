package com.cobbleraids.worldboss;

import com.mojang.brigadier.tree.CommandNode;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

/** Native server-node to client-node mapping is a graph of object references, not structural values. */
public final class WorldBossCommandNodes {
    private static final java.util.concurrent.atomic.AtomicLong mappings=new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong packetMappings=new java.util.concurrent.atomic.AtomicLong();
    private static final it.unimi.dsi.fastutil.Hash.Strategy<CommandNode<SharedSuggestionProvider>> REFERENCES=new it.unimi.dsi.fastutil.Hash.Strategy<>(){
        @Override public int hashCode(CommandNode<SharedSuggestionProvider> node){return System.identityHashCode(node);}
        @Override public boolean equals(CommandNode<SharedSuggestionProvider> first,CommandNode<SharedSuggestionProvider> second){return first==second;}
    };
    private WorldBossCommandNodes(){}
    public static Map<CommandNode<CommandSourceStack>,CommandNode<SharedSuggestionProvider>> mapping(
            Map<CommandNode<CommandSourceStack>,CommandNode<SharedSuggestionProvider>> original){
        var mapping=new java.util.IdentityHashMap<>(original);mappings.incrementAndGet();return mapping;
    }
    public static long mappings(){return mappings.get();}
    public static Object2IntMap<CommandNode<SharedSuggestionProvider>> packetMapping(Object2IntMap<CommandNode<SharedSuggestionProvider>> original){
        var mapping=new it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap<CommandNode<SharedSuggestionProvider>>(original.size(),REFERENCES);
        if(!original.isEmpty())mapping.putAll(original);
        mapping.defaultReturnValue(original.defaultReturnValue());packetMappings.incrementAndGet();return mapping;
    }
    public static long packetMappings(){return packetMappings.get();}
}
