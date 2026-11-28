package com.cobbleraids.validation.perf;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;

/** Post-timing native clone fixture: local nodes only, no command executes and no player is fabricated. */
final class NativeCommandNodesCheck {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static LiteralCommandNode<CommandSourceStack> literal(String name,Command<CommandSourceStack> command,
            Predicate<CommandSourceStack> permission,CommandNode<CommandSourceStack> redirect){
        return new LiteralCommandNode<>(name,command,permission,redirect,null,false);
    }
    @SuppressWarnings("unchecked")
    static String run(MinecraftServer server)throws ReflectiveOperationException{
        if(!server.isSameThread())throw new IllegalStateException("Command fixture requires Main");
        var implementation=Class.forName("com.cobbleraids.worldboss.WorldBossCommandNodes");
        long loginMappings=(Long)implementation.getMethod("mappings").invoke(null);
        long packetMappings=(Long)implementation.getMethod("packetMappings").invoke(null);
        require(loginMappings>=6,"Native login did not use identity command maps");
        require(packetMappings>=6,"Native login did not use identity packet node maps");
        var executed=new AtomicInteger();Command<CommandSourceStack> command=context->executed.incrementAndGet();
        Predicate<CommandSourceStack> allowed=source->true;
        var root=new RootCommandNode<CommandSourceStack>();
        var first=literal("leaf",command,allowed,null);var second=literal("leaf",command,allowed,null);
        require(first!=second&&first.equals(second),"Fixture needs distinct structurally equal nodes");
        var alpha=literal("alpha",null,allowed,null);alpha.addChild(first);root.addChild(alpha);
        var beta=literal("beta",null,allowed,null);beta.addChild(second);root.addChild(beta);
        root.addChild(literal("operator",command,source->source.hasPermission(4),null));
        root.addChild(literal("denied",command,source->false,null));
        root.addChild(literal("zalias_alpha",null,allowed,first));root.addChild(literal("zalias_beta",null,allowed,second));
        var methods=Arrays.stream(Commands.class.getDeclaredMethods()).filter(method->method.getReturnType()==void.class
                &&Arrays.equals(method.getParameterTypes(),new Class<?>[]{CommandNode.class,CommandNode.class,CommandSourceStack.class,Map.class})).toList();
        require(methods.size()==1,"Native command clone signature changed");var cloneMethod=methods.getFirst();cloneMethod.setAccessible(true);
        for(int permission:new int[]{0,4}){
            var copy=new RootCommandNode<SharedSuggestionProvider>();
            var mapping=(Map<CommandNode<CommandSourceStack>,CommandNode<SharedSuggestionProvider>>)implementation
                    .getMethod("mapping",Map.class).invoke(null,new IdentityHashMap<>());
            mapping.put(root,copy);
            cloneMethod.invoke(server.getCommands(),root,copy,server.createCommandSourceStack().withPermission(permission),mapping);
            require(copy.getChild("denied")==null,"Native denied command became visible");
            require((copy.getChild("operator")!=null)==(permission==4),"Native permission filtering changed");
            require(copy.getChild("zalias_alpha").getRedirect()==copy.getChild("alpha").getChild("leaf"),"First alias points to another subtree");
            require(copy.getChild("zalias_beta").getRedirect()==copy.getChild("beta").getChild("leaf"),"Second alias points to another subtree");
            var decoded=new ClientboundCommandsPacket(copy).getRoot(Commands.createValidationContext(server.registryAccess()));
            require(decoded.getChild("zalias_alpha").getRedirect()==decoded.getChild("alpha").getChild("leaf"),"First packet alias points to another subtree");
            require(decoded.getChild("zalias_beta").getRedirect()==decoded.getChild("beta").getChild("leaf"),"Second packet alias points to another subtree");
            require(decoded.getChild("alpha").getChild("leaf")!=decoded.getChild("beta").getChild("leaf"),"Packet collapsed structurally equal nodes");
        }
        require(executed.get()==0,"Command construction executed a command");
        return "PASS nativeLoginMappings="+loginMappings+" nativePacketMappings="+packetMappings+" permissions=0,4 redirects=2 packetRedirects=2 commandsExecuted=0";
    }
}
