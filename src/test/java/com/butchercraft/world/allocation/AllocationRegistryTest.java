package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AllocationRegistryTest {
    @Test
    void registryCanonicalizesAllDefinitionFamiliesAndBuildsIndexes() {
        AllocationTestFixtures.AllocationGraph zeta = AllocationTestFixtures.graph("zeta");
        AllocationTestFixtures.AllocationGraph alpha = AllocationTestFixtures.graph("alpha");
        AllocationRegistry registry = AllocationRegistry.builder()
                .registerRequirement(zeta.requirement())
                .registerRequest(zeta.request())
                .registerSet(zeta.set())
                .registerCommitment(zeta.commitment())
                .registerRequirement(alpha.requirement())
                .registerRequest(alpha.request())
                .registerSet(alpha.set())
                .registerCommitment(alpha.commitment())
                .build();

        assertEquals(
                registry.requirements().stream().sorted().toList(),
                registry.requirements()
        );
        assertEquals(
                registry.requests().stream()
                        .sorted(java.util.Comparator.comparing(
                                AllocationRequestDefinition::id
                        ))
                        .toList(),
                registry.requests()
        );
        assertEquals(2, registry.requirementCount());
        assertEquals(2, registry.requestCount());
        assertEquals(2, registry.setCount());
        assertEquals(2, registry.commitmentCount());
        assertEquals(
                List.of(alpha.set()),
                registry.findSetsByRequest(alpha.request().id())
        );
        assertEquals(
                List.of(alpha.commitment()),
                registry.findCommitmentsByResource(alpha.commitment().resourceId())
        );
    }

    @Test
    void registryRejectsDuplicateAndUnknownReferences() {
        AllocationTestFixtures.AllocationGraph graph = AllocationTestFixtures.graph("duplicate");
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.DUPLICATE_REGISTRATION,
                () -> AllocationRegistry.builder()
                        .registerRequirement(graph.requirement())
                        .registerRequirement(graph.requirement())
        );
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.UNKNOWN_SET,
                () -> AllocationRegistry.builder()
                        .registerRequirement(graph.requirement())
                        .build()
        );
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.UNKNOWN_REQUEST,
                () -> AllocationRegistry.builder()
                        .registerRequirement(graph.requirement())
                        .registerSet(graph.set())
                        .build()
        );
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.UNKNOWN_REQUIREMENT,
                () -> AllocationRegistry.builder()
                        .registerRequest(graph.request())
                        .registerSet(graph.set())
                        .build()
        );
    }

    @Test
    void registryAndRuntimeRegistryViewsAreImmutable() {
        AllocationTestFixtures.AllocationGraph graph = AllocationTestFixtures.graph("immutable");
        assertThrows(
                UnsupportedOperationException.class,
                () -> graph.definitions().sets().add(graph.set())
        );
        AllocationRuntimeView view = requestedView(graph.set());
        List<AllocationRuntimeView> mutable = new ArrayList<>(List.of(view));
        AllocationRuntimeRegistry registry = AllocationRuntimeRegistry.of(mutable);
        mutable.clear();
        assertEquals(List.of(view), registry.views());
        assertThrows(
                UnsupportedOperationException.class,
                () -> registry.views().add(view)
        );
    }

    @Test
    void runtimeRegistryRejectsDuplicatesAndOrdersBySetIdentity() {
        AllocationTestFixtures.AllocationGraph first = AllocationTestFixtures.graph("first");
        AllocationTestFixtures.AllocationGraph second = AllocationTestFixtures.graph("second");
        AllocationRuntimeView firstView = requestedView(first.set());
        AllocationRuntimeView secondView = requestedView(second.set());
        AllocationRuntimeRegistry registry = AllocationRuntimeRegistry.of(
                List.of(secondView, firstView)
        );

        assertEquals(
                registry.views().stream().sorted().toList(),
                registry.views()
        );
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.DUPLICATE_RUNTIME,
                () -> AllocationRuntimeRegistry.of(List.of(firstView, firstView))
        );
    }

    private static AllocationRuntimeView requestedView(AllocationSetDefinition set) {
        return AllocationSetRuntime.requested(set, AllocationMetadata.empty()).snapshot();
    }
}
