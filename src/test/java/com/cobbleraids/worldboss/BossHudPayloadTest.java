package com.cobbleraids.worldboss;

import com.cobbleraids.visual.protocol.BossHudPayload;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossHudPayloadTest {
    @Test void boundedSnapshotPreservesExactNativeBossIdentity() {
        var source = new BossHudPayload(42,true,new UUID(3,7),"minecraft:overworld","Giratina III","GATHERING / 12 READY",8000,10000,2,true);
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            BossHudPayload.CODEC.encode(buffer,source);
            assertTrue(buffer.readableBytes()<256);
            assertEquals(source,BossHudPayload.CODEC.decode(buffer));
            assertEquals(0,buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test void invalidHealthAndUnboundedTextAreRejected() {
        assertThrows(IllegalArgumentException.class,()->payload(Double.NaN,100,"Giratina"));
        assertThrows(IllegalArgumentException.class,()->payload(101,100,"Giratina"));
        assertThrows(IllegalArgumentException.class,()->payload(-1,100,"Giratina"));
        assertThrows(IllegalArgumentException.class,()->payload(100,100,"x".repeat(129)));
    }
    private BossHudPayload payload(double health,double max,String title) {
        return new BossHudPayload(1,true,new UUID(0,1),"minecraft:overworld",title,"ACTIVE",health,max,1,false);
    }
}
