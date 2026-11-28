package com.cobbleraids.worldboss;

import java.util.HashSet;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.advancements.critereon.CriterionValidator;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldBossAdvancementListenersTest {
    @org.junit.jupiter.api.BeforeAll static void nativeRegistries(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    private static final class Trigger implements CriterionTriggerInstance {
        private final String value;
        private int hashes;
        Trigger(String value){this.value=value;}
        @Override public void validate(CriterionValidator ignored){}
        @Override public int hashCode(){hashes++;return value.hashCode();}
        @Override public boolean equals(Object other){return other instanceof Trigger trigger&&value.equals(trigger.value);}
    }
    private static AdvancementHolder holder(String id){return new AdvancementHolder(ResourceLocation.parse("fixture:"+id),null);}
    private static CriterionTrigger.Listener<Trigger> listener(Trigger trigger,String id,String criterion){return new CriterionTrigger.Listener<>(trigger,holder(id),criterion);}
    private record Key(CriterionTrigger.Listener<?> listener){
        @Override public int hashCode(){return WorldBossAdvancementListeners.hash(listener);}
    }
    @Test void hashingNeverVisitsCriterionPredicates(){
        var trigger=new Trigger("expensive predicate");var listener=listener(trigger,"first","criterion");
        for(int i=0;i<100;i++)WorldBossAdvancementListeners.hash(listener);
        assertEquals(0,trigger.hashes,"Predicate trees must not be hashed during listener enrollment");
    }
    @Test void differentTriggersCanShareABucketWithoutBeingCollapsed(){
        var first=listener(new Trigger("first"),"same","criterion");
        var second=listener(new Trigger("second"),"same","criterion");
        assertNotEquals(first,second);
        assertEquals(WorldBossAdvancementListeners.hash(first),WorldBossAdvancementListeners.hash(second));
        var set=new HashSet<Key>();assertTrue(set.add(new Key(first)));assertTrue(set.add(new Key(second)));
        assertFalse(set.add(new Key(listener(new Trigger("first"),"same","criterion"))));assertEquals(2,set.size());
        assertTrue(set.remove(new Key(listener(new Trigger("first"),"same","criterion"))));
        assertEquals(new HashSet<>(java.util.List.of(new Key(second))),set);
    }
    @Test void reloadHoldersAndNullFieldsPreserveNativeEqualityContract(){
        var first=listener(new Trigger("same"),"reload","criterion");
        var reloaded=listener(new Trigger("same"),"reload","criterion");
        assertEquals(first,reloaded);assertEquals(WorldBossAdvancementListeners.hash(first),WorldBossAdvancementListeners.hash(reloaded));
        var empty=new CriterionTrigger.Listener<Trigger>(null,null,null);
        assertEquals(WorldBossAdvancementListeners.hash(empty),WorldBossAdvancementListeners.hash(new CriterionTrigger.Listener<Trigger>(null,null,null)));
        assertNotEquals(WorldBossAdvancementListeners.hash(first),WorldBossAdvancementListeners.hash(listener(new Trigger("same"),"different","criterion")));
    }
}
