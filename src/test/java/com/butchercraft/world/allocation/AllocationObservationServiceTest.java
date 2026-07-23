package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationObservationServiceTest {
    @Test
    void emptyRegistryProducesACompleteEmptyBundle() {
        AllocationObservationOperationResult operation =
                new AllocationObservationService(AllocationProviderRegistry.empty())
                        .observe(AllocationProviderFixtures.request());

        assertTrue(operation.accepted());
        AllocationObservationBundle bundle = operation.bundle().orElseThrow();
        assertEquals(AllocationObservationBundleStatus.COMPLETE, bundle.status());
        assertTrue(bundle.usableForAllocationCycle());
        assertEquals(List.of(), bundle.providerIds());
        assertEquals(List.of(), bundle.resources());
        assertEquals(List.of(), bundle.capacities());
        assertEquals(64, bundle.canonicalDigest().length());
    }

    @Test
    void providersAreInvokedAndAggregatedInCanonicalIdentityOrder() {
        List<String> invocationOrder = new ArrayList<>();
        var providerB = recordingProvider("b", invocationOrder);
        var providerA = recordingProvider("a", invocationOrder);
        AllocationProviderRegistry registry = AllocationProviderRegistry.builder()
                .register(providerB)
                .register(providerA)
                .build();

        AllocationObservationBundle bundle = new AllocationObservationService(registry)
                .observe(AllocationProviderFixtures.request())
                .bundle()
                .orElseThrow();

        assertEquals(List.of("test:provider_a", "test:provider_b"), invocationOrder);
        assertEquals(
                List.of("test:resource_a", "test:resource_b"),
                bundle.resources().stream()
                        .map(resource -> resource.resourceId().value())
                        .toList()
        );
        assertEquals(2, bundle.report().summary().successfulProviderCount());
        assertEquals(AllocationObservationBundleStatus.COMPLETE, bundle.status());
    }

    @Test
    void providerLocalFailureIsIsolatedButMakesCompletenessExplicit() {
        var successful = AllocationProviderFixtures.provider(
                "success",
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                CapacityUnits.MACHINE_TIME
        );
        AllocationProviderDescriptor failedDescriptor =
                AllocationProviderFixtures.descriptor(
                        "failed",
                        "test:owner_failed",
                        ResourceCategories.PRODUCTION,
                        AllocationProviderFixtures.MACHINE_TIME,
                        CapacityUnits.MACHINE_TIME
                );
        var failed = AllocationProviderFixtures.provider(
                failedDescriptor,
                context -> AllocationObservationResult.failure(
                        failedDescriptor.providerId(),
                        context.simulationTick(),
                        List.of(AllocationProviderFailure.provider(
                                AllocationProviderFailureCode.UNKNOWN_REFERENCE,
                                AllocationProviderFailureScope.PROVIDER,
                                failedDescriptor.providerId(),
                                "test:source",
                                "Authoritative provider source is unavailable",
                                context.simulationTick()
                        ))
                )
        );
        AllocationProviderRegistry registry = AllocationProviderRegistry.builder()
                .register(successful)
                .register(failed)
                .build();

        AllocationObservationBundle bundle = new AllocationObservationService(registry)
                .observe(AllocationProviderFixtures.request())
                .bundle()
                .orElseThrow();

        assertEquals(AllocationObservationBundleStatus.INCOMPLETE, bundle.status());
        assertFalse(bundle.usableForAllocationCycle());
        assertEquals(1, bundle.resources().size());
        assertEquals("test:resource_success", bundle.resources().getFirst()
                .resourceId().value());
        assertEquals(1, bundle.failures().size());
        assertEquals(1, successful.invocationCount());
        assertEquals(1, failed.invocationCount());
    }

    @Test
    void providerExceptionBecomesStableTypedEvidenceAndIsNeverRetried() {
        AllocationProviderDescriptor descriptor = AllocationProviderFixtures.descriptor(
                "throwing",
                "test:owner_throwing",
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                CapacityUnits.MACHINE_TIME
        );
        var provider = AllocationProviderFixtures.provider(descriptor, context -> {
            throw new IllegalStateException(
                    "identity@" + System.identityHashCode(context)
            );
        });
        AllocationProviderRegistry registry = AllocationProviderRegistry.builder()
                .register(provider)
                .build();

        AllocationObservationBundle bundle = new AllocationObservationService(registry)
                .observe(AllocationProviderFixtures.request())
                .bundle()
                .orElseThrow();

        assertEquals(1, provider.invocationCount());
        assertEquals(AllocationProviderFailureCode.PROVIDER_EXCEPTION,
                bundle.failures().getFirst().code());
        assertEquals(
                "Provider threw an unexpected exception during observation",
                bundle.failures().getFirst().message()
        );
        assertFalse(bundle.failures().getFirst().message().contains("identity@"));
    }

    @Test
    void malformedResultIsRejectedAsOneProviderContribution() {
        AllocationProviderDescriptor descriptor = AllocationProviderFixtures.descriptor(
                "wrong_tick",
                "test:owner_wrong_tick",
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                CapacityUnits.MACHINE_TIME
        );
        var provider = AllocationProviderFixtures.provider(
                descriptor,
                context -> AllocationObservationResult.success(
                        descriptor.providerId(),
                        context.simulationTick() + 1L,
                        List.of(),
                        List.of()
                )
        );

        AllocationObservationBundle bundle = new AllocationObservationService(
                AllocationProviderRegistry.builder().register(provider).build()
        ).observe(AllocationProviderFixtures.request()).bundle().orElseThrow();

        assertEquals(AllocationObservationBundleStatus.INCOMPLETE, bundle.status());
        assertEquals(AllocationProviderFailureCode.TICK_MISMATCH,
                bundle.failures().getFirst().code());
        assertEquals(List.of(), bundle.resources());
        assertEquals(List.of(), bundle.capacities());
    }

    @Test
    void requestFiltersAndDeclaredAuthorityAreEnforced() {
        AllocationProviderDescriptor descriptor = AllocationProviderFixtures.descriptor(
                "scope",
                "test:owner_scope",
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                CapacityUnits.MACHINE_TIME
        );
        var provider = AllocationProviderFixtures.provider(descriptor, context -> {
            ObservedResourceSnapshot resource = AllocationProviderFixtures.resource(
                    descriptor,
                    "scope",
                    ResourceCategories.PRODUCTION,
                    "test:other_owner",
                    context.simulationTick(),
                    ResourceExclusivityMode.SHARED
            );
            return AllocationObservationResult.success(
                    descriptor.providerId(),
                    context.simulationTick(),
                    List.of(resource),
                    List.of()
            );
        });
        AllocationObservationRequest request = new AllocationObservationRequest(
                new AllocationObservationContext(
                        AllocationProviderFixtures.TICK,
                        List.of(ResourceCategories.PRODUCTION),
                        List.of(AllocationProviderFixtures.MACHINE_TIME),
                        AllocationMetadata.empty(),
                        10,
                        10,
                        AllocationSchema.CURRENT_VERSION
                ),
                List.of(descriptor.providerId()),
                10,
                10,
                AllocationSchema.CURRENT_VERSION
        );

        AllocationObservationBundle bundle = new AllocationObservationService(
                AllocationProviderRegistry.builder().register(provider).build()
        ).observe(request).bundle().orElseThrow();

        assertEquals(AllocationProviderFailureCode.UNAUTHORIZED_OWNER_REFERENCE,
                bundle.failures().getFirst().code());
        assertEquals(AllocationObservationBundleStatus.INCOMPLETE, bundle.status());
    }

    @Test
    void unknownProviderSelectionRejectsBeforeAnyInvocation() {
        var provider = AllocationProviderFixtures.provider(
                "known",
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                CapacityUnits.MACHINE_TIME
        );
        AllocationObservationRequest request = new AllocationObservationRequest(
                AllocationObservationContext.allAtTick(
                        AllocationProviderFixtures.TICK
                ),
                List.of(AllocationProviderId.of("test:unknown")),
                10,
                10,
                AllocationSchema.CURRENT_VERSION
        );
        AllocationObservationOperationResult operation =
                new AllocationObservationService(
                        AllocationProviderRegistry.builder().register(provider).build()
                ).observe(request);

        assertFalse(operation.accepted());
        assertTrue(operation.bundle().isEmpty());
        assertEquals(AllocationProviderFailureCode.UNKNOWN_REFERENCE,
                operation.failures().getFirst().code());
        assertEquals(0, provider.invocationCount());
    }

    @Test
    void nullRequestRejectsBeforeAnyInvocation() {
        var provider = AllocationProviderFixtures.provider(
                "null_request",
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                CapacityUnits.MACHINE_TIME
        );
        AllocationObservationOperationResult operation =
                new AllocationObservationService(
                        AllocationProviderRegistry.builder().register(provider).build()
                ).observe(null);

        assertFalse(operation.accepted());
        assertEquals(AllocationProviderFailureCode.INVALID_REQUEST,
                operation.failures().getFirst().code());
        assertEquals(0, provider.invocationCount());
    }

    @Test
    void requestBoundsFailAProviderExplicitlyWithoutPartialInsertion() {
        var provider = AllocationProviderFixtures.provider(
                "bounded",
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                CapacityUnits.MACHINE_TIME
        );
        AllocationObservationContext context = new AllocationObservationContext(
                AllocationProviderFixtures.TICK,
                List.of(),
                List.of(),
                AllocationMetadata.empty(),
                1,
                1,
                AllocationSchema.CURRENT_VERSION
        );
        AllocationObservationRequest request = new AllocationObservationRequest(
                context,
                List.of(),
                1,
                1,
                AllocationSchema.CURRENT_VERSION
        );

        AllocationObservationBundle bundle = new AllocationObservationService(
                AllocationProviderRegistry.builder().register(provider).build()
        ).observe(request).bundle().orElseThrow();

        assertEquals(AllocationObservationBundleStatus.COMPLETE, bundle.status());
        assertEquals(1, bundle.resources().size());
        assertEquals(1, bundle.capacities().size());
    }

    @Test
    void totalBoundsRejectOneCompleteProviderContributionWithoutPartialData() {
        var providerB = AllocationProviderFixtures.provider(
                "b_total",
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                CapacityUnits.MACHINE_TIME
        );
        var providerA = AllocationProviderFixtures.provider(
                "a_total",
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                CapacityUnits.MACHINE_TIME
        );
        AllocationObservationRequest request = new AllocationObservationRequest(
                AllocationObservationContext.allAtTick(
                        AllocationProviderFixtures.TICK
                ),
                List.of(),
                1,
                1,
                AllocationSchema.CURRENT_VERSION
        );

        AllocationObservationBundle bundle = new AllocationObservationService(
                AllocationProviderRegistry.builder()
                        .register(providerB)
                        .register(providerA)
                        .build()
        ).observe(request).bundle().orElseThrow();

        assertEquals(AllocationObservationBundleStatus.INCOMPLETE, bundle.status());
        assertEquals(1, bundle.resources().size());
        assertEquals("test:resource_a_total", bundle.resources().getFirst()
                .resourceId().value());
        assertEquals(AllocationProviderFailureCode.RESULT_LIMIT_EXCEEDED,
                bundle.failures().getFirst().code());
        assertEquals(List.of(), bundle.providerResults().getLast().resources());
    }

    @Test
    void invalidSnapshotConstructionMapsToTypedDeterministicFailure() {
        AllocationProviderDescriptor descriptor = AllocationProviderFixtures.descriptor(
                "invalid_snapshot",
                "test:workforce",
                ResourceCategories.WORKFORCE,
                AllocationProviderFixtures.WORKFORCE_SLOT,
                CapacityUnits.WORKER_SLOT
        );
        var provider = AllocationProviderFixtures.provider(descriptor, context -> {
            new ObservedResourceSnapshot(
                    ResourceId.of("test:worker"),
                    ResourceCategories.WORKFORCE,
                    descriptor.providerId(),
                    ExternalReference.of(
                            "butchercraft:individual_worker",
                            "test:worker",
                            "test:workforce"
                    ),
                    ResourceAvailability.AVAILABLE,
                    ResourceExclusivityMode.EXCLUSIVE,
                    context.simulationTick(),
                    AllocationMetadata.empty(),
                    AllocationSchema.CURRENT_VERSION
            );
            throw new AssertionError("Snapshot construction should reject first");
        });

        AllocationObservationBundle bundle = new AllocationObservationService(
                AllocationProviderRegistry.builder().register(provider).build()
        ).observe(AllocationProviderFixtures.request()).bundle().orElseThrow();

        assertEquals(AllocationProviderFailureCode.UNSUPPORTED_SCHEMA_CONCEPT,
                bundle.failures().getFirst().code());
        assertEquals(
                "Provider constructed an invalid observation snapshot",
                bundle.failures().getFirst().message()
        );
    }

    private static AllocationProviderFixtures.TestProvider recordingProvider(
            String suffix,
            List<String> invocationOrder
    ) {
        AllocationProviderDescriptor descriptor = AllocationProviderFixtures.descriptor(
                suffix,
                "test:owner_" + suffix,
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                CapacityUnits.MACHINE_TIME
        );
        return AllocationProviderFixtures.provider(descriptor, context -> {
            invocationOrder.add(descriptor.providerId().value());
            ObservedResourceSnapshot resource = AllocationProviderFixtures.resource(
                    descriptor,
                    suffix,
                    ResourceCategories.PRODUCTION,
                    "test:owner_" + suffix,
                    context.simulationTick(),
                    ResourceExclusivityMode.SHARED
            );
            ObservedCapacitySnapshot capacity = AllocationProviderFixtures.capacity(
                    suffix,
                    resource.resourceId(),
                    AllocationProviderFixtures.MACHINE_TIME,
                    CapacityUnits.MACHINE_TIME,
                    "test:owner_" + suffix,
                    context.simulationTick(),
                    "1"
            );
            return AllocationObservationResult.success(
                    descriptor.providerId(),
                    context.simulationTick(),
                    List.of(resource),
                    List.of(capacity)
            );
        });
    }
}
