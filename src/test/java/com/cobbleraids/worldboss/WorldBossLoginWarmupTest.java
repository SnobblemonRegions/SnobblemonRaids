package com.cobbleraids.worldboss;

import com.cobblemon.mod.common.api.molang.function.PartyMoLangFunctions;
import com.cobblemon.mod.common.api.molang.function.PokemonStoreMoLangFunctions;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldBossLoginWarmupTest {
    @Test void nativeEmptyPartyBindingsNeedNoRunningServerOrRegisteredOwner(){
        assertTrue(WorldBossLoginWarmup.primePartyFunctions());
        var owner=new java.util.UUID(0,0);
        var store=new PlayerPartyStore(owner);
        assertEquals(owner,store.getPlayerUUID());assertEquals(owner,store.getUuid());
        assertTrue(store.getObserverUUIDs().isEmpty());assertFalse(store.iterator().hasNext());
        assertSame(store,store.getStruct().getObj());
    }
    @Test void customStoreProviderIsNeverInvokedForStartupDto(){
        var providers=PokemonStoreMoLangFunctions.INSTANCE.getCustom();
        providers.add(store->{throw new AssertionError("custom store provider invoked without its owner");});
        try{assertFalse(WorldBossLoginWarmup.primePartyFunctions());}finally{providers.removeLast();}
    }
    @Test void customPartyProviderIsNeverInvokedForStartupDto(){
        var providers=PartyMoLangFunctions.INSTANCE.getCustom();
        providers.add(store->{throw new AssertionError("custom party provider invoked without its owner");});
        try{assertFalse(WorldBossLoginWarmup.primePartyFunctions());}finally{providers.removeLast();}
    }
}
