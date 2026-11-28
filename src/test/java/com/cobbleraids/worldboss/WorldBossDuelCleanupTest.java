package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Exercise the actual adapter cleanup without creating a Minecraft world or battle engine. */
class WorldBossDuelCleanupTest {
    private static final UUID EVENT = new UUID(0, 1), PLAYER = new UUID(0, 2);

    private Object field(String name) throws Exception {
        var field = WorldBossService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    @SuppressWarnings("unchecked") private Map<UUID, Object> duels() throws Exception {
        return (Map<UUID, Object>) field("DUELS");
    }
    @SuppressWarnings("unchecked") private Map<UUID, UUID> players() throws Exception {
        return (Map<UUID, UUID>) field("DUEL_PLAYER");
    }

    private void admission(WorldBossAdmission queue) throws Exception {
        var field = WorldBossService.class.getDeclaredField("admissions");
        field.setAccessible(true);
        field.set(null, queue);
    }

    private Object bind(WorldBossAdmission.Token token) throws Exception {
        Class<?> type = Class.forName(WorldBossService.class.getName() + "$Duel");
        Constructor<?> constructor = type.getDeclaredConstructor(UUID.class, UUID.class, long.class, WorldBossAdmission.Token.class, Set.class);
        constructor.setAccessible(true);
        Object binding = constructor.newInstance(token.encounterId(), EVENT, 0L, token, Set.of());
        duels().put(PLAYER, binding);
        players().put(token.encounterId(), PLAYER);
        return binding;
    }

    private void cleanup(Object binding) throws Exception {
        Method method = WorldBossService.class.getDeclaredMethod("releaseEndedDuel", UUID.class, binding.getClass());
        method.setAccessible(true);
        method.invoke(null, PLAYER, binding);
    }

    @AfterEach void reset() throws Exception {
        duels().clear();
        players().clear();
        admission(null);
    }

    @Test void missingEndCallbackCannotStrandAnAdmissionSlotOrEncounterBinding() throws Exception {
        var queue = new WorldBossAdmission(EVENT, 1, 15_000, Set.of());
        admission(queue);
        queue.ready(PLAYER, true);
        var token = queue.reserve(1, 0, true, Set.of(PLAYER)).getFirst();
        queue.started(token);
        Object binding = bind(token);
        cleanup(binding);
        assertEquals(0, queue.occupied());
        assertTrue(duels().isEmpty());
        assertTrue(players().isEmpty());
        cleanup(binding);
        assertEquals(0, queue.occupied());
    }

    @Test void delayedCleanupCannotRemoveThePlayersNewDuel() throws Exception {
        var queue = new WorldBossAdmission(EVENT, 1, 15_000, Set.of());
        admission(queue);
        queue.ready(PLAYER, true);
        var old = queue.reserve(1, 0, true, Set.of(PLAYER)).getFirst();
        queue.started(old);
        Object oldBinding = bind(old);
        cleanup(oldBinding);
        queue.ready(PLAYER, true);
        var current = queue.reserve(1, Long.MAX_VALUE, true, Set.of(PLAYER)).getFirst();
        queue.started(current);
        Object currentBinding = bind(current);
        cleanup(oldBinding);
        assertSame(currentBinding, duels().get(PLAYER));
        assertEquals(PLAYER, players().get(current.encounterId()));
        assertTrue(queue.owns(current));
    }
}
