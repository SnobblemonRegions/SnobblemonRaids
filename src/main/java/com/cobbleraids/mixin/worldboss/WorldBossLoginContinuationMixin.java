package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossLoginPacer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.Connection;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.config.JoinWorldTask;
import java.util.Queue;
import java.util.function.Function;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

/** Native admission checks, protocol setup and all other mods' callbacks run once, on Main, after readiness. */
@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class WorldBossLoginContinuationMixin extends ServerCommonPacketListenerImpl {
    @Shadow @Final private Queue<ConfigurationTask> configurationTasks;
    @Shadow private ConfigurationTask currentTask;
    protected WorldBossLoginContinuationMixin(MinecraftServer server,Connection connection,CommonListenerCookie cookie){super(server,connection,cookie);}
    @Unique private boolean cobbleRaids$lastJoinTask(){
        return ((Object)this).getClass()==ServerConfigurationPacketListenerImpl.class&&currentTask!=null
                &&currentTask.type().equals(JoinWorldTask.TYPE)&&configurationTasks.isEmpty();
    }
    @WrapOperation(method="handleConfigurationFinished",at=@At(value="INVOKE",target="Lnet/minecraft/network/ProtocolInfo$Unbound;bind(Ljava/util/function/Function;)Lnet/minecraft/network/ProtocolInfo;"))
    private ProtocolInfo<?> cobbleRaids$preparedBinding(ProtocolInfo.Unbound<?,?> template,Function<?,?> decorator,Operation<ProtocolInfo<?>> original){
        try{
            var prepared=((WorldBossLoginPacer.ChannelAccess)connection).cobbleRaids$preparedOutbound();
            if(prepared==null)return original.call(template,decorator);
            if(template!=GameProtocols.CLIENTBOUND_TEMPLATE)throw new IllegalStateException("Final native game protocol template changed");
            return prepared;
        }catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:login-protocol-binding",failure);throw failure;}
    }
    @WrapMethod(method="handleConfigurationFinished")
    private void cobbleRaids$finishLogin(ServerboundFinishConfigurationPacket packet,Operation<Void> original){
        if(server.isSameThread()){
            try{
                boolean prepare=cobbleRaids$lastJoinTask();
                if(WorldBossLoginPacer.defer(server,connection,this,playerProfile().getId(),prepare,()->{
                    if(prepare&&!cobbleRaids$lastJoinTask())throw new IllegalStateException("Final login task changed during protocol preparation");
                    original.call(packet);
                }))return;
            }
            catch(RuntimeException failure){
                RaidFaultBarrier.report("worldboss:login-continuation",failure);
                try{connection.disconnect(Component.literal("Login could not finish. Please reconnect."));}
                catch(RuntimeException disconnect){RaidFaultBarrier.report("worldboss:login-disconnect",disconnect);}
                return;
            }
        }
        original.call(packet); // Native PacketUtils still dispatches off-thread arrivals to Main.
    }
}
