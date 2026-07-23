package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationObservationModelTest {
    @Test
    void contextCanonicalizesFiltersAndResistsCallerMutation() {
        List<ResourceCategory> categories = new ArrayList<>(List.of(
                ResourceCategories.WORKFORCE,
                ResourceCategories.PRODUCTION
        ));
        List<CapacityTypeId> types = new ArrayList<>(List.of(
                AllocationProviderFixtures.WORKFORCE_SLOT,
                AllocationProviderFixtures.MACHINE_TIME
        ));
        AllocationObservationContext context = new AllocationObservationContext(
                AllocationProviderFixtures.TICK,
                categories,
                types,
                AllocationMetadata.of(Map.of(
                        "test:scope",
                        AllocationMetadataValue.identifier("test:regional")
                )),
                100,
                200,
                AllocationSchema.CURRENT_VERSION
        );
        categories.clear();
        types.clear();

        assertEquals(
                List.of(ResourceCategories.PRODUCTION, ResourceCategories.WORKFORCE)
                        .stream().sorted().toList(),
                context.requestedResourceCategories()
        );
        assertTrue(context.requests(ResourceCategories.PRODUCTION));
        assertFalse(context.requests(ResourceCategories.STORAGE));
        assertEquals(64, context.canonicalDigest().length());
        assertThrows(
                UnsupportedOperationException.class,
                () -> context.requestedResourceCategories().clear()
        );
    }

    @Test
    void contextAndRequestRejectInvalidBoundsDuplicatesAndSchemas() {
        assertThrows(
                AllocationValidationException.class,
                () -> AllocationObservationContext.allAtTick(-1L)
        );
        assertThrows(
                AllocationProviderValidationException.class,
                () -> new AllocationObservationContext(
                        1L,
                        List.of(ResourceCategories.PRODUCTION),
                        List.of(),
                        AllocationMetadata.empty(),
                        0,
                        1,
                        AllocationSchema.CURRENT_VERSION
                )
        );
        assertThrows(
                AllocationProviderValidationException.class,
                () -> new AllocationObservationRequest(
                        AllocationObservationContext.allAtTick(1L),
                        List.of(
                                AllocationProviderId.of("test:a"),
                                AllocationProviderId.of("test:a")
                        ),
                        1,
                        1,
                        AllocationSchema.CURRENT_VERSION
                )
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.INVALID_SCHEMA_VERSION,
                () -> new AllocationObservationContext(
                        1L,
                        List.of(),
                        List.of(),
                        AllocationMetadata.empty(),
                        1,
                        1,
                        2
                )
        );
    }

    @Test
    void successfulAndFailedProviderResultsHaveExplicitImmutableShapes() {
        var provider = AllocationProviderFixtures.provider(
                "model",
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                CapacityUnits.MACHINE_TIME
        );
        AllocationObservationResult success = provider.observe(
                AllocationObservationContext.allAtTick(
                        AllocationProviderFixtures.TICK
                )
        );
        AllocationProviderFailure failure = AllocationProviderFailure.provider(
                AllocationProviderFailureCode.UNKNOWN_REFERENCE,
                AllocationProviderFailureScope.PROVIDER,
                provider.id(),
                "test:subject",
                "Provider could not resolve its source",
                AllocationProviderFixtures.TICK
        );
        AllocationObservationResult failed = AllocationObservationResult.failure(
                provider.id(),
                AllocationProviderFixtures.TICK,
                List.of(failure)
        );

        assertTrue(success.successful());
        assertFalse(failed.successful());
        assertEquals(List.of(), failed.resources());
        assertEquals(64, success.canonicalDigest().length());
        assertEquals(64, failure.canonicalDigest().length());
        assertThrows(
                UnsupportedOperationException.class,
                () -> success.resources().clear()
        );
    }

    @Test
    void failedResultCannotMasqueradeAsPartialSuccess() {
        AllocationProviderId providerId = AllocationProviderId.of("test:provider");
        AllocationProviderFailure failure = AllocationProviderFailure.provider(
                AllocationProviderFailureCode.UNKNOWN_REFERENCE,
                AllocationProviderFailureScope.PROVIDER,
                providerId,
                "test:subject",
                "Provider source is unavailable",
                AllocationProviderFixtures.TICK
        );
        ObservedResourceSnapshot resource = new ObservedResourceSnapshot(
                ResourceId.of("test:resource"),
                ResourceCategories.PRODUCTION,
                providerId,
                ExternalReference.of(
                        "butchercraft:resource_observation",
                        "test:resource",
                        "test:owner"
                ),
                ResourceAvailability.AVAILABLE,
                ResourceExclusivityMode.SHARED,
                AllocationProviderFixtures.TICK,
                AllocationMetadata.empty(),
                AllocationSchema.CURRENT_VERSION
        );

        assertThrows(
                AllocationProviderValidationException.class,
                () -> new AllocationObservationResult(
                        providerId,
                        AllocationProviderFixtures.TICK,
                        List.of(resource),
                        List.of(),
                        List.of(failure),
                        List.of(),
                        AllocationSchema.CURRENT_VERSION
                )
        );
    }

    @Test
    void failureAndWarningEvidenceIsCanonicalAndStackFree() {
        AllocationProviderId providerId = AllocationProviderId.of("test:provider");
        AllocationProviderWarning warning = new AllocationProviderWarning(
                "test:limited_scope",
                providerId,
                "test:scope",
                "Provider observed only its declared scope",
                AllocationProviderFixtures.TICK,
                AllocationSchema.CURRENT_VERSION
        );
        AllocationProviderFailure failure = new AllocationProviderFailure(
                AllocationProviderFailureCode.PROVIDER_EXCEPTION,
                AllocationProviderFailureScope.PROVIDER,
                java.util.Optional.of(providerId),
                "test:provider",
                "Provider threw an unexpected exception during observation",
                OptionalLong.of(AllocationProviderFixtures.TICK),
                AllocationSchema.CURRENT_VERSION
        );

        assertEquals(warning, new AllocationProviderWarning(
                "test:limited_scope",
                providerId,
                "test:scope",
                "Provider observed only its declared scope",
                AllocationProviderFixtures.TICK,
                AllocationSchema.CURRENT_VERSION
        ));
        assertFalse(failure.message().contains("@"));
        assertFalse(failure.message().contains("\n"));
        assertEquals(64, warning.canonicalDigest().length());
    }

    @Test
    void exactCapacityScaleAndZeroRemainIntactThroughAResult() {
        CapacityUnitId unit = CapacityUnitId.of("test:exact_unit");
        AllocationProviderDescriptor descriptor = AllocationProviderFixtures.descriptor(
                "exact",
                "test:owner_exact",
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                unit
        );
        ObservedResourceSnapshot resource = AllocationProviderFixtures.resource(
                descriptor,
                "exact",
                ResourceCategories.PRODUCTION,
                "test:owner_exact",
                AllocationProviderFixtures.TICK,
                ResourceExclusivityMode.SHARED
        );
        ObservedCapacitySnapshot capacity = AllocationProviderFixtures.capacity(
                "exact",
                resource.resourceId(),
                AllocationProviderFixtures.MACHINE_TIME,
                unit,
                "test:owner_exact",
                AllocationProviderFixtures.TICK,
                "0.000000001"
        );
        AllocationObservationResult result = AllocationObservationResult.success(
                descriptor.providerId(),
                AllocationProviderFixtures.TICK,
                List.of(resource),
                List.of(capacity)
        );

        assertEquals("0.000000001", result.capacities().getFirst()
                .observedAmount().canonicalAmount());
        assertEquals(unit, result.capacities().getFirst().capacityUnitId());
    }
}
