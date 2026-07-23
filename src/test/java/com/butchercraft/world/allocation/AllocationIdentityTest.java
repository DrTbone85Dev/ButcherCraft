package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationIdentityTest {
    @Test
    void everyAllocationIdentityUsesTheSameCanonicalNamespacedContract() {
        List<Function<String, ?>> factories = List.of(
                AllocationCycleId::of,
                ResourceId::of,
                CapacityId::of,
                CapacityTypeId::of,
                CapacityUnitId::of,
                RequirementId::of,
                AllocationRequestId::of,
                AllocationSetId::of,
                AllocationCommitmentId::of,
                AllocationProviderId::of,
                AllocationPolicyId::of
        );

        for (Function<String, ?> factory : factories) {
            Object first = factory.apply("example:stable_id");
            Object second = factory.apply("example:stable_id");
            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
            assertTrue(first.toString().contains("example:stable_id"));
            AllocationTestFixtures.assertFailure(
                    AllocationValidationFailureCode.INVALID_NAMESPACE,
                    () -> factory.apply("missing_namespace")
            );
            AllocationTestFixtures.assertFailure(
                    AllocationValidationFailureCode.MALFORMED_IDENTIFIER,
                    () -> factory.apply("Example:UPPER")
            );
            AllocationTestFixtures.assertFailure(
                    AllocationValidationFailureCode.MALFORMED_IDENTIFIER,
                    () -> factory.apply(" example:space")
            );
        }
    }

    @Test
    void identityOrderingAndDerivationAreStableAndLocaleIndependent() {
        Locale original = Locale.getDefault();
        AllocationRequestId first;
        AllocationRequestId second;
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            first = AllocationIds.requestId(
                    AllocationTestFixtures.work("identity"),
                    AllocationTestFixtures.ordering("identity"),
                    AllocationSchema.CURRENT_VERSION
            );
            Locale.setDefault(Locale.JAPAN);
            second = AllocationIds.requestId(
                    AllocationTestFixtures.work("identity"),
                    AllocationTestFixtures.ordering("identity"),
                    AllocationSchema.CURRENT_VERSION
            );
        } finally {
            Locale.setDefault(original);
        }

        assertEquals(first, second);
        assertEquals(0, first.compareTo(second));
        assertTrue(ResourceId.of("example:a").compareTo(ResourceId.of("example:b")) < 0);
        assertNotEquals(
                first,
                AllocationIds.requestId(
                        AllocationTestFixtures.work("other"),
                        AllocationTestFixtures.ordering("other"),
                        AllocationSchema.CURRENT_VERSION
                )
        );
    }

    @Test
    void cycleIdentityCanBeDerivedFromAnExplicitSimulationTick() {
        assertEquals("butchercraft:allocation_cycle/tick_42", AllocationCycleId.forTick(42L).value());
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.INVALID_SIMULATION_TICK,
                () -> AllocationCycleId.forTick(-1L)
        );
    }
}
