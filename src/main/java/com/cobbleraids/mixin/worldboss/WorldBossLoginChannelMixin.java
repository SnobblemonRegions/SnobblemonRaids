package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.worldboss.WorldBossLoginPacer;
import com.cobbleraids.worldboss.WorldBossLoginProtocols;
import com.cobbleraids.fault.RaidFaultBarrier;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.channel.Channel;
import java.util.concurrent.CompletableFuture;
import net.minecraft.network.Connection;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.*;

/** Native final-join outbound configuration runs on its event loop before Main admission. */
@Mixin(Connection.class)
public abstract class WorldBossLoginChannelMixin implements WorldBossLoginPacer.ChannelAccess {
    @Shadow private Channel channel;
    @Unique private volatile WorldBossLoginProtocols.Prepared<ProtocolInfo<?>> cobbleRaids$outbound;
    @Unique private boolean cobbleRaids$localExecutors(){
        for(var entry:channel.pipeline()){
            var context=channel.pipeline().context(entry.getKey());
            if(context==null||context.executor()!=channel.eventLoop())return false;
        }
        return true;
    }
    @Override public ProtocolInfo<?> cobbleRaids$preparedOutbound(){
        var prepared=cobbleRaids$outbound;return prepared==null?null:prepared.protocolForMain();
    }
    @WrapMethod(method="setupOutboundProtocol")
    private void cobbleRaids$preparedSwitch(ProtocolInfo<?> protocol,Operation<Void> original){
        try{
            var prepared=cobbleRaids$outbound;
            if(prepared!=null&&protocol.id()==ConnectionProtocol.PLAY&&protocol.flow()==PacketFlow.CLIENTBOUND&&prepared.consume(protocol)){
                cobbleRaids$outbound=null;return;
            }
            original.call(protocol);
        }catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:login-protocol-switch",failure);throw failure;}
    }
    @Override public CompletableFuture<Void> cobbleRaids$loginBarrier(ProtocolInfo<?> outbound){
        var ready=new CompletableFuture<Void>();
        try{
            if(channel==null||!channel.isActive()){ready.completeExceptionally(new IllegalStateException("Login channel closed"));return ready;}
            if(outbound!=null&&((Object)this).getClass()==Connection.class&&outbound.id()==ConnectionProtocol.PLAY&&outbound.flow()==PacketFlow.CLIENTBOUND&&cobbleRaids$localExecutors()&&WorldBossLoginProtocols.compatible()){
                if(cobbleRaids$outbound!=null)return cobbleRaids$outbound.ready();
                cobbleRaids$outbound=WorldBossLoginProtocols.prepare(outbound,channel.eventLoop(),protocol->{
                    if(!channel.isActive())throw new IllegalStateException("Login channel closed before protocol setup");
                    if(!cobbleRaids$localExecutors())throw new IllegalStateException("Login pipeline executor changed during preparation");
                    ((Connection)(Object)this).setupOutboundProtocol(protocol);
                    if(!channel.isActive())throw new IllegalStateException("Login channel closed during protocol setup");
                });
                return cobbleRaids$outbound.ready();
            }
            WorldBossLoginProtocols.fallback();
            channel.eventLoop().submit(()->{}).addListener(done->{
                if(done.isSuccess())ready.complete(null);else ready.completeExceptionally(done.cause()==null?new IllegalStateException("Login barrier cancelled"):done.cause());
            });
        }catch(RuntimeException failure){ready.completeExceptionally(failure);}
        return ready;
    }
}
