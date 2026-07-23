package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationSnapshotTest {
    @Test
    void observedResourceSnapshotPreservesTypedAuthorityAndAvailability() {
        AllocationMetadata metadata = AllocationMetadata.of(Map.of(
                "example:shift", AllocationMetadataValue.identifier("example:day")
        ));
        ObservedResourceSnapshot snapshot = new ObservedResourceSnapshot(
                ResourceId.of("example:day_shift_butchers"),
                ResourceCategories.WORKFORCE,
                AllocationProviderId.of("example:workforce_provider"),
                ExternalReference.of(
                        "butchercraft:position_shift_capacity",
                        "example:day_shift_butchers",
                        "example:workforce"
                ).withRole("example:butcher"),
                ResourceAvailability.AVAILABLE,
                ResourceExclusivityMode.SHARED,
                100L,
                metadata,
                AllocationSchema.CURRENT_VERSION
        );

        assertEquals(ResourceCategories.WORKFORCE, snapshot.resourceCategory());
        assertEquals(ResourceAvailability.AVAILABLE, snapshot.availability());
        assertEquals(Optional.of("example:butcher"), snapshot.authoritativeExternalReference().roleId());
        assertEquals(metadata, snapshot.metadata());
    }

    @Test
    void schemaOneRejectsIndividualWorkerResourceReferences() {
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.UNSUPPORTED_SCHEMA_CONCEPT,
                () -> new ObservedResourceSnapshot(
                        ResourceId.of("example:worker_alex"),
                        ResourceCategories.WORKFORCE,
                        AllocationProviderId.of("example:workforce_provider"),
                        ExternalReference.of(
                                "butchercraft:individual_worker",
                                "example:worker_alex",
                                "example:workforce"
                        ),
                        ResourceAvailability.AVAILABLE,
                        ResourceExclusivityMode.EXCLUSIVE,
                        100L,
                        AllocationMetadata.empty(),
                        AllocationSchema.CURRENT_VERSION
                )
        );
    }

    @Test
    void observedCapacitySnapshotUsesAnExactTypedCapacityKey() {
        ResourceId resourceId = ResourceId.of("example:bandsaw_line");
        ObservedCapacitySnapshot snapshot = new ObservedCapacitySnapshot(
                CapacityId.of("example:bandsaw_hours"),
                resourceId,
                CapacityTypeId.of("butchercraft:machine_time"),
                AllocationQuantity.of("8", CapacityUnits.MACHINE_TIME),
                CapacityUnits.MACHINE_TIME,
                100L,
                AllocationTestFixtures.observation("bandsaw"),
                AllocationMetadata.empty(),
                AllocationSchema.CURRENT_VERSION
        );

        assertEquals(
                new CapacityKey(
                        resourceId,
                        CapacityTypeId.of("butchercraft:machine_time"),
                        CapacityUnits.MACHINE_TIME
                ),
                snapshot.capacityKey()
        );
        assertEquals("8", snapshot.observedAmount().canonicalAmount());
    }

    @Test
    void snapshotsRejectUnitSchemaAndTickViolations() {
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.NULL_VALUE,
                () -> new ObservedCapacitySnapshot(
                        CapacityId.of("example:capacity"),
                        ResourceId.of("example:resource"),
                        CapacityTypeId.of("example:type"),
                        AllocationQuantity.of("1", CapacityUnits.ENERGY),
                        CapacityUnits.ENERGY,
                        0L,
                        null,
                        AllocationMetadata.empty(),
                        AllocationSchema.CURRENT_VERSION
                )
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.INCOMPATIBLE_UNIT,
                () -> new ObservedCapacitySnapshot(
                        CapacityId.of("example:capacity"),
                        ResourceId.of("example:resource"),
                        CapacityTypeId.of("example:type"),
                        AllocationQuantity.of("1", CapacityUnits.ENERGY),
                        CapacityUnits.MACHINE_TIME,
                        0L,
                        AllocationTestFixtures.observation("unit"),
                        AllocationMetadata.empty(),
                        AllocationSchema.CURRENT_VERSION
                )
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.INVALID_SCHEMA_VERSION,
                () -> new ObservedCapacitySnapshot(
                        CapacityId.of("example:capacity"),
                        ResourceId.of("example:resource"),
                        CapacityTypeId.of("example:type"),
                        AllocationQuantity.of("1", CapacityUnits.ENERGY),
                        CapacityUnits.ENERGY,
                        0L,
                        AllocationTestFixtures.observation("schema"),
                        AllocationMetadata.empty(),
                        2
                )
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.INVALID_SIMULATION_TICK,
                () -> new ObservedResourceSnapshot(
                        ResourceId.of("example:resource"),
                        ResourceCategories.STORAGE,
                        AllocationProviderId.of("example:provider"),
                        AllocationTestFixtures.observation("tick"),
                        ResourceAvailability.UNAVAILABLE,
                        ResourceExclusivityMode.EXCLUSIVE,
                        -1L,
                        AllocationMetadata.empty(),
                        AllocationSchema.CURRENT_VERSION
                )
        );
    }

    @Test
    void snapshotOrderingIsStableAcrossEquivalentInputOrder() {
        ObservedCapacitySnapshot earlier = capacity("a", 1L);
        ObservedCapacitySnapshot later = capacity("b", 2L);
        assertTrue(earlier.compareTo(later) < 0);
        assertEquals(0, earlier.compareTo(capacity("a", 1L)));
    }

    private static ObservedCapacitySnapshot capacity(String suffix, long tick) {
        return new ObservedCapacitySnapshot(
                CapacityId.of("example:capacity_" + suffix),
                ResourceId.of("example:resource_" + suffix),
                CapacityTypeId.of("example:type"),
                AllocationQuantity.of("1", CapacityUnits.PRODUCTION_SLOT),
                CapacityUnits.PRODUCTION_SLOT,
                tick,
                AllocationTestFixtures.observation(suffix),
                AllocationMetadata.empty(),
                AllocationSchema.CURRENT_VERSION
        );
    }
}
