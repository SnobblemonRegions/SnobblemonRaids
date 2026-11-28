package com.cobbleraids.worldboss;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import java.util.IdentityHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldBossCommandNodesTest {
    private static final class PacketNode extends LiteralCommandNode<SharedSuggestionProvider> {
        private int hashes;
        PacketNode(){super("identical",null,source->true,null,null,false);}
        @Override public int hashCode(){hashes++;return super.hashCode();}
    }
    private static final class Node extends LiteralCommandNode<CommandSourceStack> {
        private int hashes;
        Node(){super("identical",null,source->true,null,null,false);}
        @Override public int hashCode(){hashes++;return super.hashCode();}
    }
    @Test void mappingNeverRecursivelyHashesCommandSubtreesAndPreservesPriorEntries(){
        var node=new Node();var clone=new LiteralCommandNode<SharedSuggestionProvider>("copy",null,source->true,null,null,false);
        var existing=new IdentityHashMap<CommandNode<CommandSourceStack>,CommandNode<SharedSuggestionProvider>>();existing.put(node,clone);
        var mapping=WorldBossCommandNodes.mapping(existing);
        assertSame(clone,mapping.get(node));assertEquals(0,node.hashes);
        assertNotSame(existing,mapping);mapping.clear();assertSame(clone,existing.get(node));
    }
    @Test void structurallyEqualNodesRetainIndependentRedirectTargets(){
        var first=new Node();var second=new Node();assertEquals(first,second);
        var one=new LiteralCommandNode<SharedSuggestionProvider>("one",null,source->true,null,null,false);
        var two=new LiteralCommandNode<SharedSuggestionProvider>("two",null,source->true,null,null,false);
        var mapping=WorldBossCommandNodes.mapping(new IdentityHashMap<>());
        mapping.put(first,one);mapping.put(second,two);
        assertEquals(2,mapping.size());assertSame(one,mapping.get(first));assertSame(two,mapping.get(second));
    }
    @Test void packetEnumerationRetainsDistinctNodeIdsWithoutDeepHashing(){
        var first=new PacketNode();var second=new PacketNode();assertEquals(first,second);
        var original=new it.unimi.dsi.fastutil.objects.Object2IntArrayMap<CommandNode<SharedSuggestionProvider>>();
        original.defaultReturnValue(-1);original.put(first,7);
        var mapping=WorldBossCommandNodes.packetMapping(original);
        assertEquals(7,mapping.getInt(first));assertEquals(-1,mapping.getInt(second));
        mapping.put(second,8);assertEquals(2,mapping.size());assertEquals(7,mapping.getInt(first));assertEquals(8,mapping.getInt(second));
        assertEquals(0,first.hashes+second.hashes);assertEquals(1,original.size());
    }
}
