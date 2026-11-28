package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WorldBossNaturalSpawnEvaluationTest {
    @Test void onlyTheExactCandidateIsBlockedAndSuppressionIsReportedOncePerPass() {
        Object candidate = new Object();
        assertFalse(WorldBossNaturalSpawnEvaluation.blocks(candidate));
        try (var scope = WorldBossNaturalSpawnEvaluation.open(candidate, true)) {
            assertFalse(scope.suppressed());
            assertFalse(WorldBossNaturalSpawnEvaluation.blocks(new Object()));
            assertFalse(scope.suppressed());
            assertTrue(WorldBossNaturalSpawnEvaluation.blocks(candidate));
            assertTrue(WorldBossNaturalSpawnEvaluation.blocks(candidate));
            assertTrue(scope.suppressed());
        }
        assertFalse(WorldBossNaturalSpawnEvaluation.blocks(candidate));
    }
    @Test void nestedOutsideCandidatesCannotInheritTheOuterProtectedDecision() {
        Object outer = new Object(), inner = new Object();
        try (var first = WorldBossNaturalSpawnEvaluation.open(outer, true)) {
            try (var second = WorldBossNaturalSpawnEvaluation.open(inner, false)) {
                assertFalse(WorldBossNaturalSpawnEvaluation.blocks(inner));
                assertFalse(WorldBossNaturalSpawnEvaluation.blocks(outer));
                assertFalse(second.suppressed());
            }
            assertTrue(WorldBossNaturalSpawnEvaluation.blocks(outer));
        }
    }
    @Test void failureInNativeMatchingCannotLeakProtectionIntoAnotherPass() {
        Object candidate = new Object();
        assertThrows(IllegalStateException.class, () -> {
            try (var scope = WorldBossNaturalSpawnEvaluation.open(candidate, true)) {
                assertTrue(WorldBossNaturalSpawnEvaluation.blocks(candidate));
                throw new IllegalStateException("native match failed");
            }
        });
        assertFalse(WorldBossNaturalSpawnEvaluation.blocks(candidate));
    }
}
