package com.cobbleraids.worldboss;

import net.minecraft.advancements.CriterionTrigger;

/** Listener bucketing must not traverse the full criterion predicate tree. Equality stays native. */
public final class WorldBossAdvancementListeners {
    private WorldBossAdvancementListeners(){}
    public static int hash(CriterionTrigger.Listener<?> listener){
        // Omitting trigger is legal: equal listeners have equal holders/names. Unequal triggers
        // sharing a bucket are still distinguished by the unchanged native record equals().
        return 31*java.util.Objects.hashCode(listener.advancement())+java.util.Objects.hashCode(listener.criterion());
    }
}
