package com.cobbleraids.validation.perf;

import java.util.HashSet;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.advancements.critereon.CriterionValidator;
import net.minecraft.server.MinecraftServer;

/** Post-timing local collections only: never registers a listener or grants player progress. */
final class NativeAdvancementHashCheck {
    private record Trigger(String value) implements CriterionTriggerInstance {
        @Override public void validate(CriterionValidator ignored){}
        @Override public int hashCode(){throw new IllegalStateException("Native listener still hashes predicate trees");}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    static String run(MinecraftServer server){
        if(!server.isSameThread())throw new IllegalStateException("Listener fixture requires Main");
        var holders=server.getAdvancements().getAllAdvancements();
        var holder=holders.iterator().next();
        var first=new CriterionTrigger.Listener<>(new Trigger("first"),holder,"fixture");
        var second=new CriterionTrigger.Listener<>(new Trigger("second"),holder,"fixture");
        require(first.hashCode()==second.hashCode(),"Predicate-free listener buckets are not installed");
        var fixtures=new HashSet<CriterionTrigger.Listener<Trigger>>();
        require(fixtures.add(first)&&fixtures.add(second)&&fixtures.size()==2,"Unequal triggers collapsed");
        var equivalent=new CriterionTrigger.Listener<>(new Trigger("first"),new AdvancementHolder(holder.id(),holder.value()),"fixture");
        require(!fixtures.add(equivalent)&&fixtures.remove(equivalent)&&fixtures.contains(second)&&fixtures.size()==1,"Native equality/removal contract changed");
        var listeners=new HashSet<CriterionTrigger.Listener<?>>();int count=0;
        for(var advancement:holders)for(var criterion:advancement.value().criteria().entrySet()){
            require(++count<=100_000,"Advancement fixture exceeds budget");
            var listener=new CriterionTrigger.Listener<>(criterion.getValue().triggerInstance(),advancement,criterion.getKey());
            require(listeners.add(listener),"Distinct native criterion collapsed");
            var copy=new CriterionTrigger.Listener<>(criterion.getValue().triggerInstance(),new AdvancementHolder(advancement.id(),advancement.value()),criterion.getKey());
            require(!listeners.add(copy)&&listeners.contains(copy),"Equivalent native criterion did not deduplicate");
        }
        require(count>0&&listeners.size()==count,"No actual native criteria were exercised");
        for(var advancement:holders)for(var criterion:advancement.value().criteria().entrySet())
            require(listeners.remove(new CriterionTrigger.Listener<>(criterion.getValue().triggerInstance(),advancement,criterion.getKey())),"Reconstructed native criterion removal failed");
        require(listeners.isEmpty(),"Native criteria remain after removal");
        return "PASS fixtures=collision,deduplication,reload,removal predicateHashCalls=0 nativeCriteria="+count;
    }
}
