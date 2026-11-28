package com.cobbleraids.worldboss;

import com.cobbleraids.RaidLog;
import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobblemon.mod.common.api.molang.function.PokedexMoLangFunctions;
import com.cobblemon.mod.common.api.molang.function.EntityMoLangFunctions;
import com.cobblemon.mod.common.api.molang.function.LivingEntityMoLangFunctions;
import com.cobblemon.mod.common.api.molang.function.PlayerMoLangFunctions;
import com.cobblemon.mod.common.api.molang.function.PokemonStoreMoLangFunctions;
import com.cobblemon.mod.common.api.molang.function.PartyMoLangFunctions;
import com.cobblemon.mod.common.api.molang.function.WorldMoLangFunctions;
import com.cobblemon.mod.common.api.molang.MoLangFunctions;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.cobblemon.mod.common.entity.ai.OmniPathNavigation;
import com.cobblemon.mod.common.entity.pokemon.ai.tasks.FindRestingPlaceTask;
import com.cobblemon.mod.common.pokemon.ai.OmniPathNodeMaker;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.commands.SharedSuggestionProvider;
import com.mojang.brigadier.tree.RootCommandNode;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.GameProfileCache;

/** Native startup preparation, including current-state profile-cache writes; no synthetic player/entity. */
final class WorldBossLoginWarmup {
    private static boolean codecs;
    private static int templates;
    private static int profileCachePrimingCalls;
    private static boolean dexFunctions,pathNodes,navigationContext,commandPacket,restingControl,entityMap,playerFunctionHolders,loginTypes;
    private static boolean partyFunctions,worldFunctions;
    private WorldBossLoginWarmup(){}
    static void prepare(MinecraftServer server){
        codecs=false;templates=0;profileCachePrimingCalls=0;dexFunctions=false;pathNodes=false;navigationContext=false;commandPacket=false;restingControl=false;entityMap=false;playerFunctionHolders=false;loginTypes=false;long start=System.nanoTime();
        partyFunctions=false;worldFunctions=false;
        try{if(server.isDedicatedServer())profileCachePrimingCalls=primeProfileCache(server.getProfileCache());}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:login-profile-cache-priming",failure);}
        try{
            GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(server.registryAccess()));
            GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(server.registryAccess()));
            codecs=true;
        }catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:login-codec-warmup",failure);}
        try{
            // Link Fabric's native advancement-owner type check without initializing or creating a fake player.
            boolean fakeType=net.fabricmc.fabric.api.entity.FakePlayer.class.getSuperclass()==net.minecraft.server.level.ServerPlayer.class;
            net.minecraft.world.entity.RelativeMovement.values();
            new net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket(0);
            var batchStart=net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket.INSTANCE;
            loginTypes=fakeType&&batchStart!=null;
        }catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:login-type-warmup",failure);}
        // Initialize native metadata holders only. Do not attach functions to a fabricated actor
        // or invoke custom providers; each inspected singleton constructor only allocates a list.
        try{
            EntityMoLangFunctions.INSTANCE.getCustom();LivingEntityMoLangFunctions.INSTANCE.getCustom();PlayerMoLangFunctions.INSTANCE.getCustom();
            playerFunctionHolders=true;
        }catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:login-player-function-holder-warmup",failure);}
        // These constructors allocate only local function maps/node caches. No player, Pokemon,
        // persistent/registered store, scheduler, navigation world, event or file is created.
        try{
            // A custom function provider may require a real player-owned manager: never invoke it here.
            if(PokedexMoLangFunctions.INSTANCE.getCustom().isEmpty()){
                // The concrete DTO links player_id/see/catch bindings skipped by an abstract manager.
                // Its constructor only retains this UUID and local map. Nothing is published and
                // none of the bound functions is executed, so no player lookup or progress occurs.
                new PokedexManager(new java.util.UUID(0,0),new java.util.LinkedHashMap<>()).getStruct();dexFunctions=true;
            }
        }catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:login-dex-function-warmup",failure);}
        try{partyFunctions=primePartyFunctions();}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:login-party-function-warmup",failure);}
        try{
            // Bind closures against the actual overworld; do not execute any world function.
            if(WorldMoLangFunctions.INSTANCE.getCustom().isEmpty()){
                MoLangFunctions.INSTANCE.asWorldMoLangValue(net.minecraft.core.Holder.direct(server.overworld()));worldFunctions=true;
            }
        }catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:login-world-function-warmup",failure);}
        try{new OmniPathNodeMaker();pathNodes=true;}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:login-path-node-warmup",failure);}
        try{new OmniPathNavigation.NavigationContext();navigationContext=true;}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:login-navigation-context-warmup",failure);}
        // Unbound data/control objects only. No packet is sent, behavior started or entity added.
        try{new ClientboundCommandsPacket(new RootCommandNode<SharedSuggestionProvider>());commandPacket=true;}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:login-command-packet-warmup",failure);}
        try{FindRestingPlaceTask.INSTANCE.create(0,0);restingControl=true;}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:login-resting-control-warmup",failure);}
        try{
            var empty=new it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap<>();
            new it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap<>(empty);entityMap=true;
        }catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:login-entity-map-warmup",failure);}
        // Optional Towers assets use the same native manager in SlicedBuild.start. No cell is allocated or pasted here.
        try(var available=server.getStructureManager().listTemplates()){
            available.filter(id->id.getNamespace().equals("cobbletowers")).distinct().limit(16).forEach(id->{
                try{if(server.getStructureManager().get(id).isPresent())templates++;}
                catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:login-template-warmup",failure);}
            });
        }catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:login-template-list",failure);}
        RaidLog.info("Native login caches prepared during startup: {}, ms={}",diagnostics(),(System.nanoTime()-start)/1_000_000);
    }
    static int primeProfileCache(GameProfileCache cache){
        if(cache==null||cache.getClass()!=GameProfileCache.class)return 0;
        // Use native current-state serialization. A create followed by reopen is paid during
        // startup; every ordinary login still runs its original synchronous add/save afterward.
        cache.save();cache.save();return 2;
    }
    static boolean primePartyFunctions(){
        // Audited 1.8.2 constructors allocate only six empty slots, an observable and function
        // maps. Never initialize/register the store, look up its owner, or invoke its bindings.
        // Both attached holders can have extensions requiring a real owner: skip all if present.
        if(!PokemonStoreMoLangFunctions.INSTANCE.getCustom().isEmpty()||!PartyMoLangFunctions.INSTANCE.getCustom().isEmpty())return false;
        new PlayerPartyStore(new java.util.UUID(0,0)).getStruct();return true;
    }
    static String diagnostics(){return "codecs="+codecs+", templates="+templates+", dexFunctions="+dexFunctions+", pathNodes="+pathNodes+", navigationContext="+navigationContext+", commandPacket="+commandPacket+", restingControl="+restingControl+", entityMap="+entityMap+", playerFunctionHolders="+playerFunctionHolders+", loginTypes="+loginTypes+", profileCachePrimingCalls="+profileCachePrimingCalls+", partyFunctions="+partyFunctions+", worldFunctions="+worldFunctions;}
}
