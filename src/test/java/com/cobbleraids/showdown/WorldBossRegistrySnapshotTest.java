package com.cobbleraids.showdown;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class WorldBossRegistrySnapshotTest {
    @Test void snapshotPreservesRawFunctionsAndDetachesCallerMaps() {
        var snapshot = new WorldBossRegistrySnapshot(); var data = new HashMap<String, String>();
        String function = "{id:'test', onHit() { return 17; }}"; data.put("test", function);
        snapshot.record("sendRegistryData", "move", data, null); snapshot.speciesReady(); var frozen = snapshot.freeze();
        data.put("test", "changed"); snapshot.record("resetRegistryData", "move", null, null);
        assertEquals(1, frozen.size()); assertEquals(function, frozen.getFirst().data().get("test"));
        assertThrows(UnsupportedOperationException.class, () -> frozen.getFirst().data().clear());
    }
    @Test void resetAllRequiresFreshSpeciesReadinessAndRemovesPriorGeneration() {
        var snapshot = new WorldBossRegistrySnapshot();
        assertThrows(IllegalStateException.class, snapshot::freeze);
        snapshot.record("sendRegistryData", "species", Map.of("old", "{}"), null); snapshot.speciesReady();
        snapshot.record("resetAllRegistries", null, null, null); assertThrows(IllegalStateException.class, snapshot::freeze);
        snapshot.record("sendRegistryData", "species", Map.of("new", "{}"), null); snapshot.speciesReady();
        assertEquals(2, snapshot.freeze().size()); assertEquals(Set.of("new"), snapshot.freeze().getLast().data().keySet());
    }
    @Test void replayCannotGrowBeyondItsOperationBudget() {
        var snapshot = new WorldBossRegistrySnapshot();
        for (int n = 0; n < 20_000; n++) snapshot.record("resetRegistryData", "move", null, null);
        assertThrows(IllegalStateException.class, () -> snapshot.record("resetRegistryData", "move", null, null));
    }
}
